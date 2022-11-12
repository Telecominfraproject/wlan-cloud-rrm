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

// NOTE: From what I can see it currently does not appear in the list of IEs,
// although it's possible it'll be there in the future.
/**
 * This information element (IE) appears in wifiscan entries. It's called
 * "Collocated Interference Report" in 802.11 specs (section 9.4.2.84). Refer to
 * the specification for more details. Language in javadocs is taken from the
 * specification.
 */
public class CollocatedInterferenceReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 96;

	public static class InterferenceAccuracyAndIndex {
		/**
		 * Unsigned int (4 bits) representing expected accuracy of the estimate of
		 * interference in dB with 95% confidence interval
		 */
		public final byte expectedAccuracy;
		/**
		 * Unsigned int (4 bits) indicating the interference index that is unique for
		 * each type of interference source
		 */
		public final byte interferenceIndex;

		/** Constructor */
		public InterferenceAccuracyAndIndex(
			byte expectedAccuracy,
			byte interferenceIndex
		) {
			this.expectedAccuracy = expectedAccuracy;
			this.interferenceIndex = interferenceIndex;
		}

		/** Parse InterferenceAccuracyAndIndex from JSON object */
		// TODO modify this method as necessary - since the IE doesn't seem to be
		// present, we have no idea what the format looks like
		public static InterferenceAccuracyAndIndex parse(JsonObject contents) {
			return new InterferenceAccuracyAndIndex(
				contents.get("Expected Accuracy").getAsByte(),
				contents.get("Interference Index").getAsByte()
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(expectedAccuracy, interferenceIndex);
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

			InterferenceAccuracyAndIndex other =
				(InterferenceAccuracyAndIndex) obj;
			return expectedAccuracy == other.expectedAccuracy &&
				interferenceIndex == other.interferenceIndex;
		}
	}

	/** Unsigned 8 bits representing when the report is generated */
	public final short reportPeriod;
	/**
	 * signed 8 bits representing the maximum level of the collocated
	 * interference power in units of dBm over all receive chains averaged over a
	 * 4 microsecond period during an interference period and across interference
	 * bandwidth
	 */
	public final byte interferenceLevel;
	/** Subfield for interference level accuracy and index - 8 bits */
	public final InterferenceAccuracyAndIndex interferenceAccuracyAndIndex;
	/**
	 * Unsigned 32 bits representing the interval between two successibe periods
	 * of interference in microseconds
	 */
	public final long interferenceInterval;
	/**
	 * Unsigned 32 bits representing the duration of each period of interference in
	 * microseconds
	 */
	public final long interferenceBurstLength;
	/**
	 * Unsigned 32 bits contains the least significant 4 octets (i.e., B0–B31) of
	 * the TSF timer at the start of the interference burst. When either the
	 * Interference Interval or the Interference Burst Length fields are set to
	 * 2^32 – 1, this field indicates the average duty cycle
	 */
	public final long interferenceStartTimeDutyCycle;
	/**
	 * Unsigned 32 bits representing indicates the center frequency of interference
	 * in units of 5 kHz
	 */
	public final long interferenceCenterFrequency;
	/**
	 * Unsigned 16 bits representing the bandwidth in units of 5 kHz at the –3 dB
	 * roll-off point of the interference signal
	 */
	public final short interferenceBandwidth;

	/** Constructor */
	public CollocatedInterferenceReport(
		short reportPeriod,
		byte interferenceLevel,
		InterferenceAccuracyAndIndex interferenceAccuracyAndIndex,
		long interferenceInterval,
		long interferenceBurstLength,
		long interferenceStartTimeDutyCycle,
		long interferenceCenterFrequency,
		short interferenceBandwidth
	) {
		this.reportPeriod = reportPeriod;
		this.interferenceLevel = interferenceLevel;
		this.interferenceAccuracyAndIndex = interferenceAccuracyAndIndex;
		this.interferenceInterval = interferenceInterval;
		this.interferenceBurstLength = interferenceBurstLength;
		this.interferenceStartTimeDutyCycle = interferenceStartTimeDutyCycle;
		this.interferenceCenterFrequency = interferenceCenterFrequency;
		this.interferenceBandwidth = interferenceBandwidth;
	}

	/** Parse CollocatedInterferenceReport from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static CollocatedInterferenceReport parse(JsonObject contents) {
		return new CollocatedInterferenceReport(
			contents.get("Report Period").getAsShort(),
			contents.get("Intereference Level").getAsByte(),
			InterferenceAccuracyAndIndex
				.parse(
					contents.get("Interference Level Accuracy/Inteference Index").getAsJsonObject()
				),
			contents.get("Interference Interval").getAsLong(),
			contents.get("Interference Burst Length").getAsLong(),
			contents.get("Interference Start Time/Duty Cycle").getAsLong(),
			contents.get("Interference Center Frequency").getAsLong(),
			contents.get("Interference Bandwidth").getAsShort()
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			reportPeriod,
			interferenceLevel,
			interferenceAccuracyAndIndex,
			interferenceInterval,
			interferenceBurstLength,
			interferenceStartTimeDutyCycle,
			interferenceCenterFrequency,
			interferenceBandwidth
		);
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

		CollocatedInterferenceReport other = (CollocatedInterferenceReport) obj;
		return reportPeriod == other.reportPeriod &&
			interferenceLevel == other.interferenceLevel &&
			interferenceAccuracyAndIndex
				.equals(other.interferenceAccuracyAndIndex) &&
			interferenceInterval == other.interferenceInterval &&
			interferenceBurstLength == other.interferenceBurstLength &&
			interferenceStartTimeDutyCycle ==
				other.interferenceStartTimeDutyCycle &&
			interferenceCenterFrequency == other.interferenceCenterFrequency &&
			interferenceBandwidth == other.interferenceBandwidth;
	}
}
