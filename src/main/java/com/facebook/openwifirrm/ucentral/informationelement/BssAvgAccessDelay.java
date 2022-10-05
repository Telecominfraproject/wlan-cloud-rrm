/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.informationelement;

import java.util.Objects;

import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It currently does
 * not appear in these entries AFAICT. It's called "BSS Average Access Delay" in
 * 802.11 specs (section 9.4.2.38). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class BssAvgAccessDelay {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 63;

	/** number of channels in a subband of supported channels */
	public final byte apAvgAccessDelay;

	/** Constructor */
	public BssAvgAccessDelay(byte apAvgAccessDelay) {
		this.apAvgAccessDelay = apAvgAccessDelay;
	}

	/** Parse BssAvgAccessDelay from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static BssAvgAccessDelay parse(JsonObject contents) {
		return new BssAvgAccessDelay(
			contents.get("AP Average Access Delay").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(apAvgAccessDelay);
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

		BssAvgAccessDelay other = (BssAvgAccessDelay) obj;
		return apAvgAccessDelay == other.apAvgAccessDelay;
	}

	@Override
	public String toString() {
		return String.format(
			"BssAvgAccessDelay[apAvgAccessDelay=%d]",
			apAvgAccessDelay
		);
	}
}
