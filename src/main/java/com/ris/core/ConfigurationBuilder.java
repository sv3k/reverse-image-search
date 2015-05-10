package com.ris.core;

/**
 * Configuration builder for reverse image searcher.
 * 
 * @author Anton Gorbunov
 * @since 2015-04-20
 */
public class ConfigurationBuilder {

	private int	alternativesCount	= 5;

	/**
	 * Set maximum count of images returned for each alternative search. Lower
	 * value will improve search performance but may cause worse results.
	 * <p>
	 * Default value is 5.
	 * 
	 * @param value
	 *            custom property value, should be positive.
	 * @return Current builder instance.
	 */
	public ConfigurationBuilder setAlternativesCount(int value) {
		if (value <= 0) {
			throw new IllegalArgumentException("Value should be positive");
		}
		this.alternativesCount = value;
		return this;
	}

	public Configuration build() {
		return new Configuration(this);
	}

	public static class Configuration {

		private final int	alternativesCount;

		public Configuration(ConfigurationBuilder builder) {
			this.alternativesCount = builder.alternativesCount;
		}

		public int getAlternativesCount() {
			return alternativesCount;
		}
	}
}
