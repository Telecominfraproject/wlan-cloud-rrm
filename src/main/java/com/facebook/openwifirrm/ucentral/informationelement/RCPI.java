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
 * not appear in these entries AFAICT. It's called "RCPI" in 802.11 specs
 * (section 9.4.2.37). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class RCPI {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 53;

	public final byte rcpi;

	/** Constructor */
	public RCPI(byte rcpi) {
		this.rcpi = rcpi;
	}

	/** Parse RCPI from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static RCPI parse(JsonObject contents) {
		return new RCPI(
			contents.get("RCPI").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(rcpi);
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

		RCPI other = (RCPI) obj;
		return rcpi == other.rcpi;
	}

	@Override
	public String toString() {
		return String.format(
			"RCPI[rcpi=%d]",
			rcpi
		);
	}
}
