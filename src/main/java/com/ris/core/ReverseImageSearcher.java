package com.ris.core;

import java.awt.Graphics;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jtransforms.fft.FloatFFT_2D;

import com.google.common.io.CharStreams;
import com.google.common.net.HttpHeaders;

/**
 * Reverse Image Searcher.
 *
 * @author Anton Gorbunov
 * @since 2015-03-07
 */
public class ReverseImageSearcher {

	private static final String		GOOGLE_IMG_SEARCH_PREFIX	= "https://images.google.com/searchbyimage?image_url=";
	private static final String		USER_AGENT_HEADER_VALUE		= "Mozilla/5.0 (Windows NT 6.3; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/40.0.2214.115 Safari/537.36";

	private static final Pattern	RESULT_PAGE_URL_PATTERN		= Pattern.compile("href=\"(\\/search\\?[^\\\"]*?tbs=simg:[^,]+?&amp;.+?)\"");
	private static final Pattern	IMAGE_URL_PATTERN			= Pattern.compile("imgres\\?imgurl=(http.+?)&amp;imgrefurl=");
	private static final Pattern	IMAGE_SIZE_PATTERN			= Pattern.compile("class=\\\"rg_an\\\">(\\d+)&nbsp;&#215;&nbsp;(\\d+)<\\/span");

	/**
	 * Search for image copies with different size using Google search engine.
	 * Found images can contain small differences in aspect ratio and/or in
	 * picture itself - watermarks, small logos, etc.
	 *
	 * @param sourceImageUrl
	 *            URL of source image for search.
	 * @return {@link List} with {@link ImageDescriptor}s of alternative images
	 *         across the Internet. Images with bigger size goes in the
	 *         beginning of the list, lesser - in the end, but this sorting is
	 *         non-rigid.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws IOException
	 */
	public List<ImageDescriptor> findAlternatives(String sourceImageUrl) throws InterruptedException, ExecutionException, TimeoutException, IOException {

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
		conn = prepareConnection(url);
		conn.connect();
		response = getResponseString(conn);

		// 4. Extract image URLs & sizes
		Matcher imageUrlMatcher = IMAGE_URL_PATTERN.matcher(response);
		Matcher imageSizeMatcher = IMAGE_SIZE_PATTERN.matcher(response);
		List<ImageDescriptor> result = new ArrayList<>();
		while (imageUrlMatcher.find()) {
			href = imageUrlMatcher.group(1);
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());

			if (!imageSizeMatcher.find()) {
				throw new IOException("Failed to parse results page");
			}

			int width = Integer.parseInt(imageSizeMatcher.group(1));
			int height = Integer.parseInt(imageSizeMatcher.group(2));
			ImageDescriptor descriptor = new ImageDescriptor(href, width, height);
			result.add(descriptor);
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
	 * algorithm to get image frequencies breakdown and then collects highest
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
	public int scoreDetailLevel(BufferedImage image) {
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
		int score = 0;
		int count = 3; // Count of highest frequency samples
		for (int i = size - 1; i > size - 1 - count; i--) {
			score += freq[i];
		}

		return score;
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
