package com.ris.core;

/**
 * Image descriptor containing URL, width and height of an image.
 * 
 * @author Anton Gorbunov
 * @since 2015-04-20
 */
public class ImageDescriptor {

	private final String	url;
	private final int		width, height;

	public ImageDescriptor(String url, int width, int height) {
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

	@Override
	public String toString() {
		return url + " (" + width + "x" + height + ")";
	}
}
