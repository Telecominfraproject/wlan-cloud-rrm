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

// NOTE: From what I can see it currently does not appear in the list of IEs,
// although it's possible it'll be there in the future.
/**
 * This information element (IE) appears in wifiscan entries. It's called "BSS Average Access Delay" in
 * 802.11 specs (section 9.4.2.38). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class BssAvgAccessDelay {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 63;

	/**
	 * Unsigned 8 bits representing a scaled average medium access delay for all DCF
	 * and EDCAF frames transmitted, measured from the time it's ready for
	 * transmission to actual transmission start time.
	 */
	public final short apAvgAccessDelay;

	/** Constructor */
	public BssAvgAccessDelay(short apAvgAccessDelay) {
		this.apAvgAccessDelay = apAvgAccessDelay;
	}

	/** Parse BssAvgAccessDelay from JSON object */
	// TODO modify this method as necessary - since the IE doesn't seem to be
	// present, we have no idea what the format looks like
	public static BssAvgAccessDelay parse(JsonObject contents) {
		return new BssAvgAccessDelay(
			contents.get("AP Average Access Delay").getAsShort()
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
}
