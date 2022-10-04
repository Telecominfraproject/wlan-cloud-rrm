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
import com.google.gson.JsonElement;

/**
 * This information element (IE) appears in wifiscan entries. It currently does
 * not appear in these entries AFAICT. It's called "Neighbor Report" in
 * 802.11 specs (section 9.4.2.36). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class NeighborReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 52;

	public static class BssidInfo {
		/**
		 * The capability subelement
		 */
		public static class Capabilities {
			public final boolean spectrumManagement;
			public final boolean qos;
			public final boolean apsd;
			public final boolean radioMeasurement;

			/** Constructor */
			public Capabilities(
				boolean spectrumManagement,
				boolean qos,
				boolean apsd,
				boolean radioMeasurement
			) {
				this.spectrumManagement = spectrumManagement;
				this.qos = qos;
				this.apsd = apsd;
				this.radioMeasurement = radioMeasurement;
			}

			/** Parse Capabilities from JSON object */
			// TODO rename fields as necessary - we don't know how the data format yet
			public static Capabilities parse(JsonObject contents) {
				return new Capabilities(
					contents.get("Spectrum Management").getAsBoolean(),
					contents.get("QoS").getAsBoolean(),
					contents.get("APSD").getAsBoolean(),
					contents.get("Radio Management").getAsBoolean()
				);
			}

			@Override
			public int hashCode() {
				return Objects
					.hash(spectrumManagement, qos, apsd, radioMeasurement);
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

				Capabilities other = (Capabilities) obj;
				return spectrumManagement == other.spectrumManagement &&
					qos == other.qos && apsd == other.apsd &&
					radioMeasurement == other.radioMeasurement;
			}

			@Override
			public String toString() {
				return String.format(
					"Capabilities[spectrumManagement=%d, qos=%d, apsd=%d, radioMeasurement=%d]",
					spectrumManagement,
					qos,
					apsd,
					radioMeasurement
				);
			}
		}

		public final short apReachability;
		public final boolean security;
		public final boolean keyScope;
		public final Capabilities capabilities;
		public final boolean mobilityDomain;
		public final boolean highThroughput;
		public final boolean veryHighThroughput;
		public final boolean ftm;

		/** Constructor */
		public BssidInfo(
			byte apReachability,
			boolean security,
			boolean keyScope,
			Capabilities capabilities,
			boolean mobilityDomain,
			boolean highThroughput,
			boolean veryHighThroughput,
			boolean ftm
		) {
			this.apReachability = apReachability;
			this.security = security;
			this.keyScope = keyScope;
			this.capabilities = capabilities;
			this.mobilityDomain = mobilityDomain;
			this.highThroughput = highThroughput;
			this.veryHighThroughput = veryHighThroughput;
			this.ftm = ftm;
		}

		/** Parse BssidInfo from JSON object */
		// TODO rename fields as necessary - we don't know how the data format yet
		public static BssidInfo parse(JsonObject contents) {
			JsonElement capabilitiesJson = contents.get("capabilities");
			if (capabilitiesJson == null) {
				return null;
			}

			Capabilities capabilities =
				Capabilities.parse(capabilitiesJson.getAsJsonObject());
			return new BssidInfo(
				contents.get("AP Reachability").getAsByte(),
				contents.get("Security").getAsBoolean(),
				contents.get("Key Scope").getAsBoolean(),
				capabilities,
				contents.get("Mobility Domain").getAsBoolean(),
				contents.get("High Throughput").getAsBoolean(),
				contents.get("Very High Throughput").getAsBoolean(),
				contents.get("FTM").getAsBoolean()
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				apReachability,
				security,
				keyScope,
				mobilityDomain,
				highThroughput,
				veryHighThroughput,
				ftm
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

			BssidInfo other = (BssidInfo) obj;
			return apReachability == other.apReachability &&
				security == other.security && keyScope == other.keyScope &&
				capabilities == other.capabilities &&
				mobilityDomain == other.mobilityDomain &&
				highThroughput == other.highThroughput &&
				veryHighThroughput == other.veryHighThroughput &&
				ftm == other.ftm;
		}

		@Override
		public String toString() {
			return String.format(
				"BssidInfo[apReachability=%d, security=%d, keyScope=%d, mobilityDomain=%d, highThroughput=%d, veryHighThroughput=%d, ftm=%d]"
			);
		}
	}

	public final String bssid;
	public final BssidInfo bssidInfo;
	public final byte operatingClass;
	public final byte channelNumber;
	public final byte phyType;

	/** Constructor */
	public NeighborReport(
		String bssid,
		BssidInfo bssidInfo,
		byte operatingClass,
		byte channelNumber,
		byte phyType
	) {
		this.bssid = bssid;
		this.bssidInfo = bssidInfo;
		this.operatingClass = operatingClass;
		this.channelNumber = channelNumber;
		this.phyType = phyType;
	}

	/** Parse NeighborReport from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static NeighborReport parse(JsonObject contents) {
		JsonElement bssidInfoJson = contents.get("BSSID Info");
		if (bssidInfoJson == null) {
			return null;
		}

		BssidInfo bssidInfo = BssidInfo.parse(bssidInfoJson.getAsJsonObject());
		return new NeighborReport(
			contents.get("BSSID").getAsString(),
			bssidInfo,
			contents.get("Operating Class").getAsByte(),
			contents.get("Channel Number").getAsByte(),
			contents.get("Phy Type").getAsByte()
		);
	}

	@Override
	public int hashCode() {
		return Objects
			.hash(bssid, bssidInfo, operatingClass, channelNumber, phyType);
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

		NeighborReport other = (NeighborReport) obj;
		return bssid == other.bssid && bssidInfo == other.bssidInfo &&
			operatingClass == other.operatingClass &&
			channelNumber == other.channelNumber && phyType == other.phyType;
	}

	@Override
	public String toString() {
		return String.format(
			"NeighborReport[bssid=%s, operatingClass=%d, channelNumber=%d, phyType=%d]",
			bssid,
			operatingClass,
			channelNumber,
			phyType
		);
	}
}
