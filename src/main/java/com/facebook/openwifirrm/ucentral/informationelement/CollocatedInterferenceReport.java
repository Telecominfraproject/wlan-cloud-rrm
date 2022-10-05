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
 * not appear in these entries AFAICT. It's called "Collocated Interference
 * Report" in 802.11 specs (section 9.4.2.84). Refer to the specification for
 * more details. Language in javadocs is taken from the specification.
 */
public class CollocatedInterferenceReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 96;

	public static class InterferenceAccuracyAndIndex {
		public final short expectedAccuracy;
		public final short interferenceIndex;

		/** Constructor */
		public InterferenceAccuracyAndIndex(
			short expectedAccuracy,
			short interferenceIndex
		) {
			this.expectedAccuracy = expectedAccuracy;
			this.interferenceIndex = interferenceIndex;
		}

		/** Parse InterferenceAccuracyAndIndex from JSON object */
		// TODO rename fields as necessary - we don't know how the data format yet
		public static InterferenceAccuracyAndIndex parse(JsonObject contents) {
			return new InterferenceAccuracyAndIndex(
				contents.get("Expected Accuracy").getAsShort(),
				contents.get("Interference Index").getAsShort()
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

		@Override
		public String toString() {
			return String.format(
				"InterferenceAccuracyAndIndex[expectedAccuracy=%d, interferenceIndex=%d]"
			);
		}
	}

	public final byte reportPeriod;
	public final byte interferenceLevel;
	public final InterferenceAccuracyAndIndex interferenceAccuracyAndIndex;
	public final int interferenceInterval;
	public final int interferenceBurstLength;
	public final int interferenceStartTimeDutyCycle;
	public final int interferenceCenterFrequency;
	public final short interferenceBandwidth;

	/** Constructor */
	public CollocatedInterferenceReport(
		byte reportPeriod,
		byte interferenceLevel,
		InterferenceAccuracyAndIndex interferenceAccuracyAndIndex,
		int interferenceInterval,
		int interferenceBurstLength,
		int interferenceStartTimeDutyCycle,
		int interferenceCenterFrequency,
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
			contents.get("Report Period").getAsByte(),
			contents.get("Intereference Level").getAsByte(),
			InterferenceAccuracyAndIndex
				.parse(
					contents.get("Interference Level Accuracy/Inteference Index").getAsJsonObject()
				),
			contents.get("Interference Interval").getAsInt(),
			contents.get("Interference Burst Length").getAsInt(),
			contents.get("Interference Start Time/Duty Cycle").getAsInt(),
			contents.get("Interference Center Frequency").getAsInt(),
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

	@Override
	public String toString() {
		return String.format(
			"CollocatedInterferenceReport[reportPeriod=%d, interferenceLevel=%d, interferenceAccuracyAndIndex=%s, interferenceInterval=%d, interferenceBurstLength=%d, interferenceStartTimeDutyCycle=%d, interferenceCenterFrequency=%d, interferenceBandwidth=%d]",
			reportPeriod,
			interferenceLevel,
			interferenceAccuracyAndIndex.toString(),
			interferenceInterval,
			interferenceBurstLength,
			interferenceStartTimeDutyCycle,
			interferenceCenterFrequency,
			interferenceBandwidth
		);
	}
}
