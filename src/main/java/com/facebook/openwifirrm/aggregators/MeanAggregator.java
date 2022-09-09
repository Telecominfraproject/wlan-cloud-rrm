/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.aggregators;

import java.util.LinkedList;
import java.util.List;

/**
 * Tracks the mean of all added values. If no values are added, the mean is 0.
 */
public class MeanAggregator implements Aggregator<Double> {
	protected List<Double> valueList = new LinkedList<>();
	protected double mean = 0;
	protected long count = 0;

	/** Constructor with no args */
	public MeanAggregator() {}

	/** Constructor with list of values*/
	public MeanAggregator(List<Double> values) {
		for (Double value : values) {
			addValue(value);
		}
	}

	@Override
	public void addValue(Double value) {
		valueList.add(value);
		mean = ((double) count / (count + 1)) * mean + (value / (count + 1));
		count++;
	}

	@Override
	public Double getAggregate() { return mean; }

	@Override
	public List<Double> getList() { return valueList; }

	@Override
	public long getCount() { return count; }

	@Override
	public void reset() {
		valueList = new LinkedList<>();
		mean = 0;
		count = 0;
	}

}
