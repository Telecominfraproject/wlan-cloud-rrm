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
 * not appear in these entries AFAICT. It's called "BSS AC Access Delay" in
 * 802.11 specs (section 9.4.2.43). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class BssAcAccessDelay {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 68;

	public final int accessCategoryAccessDelay;

	/** Constructor */
	public BssAcAccessDelay(int accessCategoryAccessDelay) {
		this.accessCategoryAccessDelay = accessCategoryAccessDelay;
	}

	/** Parse BssAcAccessDelay from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static BssAcAccessDelay parse(JsonObject contents) {
		return new BssAcAccessDelay(
			contents.get("AP Average Access Delay").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accessCategoryAccessDelay);
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

		BssAcAccessDelay other = (BssAcAccessDelay) obj;
		return accessCategoryAccessDelay == other.accessCategoryAccessDelay;
	}

	@Override
	public String toString() {
		return String.format(
			"BssAcAccessDelay[accessCategoryAccessDelay=%d]",
			accessCategoryAccessDelay
		);
	}
}
