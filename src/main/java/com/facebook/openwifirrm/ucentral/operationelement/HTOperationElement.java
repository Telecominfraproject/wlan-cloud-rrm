/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.operationelement;

import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;

/**
 * High Throughput (HT) Operation Element, which is potentially present in
 * wifiscan entries. Introduced in 802.11n (2009).
 */
public class HTOperationElement {

	/** Channel number of the primary channel. */
	public final byte primaryChannel;
	/**
	 * Indicates the offset of the secondary channel relative to the primary
	 * channel. A 1 indicates that the secondary channel is above the primary
	 * channel. A 3 indicates that the secondary channel is below the primary
	 * channel. A 0 indicates that there is no secondary channel present. The value
	 * 2 is reserved.
	 */
	public final byte secondaryChannelOffset;
	/**
	 * Defines the channel widths that can be used to transmit to the STA. With
	 * exceptions, false allows a 20 MHz channel width. True allows use of any
	 * channel width in the supported channel width set. See 802.11 for exceptions.
	 */
	public final boolean staChannelWidth;
	/** True if RIFS is permitted; false otherwise. */
	public final boolean rifsMode;
	/**
	 * A 0 indicates no protection mode. A 1 indicates nonmember protection mode. A
	 * 2 indicates 20 MHz protection mode. A 3 indicates non-HT mixed mode.
	 */
	public final byte htProtection;
	/**
	 * False if all HT STAs that are associated are HT-greenfield capable or all HT
	 * peer mesh STAs are HT-greenfield capable; true otherwise.
	 */
	public final boolean nongreenfieldHtStasPresent;
	/**
	 * Indicates if the use of protection for non-HT STAs by overlapping BSSs is
	 * determined to be desirable. See 802.11 for details.
	 */
	public final boolean obssNonHtStasPresent;
	/**
	 * Defines the channel center frequency for a 160 or 80+80 MHz BSS bandwidth
	 * with NSS support less than Max VHT NSS. This is 0 for non-VHT STAs. See
	 * 802.11 for details.
	 */
	public final byte channelCenterFrequencySegment2;
	/** False if no STBC beacon is transmitted; true otherwise. */
	public final boolean dualBeacon;
	/** False if dual CTS protection is not required; true otherwise. */
	public final boolean dualCtsProtection;
	/** False in a primary beacon. True in an STBC beacon. */
	public final boolean stbcBeacon;
	/**
	 * Indicates the HT-MCS values that are supported by all HT STAs in the BSS. A
	 * bitmap where a bit is set to 1 to indicate support for that MCS and 0
	 * otherwise, where bit 0 corresponds to MCS 0.
	 */
	public final byte[] basicHtMcsSet;

	/**
	 * Constructs an {@code HTOperationElement} using the given field values. See
	 * 802.11 for more details.
	 * <p>
	 * For details about the parameters, see the javadocs for the corresponding
	 * member variables.
	 */
	public HTOperationElement(
		byte primaryChannel,
		byte secondaryChannelOffset,
		boolean staChannelWidth,
		boolean rifsMode,
		byte htProtection,
		boolean nongreenfieldHtStasPresent,
		boolean obssNonHtStasPresent,
		byte channelCenterFrequencySegment2,
		boolean dualBeacon,
		boolean dualCtsProtection,
		boolean stbcBeacon
	) {
		/*
		 * XXX some combinations of these parameters may be invalid as defined by
		 * 802.11-2020, but this is not checked here. If fidelity to 802.11 is required,
		 * the caller of this method must make sure to pass in valid parameters. The
		 * 802.11-2020 specification has more details about the parameters.
		 */
		this.primaryChannel = primaryChannel;
		this.secondaryChannelOffset = secondaryChannelOffset;
		this.staChannelWidth = staChannelWidth;
		this.rifsMode = rifsMode;
		this.htProtection = htProtection;
		this.nongreenfieldHtStasPresent = nongreenfieldHtStasPresent;
		this.obssNonHtStasPresent = obssNonHtStasPresent;
		this.channelCenterFrequencySegment2 = channelCenterFrequencySegment2;
		this.dualBeacon = dualBeacon;
		this.dualCtsProtection = dualCtsProtection;
		this.stbcBeacon = stbcBeacon;
		// the next 16 bytes are for the basic HT-MCS set
		// a default is chosen; if needed, we can add a parameter to set these
		this.basicHtMcsSet = new byte[16];
	}

	/** Constructor with the most used parameters. */
	public HTOperationElement(
		byte primaryChannel,
		byte secondaryChannelOffset,
		boolean staChannelWidth,
		byte channelCenterFrequencySegment2
	) {
		this(
			primaryChannel,
			secondaryChannelOffset,
			staChannelWidth,
			false,
			(byte) 0,
			true,
			false,
			channelCenterFrequencySegment2,
			false,
			false,
			false
		);
	}

	/**
	 * Constructs an {@code HTOperationElement} by decoding {@code htOper}.
	 *
	 * @param htOper a base64 encoded properly formatted HT operation element (see
	 *               802.11)
	 */
	public HTOperationElement(String htOper) {
		byte[] bytes = Base64.decodeBase64(htOper);
		/*
		 * Note that the code here may seem to read "reversed" compared to 802.11. This
		 * is because the bits within a byte are delivered from MSB to LSB, whereas the
		 * 802.11 graphic shows the bits LSB-first. At least, this is our understanding
		 * from looking at 802.11 and at the actual HT operation elements from the
		 * edgecore APs.
		 */
		this.primaryChannel = bytes[0];
		this.rifsMode = ((bytes[1] & 0b00001000) >>> 3) == 1;
		this.staChannelWidth = ((bytes[1] & 0b00000100) >>> 2) == 1;
		this.secondaryChannelOffset = (byte) (bytes[1] & 0b00000011);
		byte channelCenterFrequencySegment2LastThreeBits =
			(byte) ((bytes[2] & 0b11100000) >>> 5);
		this.obssNonHtStasPresent = ((bytes[2] & 0b00010000) >>> 4) == 1;
		this.nongreenfieldHtStasPresent = ((bytes[2] & 0b00000100) >>> 2) == 1;
		this.htProtection = (byte) (bytes[2] & 0b00000011);
		byte channelCenterFrequencySegment2FirstFiveBits =
			(byte) (bytes[3] & 0b00011111);
		this.channelCenterFrequencySegment2 =
			(byte) (((byte) (channelCenterFrequencySegment2FirstFiveBits <<
				3)) | channelCenterFrequencySegment2LastThreeBits);
		this.dualCtsProtection = ((bytes[4] & 0b10000000) >>> 7) == 1;
		this.dualBeacon = ((bytes[4] & 0b01000000) >>> 6) == 1;
		this.stbcBeacon = (bytes[5] & 0b00000001) == 1;
		byte[] basicHtMcsSet = new byte[16];
		for (int i = 0; i < basicHtMcsSet.length; i++) {
			basicHtMcsSet[i] = bytes[6 + i];
		}
		this.basicHtMcsSet = basicHtMcsSet;
	}

	/**
	 * Determine whether {@code this} and {@code other} "match" for the purpose of
	 * aggregating statistics.
	 *
	 * @param other another HT operation element
	 * @return true if the the operation elements "match" for the purpose of
	 *         aggregating statistics; false otherwise.
	 */
	public boolean matchesForAggregation(HTOperationElement other) {
		return other != null && primaryChannel == other.primaryChannel &&
			secondaryChannelOffset == other.secondaryChannelOffset &&
			staChannelWidth == other.staChannelWidth &&
			channelCenterFrequencySegment2 ==
				other.channelCenterFrequencySegment2;
	}

	/**
	 * Determines whether two HT operation elements should have their statistics
	 * aggregated.
	 *
	 * @param htOper1 a base64 encoded properly formatted HT operation element (see
	 *                802.11)
	 * @param htOper2 a base64 encoded properly formatted HT operation element (see
	 *                802.11)
	 * @return true if the two inputs should have their statistics aggregated; false
	 *         otherwise.
	 */
	public static boolean matchesHtForAggregation(
		String htOper1,
		String htOper2
	) {
		if (Objects.equals(htOper1, htOper2)) {
			return true; // true if both are null or they are equal
		}
		if (htOper1 == null || htOper2 == null) {
			return false; // false if exactly one is null
		}
		HTOperationElement htOperObj1 = new HTOperationElement(htOper1);
		HTOperationElement htOperObj2 = new HTOperationElement(htOper2);
		return htOperObj1.matchesForAggregation(htOperObj2);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(basicHtMcsSet);
		result = prime * result + Objects.hash(
			channelCenterFrequencySegment2,
			dualBeacon,
			dualCtsProtection,
			htProtection,
			nongreenfieldHtStasPresent,
			obssNonHtStasPresent,
			primaryChannel,
			rifsMode,
			secondaryChannelOffset,
			staChannelWidth,
			stbcBeacon
		);
		return result;
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
		HTOperationElement other = (HTOperationElement) obj;
		return Arrays.equals(basicHtMcsSet, other.basicHtMcsSet) &&
			channelCenterFrequencySegment2 ==
				other.channelCenterFrequencySegment2 &&
			dualBeacon == other.dualBeacon &&
			dualCtsProtection == other.dualCtsProtection &&
			htProtection == other.htProtection &&
			nongreenfieldHtStasPresent == other.nongreenfieldHtStasPresent &&
			obssNonHtStasPresent == other.obssNonHtStasPresent &&
			primaryChannel == other.primaryChannel &&
			rifsMode == other.rifsMode &&
			secondaryChannelOffset == other.secondaryChannelOffset &&
			staChannelWidth == other.staChannelWidth &&
			stbcBeacon == other.stbcBeacon;
	}
}