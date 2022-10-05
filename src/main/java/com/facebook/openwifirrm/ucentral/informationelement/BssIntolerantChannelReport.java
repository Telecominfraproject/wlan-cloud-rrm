/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.informationelement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It currently does
 * not appear in these entries AFAICT. It's called "20/40 BSS Intolerant Channel
 * Report" in 802.11 specs (section 9.4.2.57). Refer to the specification for
 * more details. Language in javadocs is taken from the specification.
 */
public class BssIntolerantChannelReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 73;

	/** unsigned 8 bits representing the operating class in which the channel list is
	 * valid
	 */
	public final short operatingClass;
	/** List of unsigned 8 bits, representing the channel numbers */
	public final List<Short> channelList;

	/** Constructor */
	public BssIntolerantChannelReport(
		short operatingClass,
		List<Short> channelList
	) {
		this.operatingClass = operatingClass;
		this.channelList = Collections.unmodifiableList(channelList);
	}

	/** Parse BssIntolerantChannelReport from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static BssIntolerantChannelReport parse(JsonObject contents) {
		List<Short> channelList = new ArrayList<>();
		JsonElement channelListJson = contents.get("Channel List");
		if (channelListJson != null) {
			for (JsonElement elem : channelListJson.getAsJsonArray()) {
				channelList.add(elem.getAsShort());
			}
		}

		return new BssIntolerantChannelReport(
			contents.get("Operating Class").getAsShort(),
			channelList
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(operatingClass, channelList);
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

		BssIntolerantChannelReport other = (BssIntolerantChannelReport) obj;
		return operatingClass == other.operatingClass &&
			channelList.equals(other.channelList);
	}

	@Override
	public String toString() {
		return String.format(
			"BssIntolerantChannelReport[operatingClass=%d, channelList=%s]",
			operatingClass,
			channelList.toString()
		);
	}
}
