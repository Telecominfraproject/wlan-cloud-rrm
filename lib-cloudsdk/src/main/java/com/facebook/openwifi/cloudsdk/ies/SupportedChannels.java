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

// NOTE: Not validated (not seen on test devices)
/**
 * This information element (IE) appears in wifiscan entries. It's called
 * "Supported Channels" in 802.11 specs (section 9.4.2.17). Refer to the
 * specification for more details. Language in javadocs is taken from the
 * specification.
 */
public class SupportedChannels {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 36;

	/** Unsigned 8 bits - first channel in a subband of supported channels */
	public final short firstChannelNumber;
	/** Unsigned 8 bits - number of channels in a subband of supported channels */
	public final short numberOfChannels;

	/** Constructor */
	public SupportedChannels(short firstChannelNumber, short numberOfChannels) {
		this.firstChannelNumber = firstChannelNumber;
		this.numberOfChannels = numberOfChannels;
	}

	/** Parse SupportedChannels from JSON object */
	// TODO modify this method as necessary - since the IE doesn't seem to be
	// present, we have no idea what the format looks like
	public static SupportedChannels parse(JsonObject contents) {
		return new SupportedChannels(
			contents.get("First Channel Number").getAsShort(),
			contents.get("Number of Channels").getAsShort()
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
}
