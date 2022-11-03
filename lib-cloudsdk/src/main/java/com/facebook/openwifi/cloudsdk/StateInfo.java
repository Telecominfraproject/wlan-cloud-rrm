/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import com.facebook.openwifi.cloudsdk.models.ap.State;

public class StateInfo extends State {
	/**
	 * Unix time in milliseconds (ms). This is added it because {@link State} is an unknown
	 * time reference.
	 */
	public long timestamp;

	/** Default Constructor. */
	public StateInfo() {}

	/** Copy Constructor. */
	public StateInfo(StateInfo stateInfo) {
		super(stateInfo);
		this.timestamp = stateInfo.timestamp;
	}
}
