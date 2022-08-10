/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules.aggregators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.aggregators.MeanAggregator;

public class MeanAggregatorTest {
	@Test
	public void testEmptyAndNonEmptyAndReset() {
		final double eps = 0.000001;

		MeanAggregator agg = new MeanAggregator();

		// default mean is 0
		assertEquals(0, agg.getAggregate(), eps);

		// adding 0 (the mean) does not change the mean
		agg.addValue(0.0);
		assertEquals(0, agg.getAggregate(), eps);

		// add an "int"
		agg.addValue(1.0);
		assertEquals(0.5, agg.getAggregate(), eps);

		// add a double
		agg.addValue(3.5);
		assertEquals(1.5, agg.getAggregate(), eps);

		// add a negative number
		agg.addValue(-0.5);
		assertEquals(1.0, agg.getAggregate(), eps);

		// adding the mean does not change the mean
		agg.addValue(1.0);
		assertEquals(1.0, agg.getAggregate(), eps);

		// test reset
		agg.reset();
		assertEquals(0, agg.getAggregate(), eps);
	}
}
