package com.ris.core;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.imgscalr.Scalr;
import org.jtransforms.fft.FloatFFT_2D;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;

/**
 * Reverse image searcher.
 *
 * @author Anton Gorbunov
 * @since 2015-03-07
 */
public class ReverseImageSearcher {

	private static final Logger							log							= LoggerFactory.getLogger(ReverseImageSearcher.class);

	private static final String							GOOGLE_IMG_SEARCH_PREFIX	= "https://images.google.com/searchbyimage?image_url=";
	private static final String							USER_AGENT_HEADER_VALUE		= "Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36";

	private static final Pattern						RESULT_PAGE_URL_PATTERN		= Pattern.compile("href=\"(\\/search\\?[^\\\"]*?tbs=simg:[^,]+?&amp;.+?)\"");
	private static final Pattern						IMAGE_URL_PATTERN			= Pattern.compile("\\\"ou\\\":\\\"(http.+?)\\\"");
	private static final Pattern						IMAGE_SIZE_PATTERN			= Pattern.compile("\\\"oh\\\":(?<height>\\d+),\\\"ou\\\":\\\"http.+?\\\",\\\"ow\\\":(?<width>\\d+)");

	private final ConfigurationBuilder.Configuration	configuration;

	/**
	 * Create reverse image searcher instance with default configuration.
	 */
	public ReverseImageSearcher() {
		this.configuration = new ConfigurationBuilder().build();
	}

	/**
	 * Create reverse image searcher instance with custom configuration.
	 * 
	 * @param configuration
	 *            custom configuration for searcher.
	 */
	public ReverseImageSearcher(ConfigurationBuilder.Configuration configuration) {
		this.configuration = configuration;
	}

	/**
	 * Search for possible best quality image copy using Google search engine.
	 * Found image can contain small differences in aspect ratio and/or in
	 * picture itself - watermarks, small logos, etc.
	 * 
	 * @param sourceImageUrl
	 *            URL of source image for search.
	 * @return {@link WebImage} with the highest calculated quality score.
	 * @throws IOException
	 *             if something went wrong.
	 */
	public WebImage findBestAlternative(String sourceImageUrl) throws IOException {
		WebImage sourceImage = new WebImage(sourceImageUrl);

		List<WebImage> alternatives = findAlternatives(sourceImageUrl);
		if (alternatives.isEmpty()) {
			return sourceImage;
		}

		List<WebImage> images = new ArrayList<>(alternatives.size() + 1);
		images.add(sourceImage);
		images.addAll(alternatives);

		// Find the biggest dimension
		int maxDimension = 0;
		for (WebImage image : images) {
			int imgDimension = Math.max(image.getWidth(), image.getHeight());
			if (imgDimension > maxDimension) {
				maxDimension = imgDimension;
			}
		}

		long highest = 0;
		WebImage best = sourceImage;
		for (WebImage image : images) {
			log.debug("Processing image {}", image);
			BufferedImage img;

			long start = System.nanoTime();
			try {
				img = image.getImage();
			} catch (IOException e) {
				log.warn("Failed to download image: {}, skipping", image);
				continue;
			}
			log.trace("Download time: {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

			start = System.nanoTime();
			BufferedImage resized = Scalr.resize(img, maxDimension); // Resize to the biggest size
			log.trace("Resize time: {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

			start = System.nanoTime();
			long score = scoreDetailLevel(resized);
			log.trace("Score time: {}ms", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));

			log.debug("Score: {}", score);

			if (score > highest) {
				highest = score;
				best = image;
			}
		}

		return best;
	}

	/**
	 * Search for image copies with different size using Google search engine.
	 * Found images can contain small differences in aspect ratio and/or in
	 * picture itself - watermarks, small logos, etc.
	 *
	 * @param sourceImageUrl
	 *            URL of source image for search.
	 * @return {@link List} with {@link WebImage}s of alternative images
	 *         across the Internet. Images with bigger size goes in the
	 *         beginning of the list, lesser - in the end, but this sorting is
	 *         non-rigid.
	 * @throws IOException
	 *             if something went wrong.
	 */
	public List<WebImage> findAlternatives(String sourceImageUrl) throws IOException {

		// 1. Run reverse image search in Google
		URL url = new URL(GOOGLE_IMG_SEARCH_PREFIX + sourceImageUrl); // sourceImageUrl encoding will be done automatically
		HttpURLConnection conn = prepareConnection(url, true);
		conn.connect();
		String response = getResponseString(conn);
		String redirectedHost = conn.getURL().getHost();

		// 2. Find a link to a results page
		Matcher resultPageMatcher = RESULT_PAGE_URL_PATTERN.matcher(response);
		if (!resultPageMatcher.find()) {
			throw new IOException("Result page URL not found");
		}
		String href = resultPageMatcher.group(1).replaceAll("&amp;", "&");

		// 3. Open the results page
		url = new URL("https://" + redirectedHost + href);
		log.debug("Results page: {}", url);
		conn = prepareConnection(url);
		conn.connect();
		response = getResponseString(conn);

		// 4. Extract image URLs & sizes
		Matcher imageUrlMatcher = IMAGE_URL_PATTERN.matcher(response);
		Matcher imageSizeMatcher = IMAGE_SIZE_PATTERN.matcher(response);
		List<WebImage> result = new ArrayList<>();
		while (imageUrlMatcher.find()) {
			href = imageUrlMatcher.group(1);
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());

			if (!imageSizeMatcher.find()) {
				throw new IOException("Failed to parse results page");
			}

			int width = Integer.parseInt(imageSizeMatcher.group("width"));
			int height = Integer.parseInt(imageSizeMatcher.group("height"));
			WebImage descriptor = new WebImage(href, width, height);
			result.add(descriptor);

			if (result.size() == configuration.getAlternativesCount()) {
				break;
			}
		}

		return result;
	}

	/**
	 * Score the level of detail in the image. Calculated score could be used to
	 * compare quality of the images with the same content. Generally bigger
	 * score means better image quality (higher level of details).
	 * <p>
	 * This method uses <a href =
	 * "http://en.wikipedia.org/wiki/Discrete_Fourier_transform">DFT</a>
	 * algorithm to get image frequencies breakdown and then collects all
	 * frequency estimations. Final score is calculated basing on these
	 * estimations.
	 * 
	 * @param image
	 *            Source image for analyzing.
	 * @return Estimation of the level of details in provided image.
	 * @see <a href =
	 *      "http://en.wikipedia.org/wiki/Discrete_Fourier_transform">Discrete
	 *      Fourier transform</a>
	 */
	public long scoreDetailLevel(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();

		// 1. Convert to grayscale image
		BufferedImage grayscaleImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics graphics = grayscaleImage.getGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();

		// 2. Convert to 2D array
		float raw[][] = new float[width][height * 2]; // Double-sized for DFT calculation
		Raster raster = grayscaleImage.getData();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				raw[x][y] = raster.getSample(x, y, 0);
			}
		}

		// 3. Calculate DFT in-place
		FloatFFT_2D fft2D = new FloatFFT_2D(width, height);
		fft2D.realForwardFull(raw);

		// 4. Collect frequency estimations from 2D to 1D array
		int size = width % 2 == 0 ? width / 2 : width / 2 + 1;
		int freq[] = new int[size + 1];
		float yMultiplier = size / height;
		for (int y = 0; y < height; y++) {
			int yDistance = (int) (y * yMultiplier);
			for (int x = 0; x < size; x++) {
				int distance = Math.max(x, yDistance);
				freq[distance] += Math.abs(raw[x][y]);
			}
			for (int x = size; x < width; x++) {
				int xDistance = width - x;
				int distance = Math.max(xDistance, yDistance);
				freq[distance] += Math.abs(raw[x][y]);
			}
		}

		// 5. Normalize
		float multiplier = 10000f / freq[0];
		for (int i = 0; i < size; i++) {
			freq[i] *= multiplier;
		}

		// 6. Calculate final score
		long score = 0;
		for (int i = 1; i < size; i++) {
			score += freq[i];
		}

		return score;
	}

	public ConfigurationBuilder.Configuration getConfiguration() {
		return configuration;
	}

	private HttpURLConnection prepareConnection(URL url) throws IOException {
		return prepareConnection(url, false);
	}

	private HttpURLConnection prepareConnection(URL url, boolean followRedirects) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.addRequestProperty(HttpHeaders.USER_AGENT, USER_AGENT_HEADER_VALUE);
		conn.setInstanceFollowRedirects(followRedirects);
		return conn;
	}

	private String getResponseString(HttpURLConnection connection) throws IOException {
		if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
			throw new IOException("Unexpected response status code " + connection.getResponseCode());
		}

		try (InputStreamReader streamReader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
			return CharStreams.toString(streamReader);
		}
	}
}
