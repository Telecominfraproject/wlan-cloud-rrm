/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

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
