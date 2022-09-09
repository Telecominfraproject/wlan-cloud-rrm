/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.aggregators;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedList;

import org.junit.jupiter.api.Test;

public class MeanAggregatorTest {
	@Test
	public void testEmptyAndNonEmptyAndReset() {
		final double eps = 0.000001;

		MeanAggregator agg = new MeanAggregator();

		// default mean is 0
		assertEquals(0, agg.getAggregate(), eps);
		assertEquals(0, agg.getCount());
		assertEquals(new LinkedList<>(), agg.getList());

		// adding 0 (the mean) does not change the mean
		agg.addValue(0.0);
		assertEquals(0, agg.getAggregate(), eps);
		assertEquals(1, agg.getCount());
		assertEquals(new LinkedList<>(Arrays.asList(0.0)), agg.getList());

		// add an "int"
		agg.addValue(1.0);
		assertEquals(0.5, agg.getAggregate(), eps);
		assertEquals(2, agg.getCount());
		assertEquals(new LinkedList<>(Arrays.asList(0.0, 1.0)), agg.getList());

		// add a double
		agg.addValue(3.5);
		assertEquals(1.5, agg.getAggregate(), eps);
		assertEquals(3, agg.getCount());
		assertEquals(
			new LinkedList<>(Arrays.asList(0.0, 1.0, 3.5)),
			agg.getList()
		);

		// add a negative number
		agg.addValue(-0.5);
		assertEquals(1.0, agg.getAggregate(), eps);
		assertEquals(4, agg.getCount());
		assertEquals(
			new LinkedList<>(Arrays.asList(0.0, 1.0, 3.5, -0.5)),
			agg.getList()
		);

		// adding the mean does not change the mean
		agg.addValue(1.0);
		assertEquals(1.0, agg.getAggregate(), eps);
		assertEquals(5, agg.getCount());
		assertEquals(
			new LinkedList<>(Arrays.asList(0.0, 1.0, 3.5, -0.5, 1.0)),
			agg.getList()
		);

		// test reset
		agg.reset();
		assertEquals(0, agg.getAggregate(), eps);
		assertEquals(0, agg.getCount());
		assertEquals(new LinkedList<>(), agg.getList());
	}
}
