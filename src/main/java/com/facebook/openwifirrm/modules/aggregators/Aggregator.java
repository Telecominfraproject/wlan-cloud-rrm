package com.facebook.openwifirrm.modules.aggregators;

/**
 * Aggregates added values into one "aggregate" measure.
 *
 * @param <T> the type of values being aggregated (e.g., Double).
 */
public interface Aggregator<T> {
	/** Adds {@value} to the group of values being aggregated. */
	void addValue(T value);

	/** Returns the aggregate measure of all added values. */
	T getAggregate();

	/** Remove all added values from the group of values being aggregated. */
	void reset();
}
