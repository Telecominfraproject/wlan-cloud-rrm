package com.facebook.openwifirrm.modules.aggregators;

/**
 * Aggregates added values into one "aggregate" measure.
 *
 * @param <T>
 */
public interface Aggregator<T> {
	public void addValue(T value);

	public T getAggregate();

	public void reset();
}
