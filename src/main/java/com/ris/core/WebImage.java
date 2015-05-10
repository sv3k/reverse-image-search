package com.ris.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

/**
 * Image descriptor containing URL, width and height of an image with ability to
 * download image itself as a {@link java.awt.image.BufferedImage}.
 * 
 * @author Anton Gorbunov
 * @since 2015-04-20
 */
public class WebImage {

	private final String	url;
	private final int		width, height;

	public WebImage(String url, int width, int height) {
		this.url = url;
		this.width = width;
		this.height = height;
	}

	public String getUrl() {
		return url;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public BufferedImage download() throws IOException {
		return ImageIO.read(new URL(url));
	}

	@Override
	public String toString() {
		return url + " (" + width + "x" + height + ")";
	}
}
