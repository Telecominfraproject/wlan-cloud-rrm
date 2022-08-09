/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules.operationelement;

/** Represents an Operation Element (in IEEE 802.11-2020). */
public interface OperationElement {

	/**
	 * Determine whether {@code this} and {@code otherOper} "match" for the purpose
	 * of aggregating statistics.
	 *
	 * @param otherOper another operation element as defined in 802.11
	 * @return true if the the operation elements "match" for the purpose of
	 *         aggregating statistics; false otherwise.
	 */
	boolean matchesForAggregation(OperationElement otherOper);
}