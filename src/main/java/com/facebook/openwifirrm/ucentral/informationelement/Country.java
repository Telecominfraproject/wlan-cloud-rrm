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
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries.
 * Refer to the 802.11 specification for more details. Language in
 * javadocs is taken from the specification.
 */
public class Country {
	private static final Logger logger = LoggerFactory.getLogger(Country.class);

	/** Defined in 802.11 */
	public static final int TYPE = 7;

	/** Constraints for a subset of channels in the AP's country */
	public static class CountryInfo {
		/**
		 * The lowest channel number in the CountryInfo.
		 */
		public final int firstChannelNumber;
		/**
		 * The maximum power, in dBm, allowed to be transmitted.
		 */
		public final int maximumTransmitPowerLevel;
		/**
		 * Number of channels this CountryInfo applies to. E.g., if First
		 * Channel Number is 2 and Number of Channels is 4, this CountryInfo
		 * describes channels 2, 3, 4, and 5.
		 */
		public final int numberOfChannels;

		/** Constructor. */
		public CountryInfo(
			int firstChannelNumber,
			int maximumTransmitPowerLevel,
			int numberOfChannels
		) {
			this.firstChannelNumber = firstChannelNumber;
			this.maximumTransmitPowerLevel = maximumTransmitPowerLevel;
			this.numberOfChannels = numberOfChannels;
		}

		/** Parse CountryInfo from the appropriate Json object. */
		public static CountryInfo parse(JsonObject contents) {
			final int firstChannelNumber =
				contents.get("First Channel Number").getAsInt();
			final int maximumTransmitPowerLevel = contents
				.get("Maximum Transmit Power Level (in dBm)")
				.getAsInt();
			final int numberOfChannels =
				contents.get("Number of Channels").getAsInt();
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

		@Override
		public String toString() {
			return "CountryInfo [firstChannelNumber=" + firstChannelNumber +
				", maximumTransmitPowerLevel=" + maximumTransmitPowerLevel +
				", numberOfChannels=" + numberOfChannels + "]";
		}
	}

	/**
	 * Each constraint is a CountryInfo describing tx power constraints on
	 * one or more channels, for the current country.
	 */
	public final List<CountryInfo> constraints;

	/** Constructor */
	public Country(List<CountryInfo> countryInfos) {
		this.constraints = Collections.unmodifiableList(countryInfos);
	}

	/** Parse Country IE from the appropriate Json object. */
	public static Country parse(JsonObject contents) {
		List<CountryInfo> constraints = new ArrayList<>();
		JsonElement constraintsObject = contents.get("constraints");
		if (constraintsObject != null) {
			for (JsonElement jsonElement : constraintsObject.getAsJsonArray()) {
				CountryInfo countryInfo =
					CountryInfo.parse(jsonElement.getAsJsonObject());
				constraints.add(countryInfo);
			}
		}
		return new Country(constraints);
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

	@Override
	public String toString() {
		return "Country [constraints=" + constraints + "]";
	}
}
