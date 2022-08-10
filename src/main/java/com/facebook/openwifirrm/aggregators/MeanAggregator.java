/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.aggregators;

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
	public long getCount() {
		return count;
	}

	@Override
	public void reset() {
		mean = 0;
		count = 0;
	}

}
