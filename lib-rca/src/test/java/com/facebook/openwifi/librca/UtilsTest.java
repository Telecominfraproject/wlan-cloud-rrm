/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class UtilsTest {

	@Test
	void testGetValueAtPercentile() {
		// check illegal args
		// null list is invalid
		assertThrows(
			IllegalArgumentException.class,
			() -> Utils.getValueAtPercentile(null, 0)
		);
		// empty list is invalid
		final List<Integer> values = new ArrayList<>();
		assertThrows(
			IllegalArgumentException.class,
			() -> Utils.getValueAtPercentile(values, 0)
		);
		// list with null element is invalid
		values.add(0);
		values.add(null);
		assertThrows(
			IllegalArgumentException.class,
			() -> Utils.getValueAtPercentile(values, 0)
		);
		// negative percentile is invalid
		values.remove(1);
		assertThrows(
			IllegalArgumentException.class,
			() -> Utils.getValueAtPercentile(values, -0.1)
		);
		// percentile above 100 is invalid
		assertThrows(
			IllegalArgumentException.class,
			() -> Utils.getValueAtPercentile(values, 101)
		);

		// basic tests
		// list with only 1 element
		int actual = Utils.getValueAtPercentile(values, 0);
		int exp = values.get(0);
		assertEquals(actual, exp);
		actual = Utils.getValueAtPercentile(values, 50);
		assertEquals(actual, exp);
		actual = Utils.getValueAtPercentile(values, 100);
		assertEquals(actual, exp);
		// list with 2 elements
		// 1% -> smallest element
		values.add(1);
		actual = Utils.getValueAtPercentile(values, 1);
		exp = values.get(0);
		assertEquals(actual, exp);
		// 24% -> still smallest element
		actual = Utils.getValueAtPercentile(values, 24);
		exp = values.get(0);
		assertEquals(actual, exp);
		// 26% -> now the largest element
		actual = Utils.getValueAtPercentile(values, 26);
		exp = values.get(1);
		assertEquals(actual, exp);
		// 50% -> still the largest element
		actual = Utils.getValueAtPercentile(values, 50);
		exp = values.get(1);
		assertEquals(actual, exp);

		// test in reverse order
		values.clear();
		values.add(1);
		values.add(0);
		actual = Utils.getValueAtPercentile(values, 24);
		exp = values.get(1);
		assertEquals(actual, exp);
		actual = Utils.getValueAtPercentile(values, 26);
		exp = values.get(0);
		assertEquals(actual, exp);

		// test more values
		values.clear();
		for (int i = 0; i < 1000; i++) {
			values.add(i);
		}
		actual = Utils.getValueAtPercentile(values, 50);
		exp = values.get(500);
		assertEquals(actual, exp);
		actual = Utils.getValueAtPercentile(values, 99.9);
		exp = values.get(999);
		assertEquals(actual, exp);
		// test ties
		actual = Utils.getValueAtPercentile(values, 99.85);
		int exp1 = values.get(998);
		int exp2 = values.get(999);
		assertTrue(actual == exp1 || actual == exp2);
	}
}
