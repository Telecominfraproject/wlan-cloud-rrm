/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.ies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries.
 * Refer to the 802.11 specification (section 9.4.2.8) for more details.
 * Language in javadocs is taken from the specification.
 */
public class Country {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 7;

	/** Constraints for a subset of channels in the AP's country */
	public static class CountryInfo {
		/**
		 * 8 bits unsigned - the lowest channel number in the CountryInfo.
		 */
		public final short firstChannelNumber;
		/**
		 * 8 bits unsigned - The maximum power, in dBm, allowed to be transmitted.
		 */
		public final short maximumTransmitPowerLevel;
		/**
		 * 8 bits unsigned - Number of channels this CountryInfo applies to. E.g.,
		 * if First Channel Number is 2 and Number of Channels is 4, this CountryInfo
		 * describes channels 2, 3, 4, and 5.
		 */
		public final short numberOfChannels;

		/** Constructor. */
		public CountryInfo(
			short firstChannelNumber,
			short maximumTransmitPowerLevel,
			short numberOfChannels
		) {
			this.firstChannelNumber = firstChannelNumber;
			this.maximumTransmitPowerLevel = maximumTransmitPowerLevel;
			this.numberOfChannels = numberOfChannels;
		}

		/** Parse CountryInfo from the appropriate Json object. */
		public static CountryInfo parse(JsonObject contents) {
			final short firstChannelNumber =
				contents.get("First Channel Number").getAsShort();
			final short maximumTransmitPowerLevel = contents
				.get("Maximum Transmit Power Level (in dBm)")
				.getAsShort();
			final short numberOfChannels =
				contents.get("Number of Channels").getAsShort();
			return new CountryInfo(
				firstChannelNumber,
				maximumTransmitPowerLevel,
				numberOfChannels
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				firstChannelNumber,
				maximumTransmitPowerLevel,
				numberOfChannels
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
			CountryInfo other = (CountryInfo) obj;
			return firstChannelNumber == other.firstChannelNumber &&
				maximumTransmitPowerLevel == other.maximumTransmitPowerLevel &&
				numberOfChannels == other.numberOfChannels;
		}
	}

	/** Country */
	public final String country;
	/**
	 * Each constraint is a CountryInfo describing tx power constraints on
	 * one or more channels, for the current country.
	 */
	public final List<CountryInfo> constraints;

	/** Constructor */
	public Country(
		String country,
		List<CountryInfo> countryInfos
	) {
		this.country = country;
		this.constraints = Collections.unmodifiableList(countryInfos);
	}

	/** Parse Country IE from the appropriate Json object. */
	public static Country parse(JsonObject contents) {
		List<CountryInfo> constraints = new ArrayList<>();
		JsonElement constraintsObject = contents.get("constraints");
		if (constraintsObject != null) {
			for (JsonElement jsonElement : constraintsObject.getAsJsonArray()) {
				JsonObject innerElem = jsonElement.getAsJsonObject();
				CountryInfo countryInfo =
					CountryInfo.parse(innerElem.get("Country Info").getAsJsonObject());
				constraints.add(countryInfo);
			}
		}
		return new Country(
			contents.get("Code").getAsString(),
			constraints
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(constraints);
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
		Country other = (Country) obj;
		return Objects.equals(constraints, other.constraints);
	}
}
