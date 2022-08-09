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

	boolean matchesForAggregation(OperationElement otherOper);
}