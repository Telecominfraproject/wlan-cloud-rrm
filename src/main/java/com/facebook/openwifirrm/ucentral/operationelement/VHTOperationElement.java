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
 * Very High Throughput (VHT) Operation Element, which is potentially present in
 * wifiscan entries. Introduced in 802.11ac (2013).
 */
public class VHTOperationElement implements OperationElement {

	/** False if the channel width is 20 MHz or 40 MHz; true otherwise. */
	private final boolean channelWidthIndicator;
	/**
	 * If the channel is 20 MHz, 40 MHz, or 80 MHz wide, this parameter is the
	 * channel number. E.g., the channel centered at 5180 MHz is channel 36. For a
	 * 160 MHz wide channel, this parameter is the channel number of the 80MHz
	 * channel that contains the primary channel. For a 80+80 MHz wide channel, this
	 * parameter is the channel number of the primary channel.
	 */
	private final byte channel1;
	/**
	 * This should be zero unless the channel is 160MHz or 80+80 MHz wide. If the
	 * channel is 160 MHz wide, this parameter is the channel number of the 160 MHz
	 * wide channel. If the channel is 80+80 MHz wide, this parameter is the channel
	 * index of the secondary 80 MHz wide channel.
	 */
	private final byte channel2;
	/**
	 * An 8-element array where each element is between 0 and 4 inclusive. MCS means
	 * Modulation and Coding Scheme. NSS means Number of Spatial Streams. There can
	 * be 1, 2, ..., or 8 spatial streams. For each NSS, the corresponding element
	 * in the array should specify which MCSs are supported for that NSS in the
	 * following manner: 0 indicates support for VHT-MCS 0-7, 1 indicates support
	 * for VHT-MCS 0-8, 2 indicates support for VHT-MCS 0-9, and 3 indicates that no
	 * VHT-MCS is supported for that NSS. For the specifics of what each VHT-MCS is,
	 * see IEEE 802.11-2020, Table "21-29" through Table "21-60".
	 */
	private final byte[] vhtMcsForNss;

	/**
	 * Constructs a {@code VHTOperationElement} by decoding {@code vhtOper}.
	 *
	 * @param vhtOper a base64 encoded properly formatted VHT operation element (see
	 *                802.11 standard)
	 */
	public VHTOperationElement(String vhtOper) {
		byte[] bytes = Base64.decodeBase64(vhtOper);
		this.channelWidthIndicator = bytes[0] == 1;
		this.channel1 = bytes[1];
		this.channel2 = bytes[2];
		byte[] vhtMcsForNss = new byte[8];
		vhtMcsForNss[0] = (byte) (bytes[3] >>> 6);
		vhtMcsForNss[1] = (byte) ((bytes[3] & 0b00110000) >>> 4);
		vhtMcsForNss[2] = (byte) ((bytes[3] & 0b00001100) >>> 2);
		vhtMcsForNss[3] = (byte) (bytes[3] & 0b00000011);
		vhtMcsForNss[4] = (byte) (bytes[4] >>> 6);
		vhtMcsForNss[5] = (byte) ((bytes[4] & 0b00110000) >>> 4);
		vhtMcsForNss[6] = (byte) ((bytes[4] & 0b00001100) >>> 2);
		vhtMcsForNss[7] = (byte) (bytes[4] & 0b00000011);
		this.vhtMcsForNss = vhtMcsForNss;
	}

	/**
	 * Constructs an {@code HTOperationElement} using the given field values. See
	 * 802.11 for more details.
	 * <p>
	 * For details about the parameters, see the javadocs for the corresponding
	 * member variables.
	 */
	public VHTOperationElement(boolean channelWidthIndicator, byte channel1, byte channel2, byte[] vhtMcsForNss) {
		/*
		 * XXX some combinations of channelWidth, channel, channel2, and vhtMcsAtNss are
		 * invalid, but this is not checked here. If fidelity to 802.11 is required, the
		 * caller of this method must make sure to pass in valid parameters.
		 */
		this.channelWidthIndicator = channelWidthIndicator;
		this.channel1 = channel1;
		this.channel2 = channel2;
		this.vhtMcsForNss = vhtMcsForNss;
	}

	@Override
	public boolean matchesForAggregation(OperationElement otherOper) {
		// check everything except vhtMcsForNss
		if (otherOper == null || getClass() == otherOper.getClass()) {
			return false;
		}
		VHTOperationElement other = (VHTOperationElement) otherOper;
		return channel1 == other.channel1 && channel2 == other.channel2
				&& channelWidthIndicator == other.channelWidthIndicator;
	}

	/**
	 * Determines whether two VHT operation elements should have their statistics
	 * aggregated.
	 *
	 * @param vhtOper1 a base64 encoded properly formatted VHT operation element
	 *                 (see 802.11 standard)
	 *
	 * @param vhtOper2 a base64 encoded properly formatted VHT operation element
	 *                 (see 802.11 standard)
	 * @return true if the two inputs should have their statistics aggregated; false
	 *         otherwise.
	 */
	public static boolean matchesVhtForAggregation(String vhtOper1, String vhtOper2) {
		if (Objects.equals(vhtOper1, vhtOper2)) {
			return true; // true if both are null or they are equal
		}
		if (vhtOper1 == null || vhtOper2 == null) {
			return false; // false if exactly one is null
		}
		VHTOperationElement vhtOperObj1 = new VHTOperationElement(vhtOper1);
		VHTOperationElement vhtOperObj2 = new VHTOperationElement(vhtOper2);
		return vhtOperObj1.matchesForAggregation(vhtOperObj2);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(vhtMcsForNss);
		result = prime * result + Objects.hash(channel1, channel2, channelWidthIndicator);
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
		VHTOperationElement other = (VHTOperationElement) obj;
		return channel1 == other.channel1 && channel2 == other.channel2
				&& channelWidthIndicator == other.channelWidthIndicator
				&& Arrays.equals(vhtMcsForNss, other.vhtMcsForNss);
	}
}