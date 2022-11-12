/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.ies;

import java.util.Objects;

import com.facebook.openwifi.cloudsdk.IEUtils;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It is called
 * "Tx Pwr Info" in these entries, and "Transmit Power Envelope" in the 802.11
 * specification (section 9.4.2.161). Refer to the specification for more details. Language in
 * javadocs is taken from the specification.
 */
public class TxPwrInfo {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 195;

	/**
	 * Unsigned 8 bits - Local maximum transmit power for 20 MHz. Required field.
	 */
	public final Short localMaxTxPwrConstraint20MHz;
	/**
	 * Unsigned 8 bits - Local maximum transmit power for 40 MHz. Optional field.
	 */
	public final Short localMaxTxPwrConstraint40MHz;
	/**
	 * Unsigned 8 bits - Local maximum transmit power for 80 MHz. Optional field.
	 */
	public final Short localMaxTxPwrConstraint80MHz;
	/**
	 * Unsigned 8 bits - Local maximum transmit power for both 160 MHz and 80+80 MHz. Optional field.
	 */
	public final Short localMaxTxPwrConstraint160MHz;

	/** Constructor */
	public TxPwrInfo(
		short localMaxTxPwrConstraint20MHz,
		Short localMaxTxPwrConstraint40MHz,
		Short localMaxTxPwrConstraint80MHz,
		Short localMaxTxPwrConstraint160MHz
	) {
		this.localMaxTxPwrConstraint20MHz = localMaxTxPwrConstraint20MHz;
		this.localMaxTxPwrConstraint40MHz = localMaxTxPwrConstraint40MHz;
		this.localMaxTxPwrConstraint80MHz = localMaxTxPwrConstraint80MHz;
		this.localMaxTxPwrConstraint160MHz = localMaxTxPwrConstraint160MHz;
	}

	/** Parse TxPwrInfo IE from appropriate Json object. */
	public static TxPwrInfo parse(JsonObject contents) {
		JsonObject innerObj = contents.get("Tx Pwr Info").getAsJsonObject();
		// required field
		short localMaxTxPwrConstraint20MHz =
			contents.get("Local Max Tx Pwr Constraint 20MHz").getAsShort();
		// optional field
		Short localMaxTxPwrConstraint40MHz =
			IEUtils.parseOptionalShortField(
				contents,
				"Local Max Tx Pwr Constraint 40MHz"
			);
		Short localMaxTxPwrConstraint80MHz =
			IEUtils.parseOptionalShortField(
				contents,
				"Local Max Tx Pwr Constraint 40MHz"
			);
		Short localMaxTxPwrConstraint160MHz =
			IEUtils.parseOptionalShortField(
				contents,
				"Local Max Tx Pwr Constraint 40MHz"
			);
		return new TxPwrInfo(
			localMaxTxPwrConstraint20MHz,
			localMaxTxPwrConstraint40MHz,
			localMaxTxPwrConstraint80MHz,
			localMaxTxPwrConstraint160MHz
		);
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
}
