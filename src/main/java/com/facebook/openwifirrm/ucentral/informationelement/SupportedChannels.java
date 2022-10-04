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
 * not appear in these entries AFAICT. It's called "Supported Channels" in
 * 802.11 specs (section 9.4.2.17). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class SupportedChannels {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 36;

	/** first channel in a subband of supported channels */
	public final byte firstChannelNumber;
	/** number of channels in a subband of supported channels */
	public final byte numberOfChannels;

	/** Constructor */
	public SupportedChannels(byte firstChannelNumber, byte numberOfChannels) {
		this.firstChannelNumber = firstChannelNumber;
		this.numberOfChannels = numberOfChannels;
	}

	/** Parse SupportedChannels from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static SupportedChannels parse(JsonObject contents) {
		return new SupportedChannels(
			contents.get("First Channel Number").getAsByte(),
			contents.get("Number of Channels").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(firstChannelNumber, numberOfChannels);
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

		SupportedChannels other = (SupportedChannels) obj;
		return firstChannelNumber == other.firstChannelNumber &&
			numberOfChannels == other.numberOfChannels;
	}

	@Override
	public String toString() {
		return String.format(
			"SupportedChannels[firstChannelNumber=%d, numberOfChannels=%d]",
			firstChannelNumber,
			numberOfChannels
		);
	}
}
