/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Util functions */
public class Utils {

	/** Private constructor (this class should never be instantiated */
	private Utils() {}

	/**
	 * Return the value at the given percentile of a collection of values.
	 *
	 * @param <T>        {@code Comparable} type
	 * @param values     list of comparable values
	 * @param percentile percentile (from the bottom) between 0 and 100
	 *                   inclusive. E.g., 90th percentile means higher than 90%
	 *                   of values. Note that the 100th percentile is always
	 *                   impossible (it is not possible to be greater than every
	 *                   single value), but it is used here to mean the highest
	 *                   value. Note that 0% is possible and refers to the
	 *                   lowest value.
	 * @return the value closest to being at the {@code percentile} percentile
	 *         of the values given in {@code values}. A value V being "at the X
	 *         percentile" means that X percent of the values are lower than V.
	 *         If multiple values are equally close to being at that percentile,
	 *         any can be returned. Note that "close" applies in both directions
	 *         (e.g., a value at the 88th percentile and a value at the 92nd
	 *         percentile are equally close to being at the 90th percentile).
	 */
	public static <T extends Comparable<T>> T getValueAtPercentile(
		List<T> values,
		double percentile
	) {
		if (values == null || values.isEmpty()) {
			throw new IllegalArgumentException(
				"Input list must be non-null and non-empty."
			);
		}
		for (T value : values) {
			if (value == null) {
				throw new IllegalArgumentException(
					"Input list must contain only non-null values."
				);
			}
		}
		if (percentile < 0 || percentile > 100) {
			throw new IllegalArgumentException(
				"Percentile must be between 0 and 100 inclusive."
			);
		}
		// do not modify original list
		values = new ArrayList<>(values);
		Collections.sort(values);
		double idealIndex = values.size() * (percentile / 100);
		// use int, not long, since values.get(...) requires an int
		int index = (int) Math.round(idealIndex);
		if (index < 0) {
			return values.get(0);
		}
		if (index >= values.size()) {
			return values.get(values.size() - 1);
		}
		return values.get(index);
	}
}
