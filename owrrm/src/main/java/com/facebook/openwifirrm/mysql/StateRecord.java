/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.mysql;

/**
 * Representation of a record in the "state" table.
 */
public class StateRecord {
	public final long id;
	public final long timestamp;
	public final String metric;
	public final long value;
	public final String serial;

	/** Constructor (with empty "id"). */
	public StateRecord(
		long timestamp,
		String metric,
		long value,
		String serial
	) {
		this(0, timestamp, metric, value, serial);
	}

	/** Constructor. */
	public StateRecord(
		long id,
		long timestamp,
		String metric,
		long value,
		String serial
	) {
		this.id = id;
		this.timestamp = timestamp;
		this.metric = metric;
		this.value = value;
		this.serial = serial;
	}

	@Override
	public String toString() {
		return String.format(
			"%s = %d [serial=%s, ts=%d]",
			metric,
			value,
			serial,
			timestamp
		);
	}
}
