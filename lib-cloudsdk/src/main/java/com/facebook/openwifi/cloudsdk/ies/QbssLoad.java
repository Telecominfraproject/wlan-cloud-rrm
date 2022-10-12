/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.ies;

import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It is called
 * "QBSS Load" in these entries, and just "BSS Load" in the 802.11
 * specification. Refer to the specification for more details. Language in
 * javadocs is taken from the specification.
 */
public class QbssLoad {

	/** Defined in 802.11 */
	public static final int TYPE = 11;

	/**
	 * The total number of STAs currently associated with the BSS.
	 */
	public final int stationCount;
	/**
	 * The Channel Utilization field is defined as the percentage of time,
	 * linearly scaled with 255 representing 100%, that the AP sensed the
	 * medium was busy, as indicated by either the physical or virtual carrier
	 * sense (CS) mechanism. When more than one channel is in use for the BSS,
	 * the Channel Utilization field value is calculated only for the primary
	 * channel. This percentage is computed using the following formula:
	 * <p>
	 * floor(255 * channelBusyTime /
	 * 		(dot11ChannelUtilizationBeaconIntervals * dot11BeaconPeriod * 1024)
	 * )
	 */
	public final int channelUtilization;
	/**
	 * The Available Admission Capacity field contains an unsigned integer that
	 * specifies the remaining amount of medium time available via explicit
	 * admission control, in units of 32 miscrosecond/second. The field is
	 * helpful for roaming STAs to select an AP that is likely to accept future
	 * admission control requests, but it does not represent an assurance that
	 * the HC admits these requests.
	 */
	public final int availableAdmissionCapacity;

	/** Constructor */
	public QbssLoad(
		int stationCount,
		int channelUtilization,
		int availableAdmissionCapacity
	) {
		this.stationCount = stationCount;
		this.channelUtilization = channelUtilization;
		this.availableAdmissionCapacity = availableAdmissionCapacity;
	}

	/** Parse QbssLoad IE from appropriate Json object; return null if invalid. */
	public static QbssLoad parse(JsonObject contents) {
		// unclear why there is this additional nested layer
		JsonElement ccaContentJsonElement = contents.get("802.11e CCA Version");
		if (ccaContentJsonElement == null) {
			return null;
		}
		contents = ccaContentJsonElement.getAsJsonObject();
		final int stationCount = contents.get("Station Count").getAsInt();
		final int channelUtilization =
			contents.get("Channel Utilization").getAsInt();
		final int availableAdmissionCapacity =
			contents.get("Available Admission Capabilities").getAsInt();
		return new QbssLoad(
			stationCount,
			channelUtilization,
			availableAdmissionCapacity
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			availableAdmissionCapacity,
			channelUtilization,
			stationCount
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
		QbssLoad other = (QbssLoad) obj;
		return availableAdmissionCapacity == other.availableAdmissionCapacity &&
			channelUtilization == other.channelUtilization &&
			stationCount == other.stationCount;
	}

	@Override
	public String toString() {
		return "QbssLoad [stationCount=" + stationCount +
			", channelUtilization=" + channelUtilization +
			", availableAdmissionCapacity=" + availableAdmissionCapacity + "]";
	}
}
