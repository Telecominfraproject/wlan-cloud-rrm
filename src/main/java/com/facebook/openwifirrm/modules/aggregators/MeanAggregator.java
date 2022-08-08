package com.facebook.openwifirrm.modules.aggregators;

/**
 * Tracks the mean of all added values. If no values are added, the mean is 0.
 */
public class MeanAggregator implements Aggregator<Double> {

	protected double mean = 0;
	protected long count = 0;

	@Override
	public void addValue(Double value) {
		mean = ((double) count / (count + 1)) * mean + (value / (count + 1));
		count++;
	}

	@Override
	public Double getAggregate() {
		return mean;
	}

	@Override
	public void reset() {
		mean = 0;
		count = 0;
	}

}
