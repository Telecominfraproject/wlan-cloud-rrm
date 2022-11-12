/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.ies;

import java.util.Objects;

import com.google.gson.JsonObject;

// NOTE: From what I can see it currently does not appear in the list of IEs,
// although it's possible it'll be there in the future.
/**
 * This information element (IE) appears in wifiscan entries. It's called "Power
 * Capability" in 802.11 specs (section 9.4.2.14). Refer to the specification
 * for more details. Language in javadocs is taken from the specification.
 */
public class PowerCapability {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 33;

	/**
	 * Signed 8 bits units of dB relative to 1mW - nominal minimum transmit power
	 * with which the STA is capable of transmitting in the current channel, with a
	 * tolerance ± 5 dB.
	 */
	public final byte minimumTxPowerCapability;
	/**
	 * Signed 8 bits units of dB relative to 1mW - nominal maximum transmit power
	 * with which the STA is capable of transmitting in the current channel, with a
	 * tolerance ± 5 dB.
	 */
	public final byte maximumTxPowerCapability;

	/** Constructor */
	public PowerCapability(
		byte minimumTxPowerCapability,
		byte maximumTxPowerCapability
	) {
		this.minimumTxPowerCapability = minimumTxPowerCapability;
		this.maximumTxPowerCapability = maximumTxPowerCapability;
	}

	/** Parse PowerCapability from JSON object */
	// TODO modify this method as necessary - since the IE doesn't seem to be
	// present, we have no idea what the format looks like
	public static PowerCapability parse(JsonObject contents) {
		return new PowerCapability(
			contents.get("Minimum Tx Power Capability").getAsByte(),
			contents.get("Maximum Tx Power Capability").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(minimumTxPowerCapability, maximumTxPowerCapability);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}

		if (this == obj) {
			return true;
		}

		if (getClass() != obj.getClass()) {
			return false;
		}

		PowerCapability other = (PowerCapability) obj;
		return minimumTxPowerCapability == other.minimumTxPowerCapability &&
			maximumTxPowerCapability == other.maximumTxPowerCapability;
	}
}
