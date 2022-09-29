/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.informationelement;

import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It is called
 * "Tx Pwr Info" in these entries, and "Transmit Power Envelope" in the 802.11
 * specification. Refer to the specification for more details. Language in
 * javadocs is taken from the specification.
 */
public class TxPwrInfo {

	/** Defined in 802.11 */
	public static final int TYPE = 195;

	/** Local maximum transmit power for 20 MHz. Required field. */
	public final Integer localMaxTxPwrConstraint20MHz;
	/** Local maximum transmit power for 40 MHz. Optional field. */
	public final Integer localMaxTxPwrConstraint40MHz;
	/** Local maximum transmit power for 80 MHz. Optional field. */
	public final Integer localMaxTxPwrConstraint80MHz;
	/** Local maximum transmit power for both 160 MHz and 80+80 MHz. Optional field. */
	public final Integer localMaxTxPwrConstraint160MHz;

	/** Constructor */
	public TxPwrInfo(
		int localMaxTxPwrConstraint20MHz,
		int localMaxTxPwrConstraint40MHz,
		int localMaxTxPwrConstraint80MHz,
		int localMaxTxPwrConstraint160MHz
	) {
		this.localMaxTxPwrConstraint20MHz = localMaxTxPwrConstraint20MHz;
		this.localMaxTxPwrConstraint40MHz = localMaxTxPwrConstraint40MHz;
		this.localMaxTxPwrConstraint80MHz = localMaxTxPwrConstraint80MHz;
		this.localMaxTxPwrConstraint160MHz = localMaxTxPwrConstraint160MHz;
	}

	/** Parse TxPwrInfo IE from appropriate Json object. */
	public static TxPwrInfo parse(JsonObject contents) {
		// required field
		int localMaxTxPwrConstraint20MHz =
			contents.get("Local Max Tx Pwr Constraint 20MHz").getAsInt();
		// optional field
		Integer localMaxTxPwrConstraint40MHz =
			parseOptionalField(contents, "Local Max Tx Pwr Constraint 40MHz");
		Integer localMaxTxPwrConstraint80MHz =
			parseOptionalField(contents, "Local Max Tx Pwr Constraint 40MHz");
		Integer localMaxTxPwrConstraint160MHz =
			parseOptionalField(contents, "Local Max Tx Pwr Constraint 40MHz");
		return new TxPwrInfo(
			localMaxTxPwrConstraint20MHz,
			localMaxTxPwrConstraint40MHz,
			localMaxTxPwrConstraint80MHz,
			localMaxTxPwrConstraint160MHz
		);
	}

	private static Integer parseOptionalField(
		JsonObject contents,
		String fieldName
	) {
		JsonElement element = contents.get(fieldName);
		if (element == null) {
			return null;
		}
		return element.getAsInt();
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			localMaxTxPwrConstraint160MHz,
			localMaxTxPwrConstraint20MHz,
			localMaxTxPwrConstraint40MHz,
			localMaxTxPwrConstraint80MHz
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		TxPwrInfo other = (TxPwrInfo) obj;
		return localMaxTxPwrConstraint160MHz ==
			other.localMaxTxPwrConstraint160MHz &&
			localMaxTxPwrConstraint20MHz ==
				other.localMaxTxPwrConstraint20MHz &&
			localMaxTxPwrConstraint40MHz ==
				other.localMaxTxPwrConstraint40MHz &&
			localMaxTxPwrConstraint80MHz == other.localMaxTxPwrConstraint80MHz;
	}

	@Override
	public String toString() {
		return "TxPwrInfo [localMaxTxPwrConstraint20MHz=" +
			localMaxTxPwrConstraint20MHz + ", localMaxTxPwrConstraint40MHz=" +
			localMaxTxPwrConstraint40MHz + ", localMaxTxPwrConstraint80MHz=" +
			localMaxTxPwrConstraint80MHz + ", localMaxTxPwrConstraint160MHz=" +
			localMaxTxPwrConstraint160MHz + "]";
	}
}
