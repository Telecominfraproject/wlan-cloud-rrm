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

import com.facebook.openwifi.cloudsdk.IEUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

// NOTE: Not validated (not seen on test devices)
/**
 * This information element (IE) appears in wifiscan entries. It's called
 * "Reduced Neighbor Report" in 802.11 specs (section 9.4.2.170). Refer to the
 * specification for more details. Language in javadocs is taken from the
 * specification.
 */
public class ReducedNeighborReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 201;

	/**
	 * The Neighbor AP Information field specifies TBTT and other information
	 * related to a group of neighbor APs on one channel.
	 */
	public static class NeighborApInformation {
		/**
		 * Subfield for TBTT Information header
		 */
		public static class TbttInformationHeader {
			/**
			 * Unsigned 2 bits -  identifies, together with the TBTT Information Length
			 * subfield, the format of the TBTT Information field
			 */
			public final byte tbttInformationType;
			/**
			 * 1 bit - reserved except when the Reduced Neighbor Report element is
			 * carried in a Probe Response frame transmitted by a TVHT AP
			 */
			public final boolean filteredNeighborAp;
			/**
			 * Unsigned 4 bits - number of TBTT Information fields included in the TBTT
			 * Information Set field of the Neighbor AP Information field, minus one
			 */
			public final byte tbttInformationCount;
			/**
			 * Unsigned 8 bits - the length of each TBTT Information field included in
			 * the TBTT Information Set field of the Neighbor AP Information field
			 */
			public final short tbttInformationLength;

			/** Constructor */
			public TbttInformationHeader(
				byte tbttInformationType,
				boolean filteredNeighborAp,
				byte tbttInformationCount,
				short tbttInformationLength
			) {
				this.tbttInformationType = tbttInformationType;
				this.filteredNeighborAp = filteredNeighborAp;
				this.tbttInformationCount = tbttInformationCount;
				this.tbttInformationLength = tbttInformationLength;
			}

			/** Parse TbttInformationHeader from JSON object */
			// TODO modify this method as necessary - since the IE doesn't seem to be
			// present, we have no idea what the format looks like
			public static TbttInformationHeader parse(JsonObject contents) {
				return new TbttInformationHeader(
					contents.get("TBTT Information Type").getAsByte(),
					contents.get("Filtered Neighbor Map").getAsBoolean(),
					contents.get("TBTT Information Count").getAsByte(),
					contents.get("TBTT Information Length").getAsShort()
				);
			}

			@Override
			public int hashCode() {
				return Objects.hash(
					tbttInformationType,
					filteredNeighborAp,
					tbttInformationCount,
					tbttInformationLength
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

				TbttInformationHeader other = (TbttInformationHeader) obj;
				return tbttInformationType == other.tbttInformationType &&
					filteredNeighborAp == other.filteredNeighborAp &&
					tbttInformationCount == other.tbttInformationCount &&
					tbttInformationLength == other.tbttInformationLength;
			}
		}

		/**
		 * Subfield for TBTT Information
		 */
		public static class TbttInformation {
			/**
			 * Unsigned 8 bits - offset in TUs, rounded down to nearest TU, to the next
			 * TBTT of an AP’s BSS from the immediately prior TBTT of the AP that
			 * transmits this element
			 */
			public final short neighborApTbttOffset;
			/** BSSID of neighbor, optional */
			public final String bssid;
			/** Short SSID of neighbor, optional */
			public final String shortSsid;

			/** Constructor */
			public TbttInformation(
				short neighborApTbttOffset,
				String bssid,
				String shortSsid
			) {
				this.neighborApTbttOffset = neighborApTbttOffset;
				this.bssid = bssid;
				this.shortSsid = shortSsid;
			}

			/** Parse TbttInformation from JSON object */
			// TODO modify this method as necessary - since the IE doesn't seem to be
			// present, we have no idea what the format looks like
			public static TbttInformation parse(JsonObject contents) {
				return new TbttInformation(
					contents.get("Neighbor AP TBTT Offset").getAsShort(),
					IEUtils.parseOptionalStringField(contents, "BSSID"),
					IEUtils.parseOptionalStringField(contents, "Short SSID")
				);
			}

			@Override
			public int hashCode() {
				return Objects.hash(neighborApTbttOffset, bssid, shortSsid);
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

				TbttInformation other = (TbttInformation) obj;
				return neighborApTbttOffset ==
					other.neighborApTbttOffset && bssid.equals(other.bssid) &&
					Objects.equals(shortSsid, other.shortSsid);
			}
		}

		/**
		 * @see TbttInformationHeader
		 */
		public final TbttInformationHeader tbttInformationHeader;
		/**
		 * Unsigned 8 bits - channel starting frequency that, together with the
		 * Channel Number field, indicates the primary channel of the BSSs of the APs
		 * in this Neighbor AP Information field
		 */
		public final short operatingClass;
		/**
		 * Unsigned 8 bits - the last known primary channel of the APs in this
		 * Neighbor AP Information field.
		 */
		public final short channelNumber;
		/**
		 * @see TbttInformation
		 */
		public final TbttInformation tbttInformation;

		/** Constructor */
		public NeighborApInformation(
			TbttInformationHeader tbttInformationHeader,
			short operatingClass,
			short channelNumber,
			TbttInformation tbttInformation
		) {
			this.tbttInformationHeader = tbttInformationHeader;
			this.operatingClass = operatingClass;
			this.channelNumber = channelNumber;
			this.tbttInformation = tbttInformation;
		}

		/** Parse NeighborApInformation from JSON object */
		// TODO modify this method as necessary - since the IE doesn't seem to be
		// present, we have no idea what the format looks like
		public static NeighborApInformation parse(JsonObject contents) {
			return new NeighborApInformation(
				TbttInformationHeader.parse(
					contents.get("TBTT Information Header").getAsJsonObject()
				),
				contents.get("Operating Class").getAsShort(),
				contents.get("Channel Number").getAsShort(),
				TbttInformation.parse(contents.get("TBTT Information").getAsJsonObject())
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				tbttInformationHeader,
				operatingClass,
				channelNumber,
				tbttInformation
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

			NeighborApInformation other = (NeighborApInformation) obj;
			return tbttInformationHeader.equals(other.tbttInformationHeader) &&
				operatingClass == other.operatingClass &&
				channelNumber == other.channelNumber &&
				Objects.equals(tbttInformation, other.tbttInformation);
		}
	}

	/** number of channels in a subband of supported channels */
	public final List<NeighborApInformation> neighborApInformations;

	/** Constructor */
	public ReducedNeighborReport(
		List<NeighborApInformation> neighborApInformations
	) {
		this.neighborApInformations =
			Collections.unmodifiableList(neighborApInformations);
	}

	/** Parse ReducedNeighborReport from JSON object */
	// TODO modify this method as necessary - since the IE doesn't seem to be
	// present, we have no idea what the format looks like
	public static ReducedNeighborReport parse(JsonObject contents) {
		List<NeighborApInformation> neighborApInformations = new ArrayList<>();

		JsonElement neighborApInformationsObject =
			contents.get("Neighbor AP Informations");
		if (neighborApInformationsObject != null) {
			for (
				JsonElement elem : neighborApInformationsObject.getAsJsonArray()
			) {
				neighborApInformations
					.add(NeighborApInformation.parse(elem.getAsJsonObject()));
			}
		}

		return new ReducedNeighborReport(neighborApInformations);
	}

	@Override
	public int hashCode() {
		return Objects.hash(neighborApInformations);
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

		ReducedNeighborReport other = (ReducedNeighborReport) obj;
		return neighborApInformations.equals(other.neighborApInformations);
	}
}
