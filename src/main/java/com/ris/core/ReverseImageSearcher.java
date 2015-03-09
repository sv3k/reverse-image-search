package com.ris.core;

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

	/**
	 * Search for image copies with different size using Google search engine.
	 * Found images can contain small differences in aspect ratio and/or in
	 * picture itself - watermarks, small logos, etc.
	 *
	 * @param sourceImageUrl
	 *            URL of source image for search.
	 * @return {@link List} with URLs of alternative images across the Internet.
	 *         Better resolution images goes in the beginning of the list, worst
	 *         - in the end, but this sorting is non-rigid.
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 * @throws IOException
	 */
	public List<String> findAlternatives(String sourceImageUrl) throws InterruptedException, ExecutionException, TimeoutException, IOException {

		// 1. Run reverse image search in Google
		URL url = new URL(GOOGLE_IMG_SEARCH_PREFIX + sourceImageUrl); // sourceImageUrl encoding will be done automatically
		HttpURLConnection conn = prepareConnection(url, true);
		conn.connect();
		String response = getResponseString(conn);
		String redirectedHost = conn.getURL().getHost();

		// 2. Find a link to a results page
		Matcher matcher = RESULT_PAGE_URL_PATTERN.matcher(response);
		if (!matcher.find()) {
			throw new IOException("Result page URL not found");
		}
		String href = matcher.group(1).replaceAll("&amp;", "&");

		// 3. Open the results page
		url = new URL("https://" + redirectedHost + href);
		conn = prepareConnection(url);
		conn.connect();
		response = getResponseString(conn);

		// 4. Extract image URLs
		matcher = IMAGE_URL_PATTERN.matcher(response);
		List<String> result = new ArrayList<>();
		while (matcher.find()) {
			href = matcher.group(1);
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());
			href = URLDecoder.decode(href, StandardCharsets.UTF_8.name());
			result.add(href);
		}

		return result;
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