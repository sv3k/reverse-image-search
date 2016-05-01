package com.ris.core;

import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Anton Gorbunov
 * @since 2016-05-01
 */
public class ReverseImageSearcherTest {

	private static final String	TEST_IMAGE_URL	= "https://upload.wikimedia.org/wikipedia/en/2/24/Lenna.png";

	@Test
	public void FindAlternativesShouldWork() throws Exception {
		ReverseImageSearcher ris = new ReverseImageSearcher();
		List<WebImage> images = ris.findAlternatives(TEST_IMAGE_URL);
		assertThat(images).isNotEmpty();
		assertThat(images.size()).isEqualTo(ris.getConfiguration().getAlternativesCount());
	}
}
