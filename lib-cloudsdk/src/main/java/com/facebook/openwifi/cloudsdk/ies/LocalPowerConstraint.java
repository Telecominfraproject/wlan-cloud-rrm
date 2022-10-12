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

/**
 * This information element (IE) appears in wifiscan entries. It is called
 * "Local Power Constraint" in these entries, and just "Power Constraint" in
 * the 802.11 specification. Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class LocalPowerConstraint {

	/** Defined in 802.11 */
	public static final int TYPE = 32;

	/**
	 * Units are dB.
	 * <p>
	 * The local maximum transmit power for a channel is defined as the maximum
	 * transmit power level specified for the channel in the Country IE minus
	 * this variable for the given channel.
	 */
	public final int localPowerConstraint;

	/** Constructor */
	public LocalPowerConstraint(int localPowerConstraint) {
		this.localPowerConstraint = localPowerConstraint;
	}

	/** Parse LocalPowerConstraint IE from appropriate Json object. */
	public static LocalPowerConstraint parse(JsonObject contents) {
		final int localPowerConstraint =
			contents.get("Local Power Constraint").getAsInt();
		return new LocalPowerConstraint(localPowerConstraint);
	}

	@Override
	public int hashCode() {
		return Objects.hash(localPowerConstraint);
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
		LocalPowerConstraint other = (LocalPowerConstraint) obj;
		return localPowerConstraint == other.localPowerConstraint;
	}

	@Override
	public String toString() {
		return "LocalPowerConstraint [localPowerConstraint=" +
			localPowerConstraint + "]";
	}
}
