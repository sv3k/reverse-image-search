package com.ris.core;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

/**
 * Image descriptor containing URL, width and height of an image with ability to
 * download image itself as a {@link java.awt.image.BufferedImage}.
 * 
 * @author Anton Gorbunov
 * @since 2015-04-20
 */
public class WebImage {

	private final String					url;
	private final int						width, height;
	private final Supplier<BufferedImage>	imageSupplier;

	public WebImage(String url, int width, int height) {
		this.url = url;
		this.width = width;
		this.height = height;
		imageSupplier = Suppliers.memoize(() -> {
			try {
				return ImageIO.read(new URL(url));
			} catch (IOException e) {
				throw new RuntimeException(e); // Will unwrap outside
			}
		});
	}

	public WebImage(String url) throws IOException {
		this.url = url;
		BufferedImage image = ImageIO.read(new URL(url));
		width = image.getWidth();
		height = image.getHeight();
		imageSupplier = () -> image;
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

	/**
	 * Downloads actual image by known URL or get from cache if image is already
	 * downloaded.
	 *
	 * @return Downloaded image.
	 * @throws IOException
	 *             if download fails.
	 */
	public BufferedImage getImage() throws IOException {
		try {
			return imageSupplier.get();
		} catch (RuntimeException e) {
			// Unwrap possible exception from supplier
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			} else {
				throw e;
			}
		}
	}

	@Override
	public String toString() {
		return url + " (" + width + "x" + height + ")";
	}
}
