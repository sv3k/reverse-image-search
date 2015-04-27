package com.ris.core;

/**
 * @author Anton Gorbunov
 */
public class ImageSearchException extends RuntimeException {

	public ImageSearchException(String message) {
		super(message);
	}

	public ImageSearchException(String message, Throwable cause) {
		super(message, cause);
	}
}
