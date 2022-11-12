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

// NOTE: Not validated (not seen on test devices)
/**
 * This information element (IE) appears in wifiscan entries. It's called
 * "Neighbor Report" in 802.11 specs (section 9.4.2.36). Refer to the
 * specification for more details. Language in javadocs is taken from the
 * specification.
 */
public class NeighborReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 52;

	/**
	 * The BSSID Information field can be used to help determine neighbor service
	 * set transition candidates
	 */
	public static class BssidInfo {
		/**
		 * The capability subelement containing selected capability information for
		 * the AP indicated by this BSSID.
		 */
		public static class Capabilities {
			/** dot11SpectrumManagementRequired */
			public final boolean spectrumManagement;
			/** dot11QosOptionImplemented */
			public final boolean qos;
			/** dot11APSDOptionImplemented */
			public final boolean apsd;
			/** dot11RadioMeasurementActivated */
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
			// TODO modify this method as necessary - since the IE doesn't seem to be
			// present, we have no idea what the format looks like
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
		}

		/**
		 * 2 unsigned bits - whether the AP identified by this BSSID is reachable by
		 * the STA that requested the neighbor report
		 */
		public final byte apReachability;
		/**
		 * If true, indicates that the AP identified by this BSSID supports the same
		 * security provisioning as used by the STA in its current association. If the
		 * bit is false, it indicates either that the AP does not support the same
		 * security provisioning or that the security information is not available at
		 * this time.
		 */
		public final boolean security;
		/**
		 * Indicates the AP indicated by this BSSID has the same authenticator as
		 * the AP sending the report. If this bit is false, it indicates a distinct
		 * authenticator or the information is not available.
		 */
		public final boolean keyScope;
		/**
		 * @see Capabilities
		 */
		public final Capabilities capabilities;
		/**
		 * Set to true to indicate that the AP represented by this BSSID is including
		 * an MDE in its Beacon frames and that the contents of that MDE are identical
		 * to the MDE advertised by the AP sending the report
		 */
		public final boolean mobilityDomain;
		/**
		 * High throughput or not, if true the contents of the HT Capabilities in the
		 * Beacon frame should be identical to the HT Capabilities advertised by the
		 * AP sending the report
		 */
		public final boolean highThroughput;
		/**
		 * Very High throughput or not, if true the contents of the VHT Capabilities
		 * in the Beacon frame should be identical to the VHT Capabilities advertised
		 * by the AP sending the report
		 */
		public final boolean veryHighThroughput;
		/**
		 * Indicate that the AP represented by this BSSID is an AP that has set the Fine
		 * Timing Measurement Responder field of the Extended Capabilities element
		 */
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
	}

	/** BSSID */
	public final String bssid;
	/**
	 * @see BssidInfo
	 */
	public final BssidInfo bssidInfo;
	/**
	 * Unsigned 8 bits - indicates the channel set of the AP indicated by this BSSID
	 */
	public final short operatingClass;
	/**
	 * Unsigned 8 bits - channel number
	 */
	public final short channelNumber;
	/**
	 * Unsigned 8 bits - PHY type
	 */
	public final short phyType;
	// TODO do we want to support the subelements?
	/**
	 * Optional subelements
	 */
	public final List<JsonObject> subelements;

	/** Constructor */
	public NeighborReport(
		String bssid,
		BssidInfo bssidInfo,
		short operatingClass,
		short channelNumber,
		short phyType,
		List<JsonObject> subelements
	) {
		this.bssid = bssid;
		this.bssidInfo = bssidInfo;
		this.operatingClass = operatingClass;
		this.channelNumber = channelNumber;
		this.phyType = phyType;
		this.subelements = Collections.unmodifiableList(subelements);
	}

	/** Parse NeighborReport from JSON object */
	// TODO modify this method as necessary - since the IE doesn't seem to be
	// present, we have no idea what the format looks like
	public static NeighborReport parse(JsonObject contents) {
		List<JsonObject> subelements = null;
		JsonElement subelementsObj = contents.get("Subelements");
		if (subelementsObj != null) {
			subelements = new ArrayList<JsonObject>();
			for (JsonElement elem : subelementsObj.getAsJsonArray()) {
				subelements.add(elem.getAsJsonObject());
			}
		}

		return new NeighborReport(
			contents.get("BSSID").getAsString(),
			BssidInfo.parse(contents.get("BSSID Info").getAsJsonObject()),
			contents.get("Operating Class").getAsShort(),
			contents.get("Channel Number").getAsShort(),
			contents.get("Phy Type").getAsShort(),
			subelements
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
}
