/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.informationelement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Collections;

import com.facebook.openwifirrm.ucentral.IEUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It currently does
 * not appear in these entries AFAICT. It's called "Reduced Neighbor Report" in
 * 802.11 specs (section 9.4.2.170). Refer to the specification for more
 * details.
 * Language in javadocs is taken from the specification.
 */
public class ReducedNeighborReport {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 201;

	public static class NeighborApInformation {

		public static class TbttInformationHeader {
			public final byte tbttInformationType;
			public final boolean filteredNeighborAp;
			public final byte tbttInformationCount;
			public final byte tbttInformationLength;

			/** Constructor */
			public TbttInformationHeader(
				byte tbttInformationType,
				boolean filteredNeighborAp,
				byte tbttInformationCount,
				byte tbttInformationLength
			) {
				this.tbttInformationType = tbttInformationType;
				this.filteredNeighborAp = filteredNeighborAp;
				this.tbttInformationCount = tbttInformationCount;
				this.tbttInformationLength = tbttInformationLength;
			}

			/** Parse TbttInformationHeader from JSON object */
			// TODO rename fields as necessary - we don't know how the data format yet
			public static TbttInformationHeader parse(JsonObject contents) {
				return new TbttInformationHeader(
					contents.get("TBTT Information Type").getAsByte(),
					contents.get("Filtered Neighbor Map").getAsBoolean(),
					contents.get("TBTT Information Count").getAsByte(),
					contents.get("TBTT Information Length").getAsByte()
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

			@Override
			public String toString() {
				return String.format(
					"TbttInformationHeader[tbttInformationType=%d, filteredNeighborAp=%b, tbttInformationCount=%d, tbttInformationLength=%d]",
					tbttInformationType,
					filteredNeighborAp,
					tbttInformationCount,
					tbttInformationLength
				);
			}
		}

		public static class TbttInformation {
			public final byte neighborApTbttOffset;
			/** BSSID of neighbor, optional */
			public final String bssid;
			/** Short SSID of neighbor, optional */
			public final String shortSsid;

			/** Constructor */
			public TbttInformation(
				byte neighborApTbttOffset,
				String bssid,
				String shortSsid
			) {
				this.neighborApTbttOffset = neighborApTbttOffset;
				this.bssid = bssid;
				this.shortSsid = shortSsid;
			}

			/** Parse TbttInformation from JSON object */
			// TODO rename fields as necessary - we don't know how the data format yet
			public static TbttInformation parse(JsonObject contents) {
				return new TbttInformation(
					contents.get("Neighbor AP TBTT Offset").getAsByte(),
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

			@Override
			public String toString() {
				return String.format(
					"TbttInformation[neighborApTbttOffset=%d, bssid=%s, shortSsid=%s]",
					neighborApTbttOffset,
					bssid,
					shortSsid
				);
			}
		}

		public final TbttInformationHeader tbttInformationHeader;
		public final byte operatingClass;
		public final byte channelNumber;
		public final TbttInformation tbttInformation;

		/** Constructor */
		public NeighborApInformation(
			TbttInformationHeader tbttInformationHeader,
			byte operatingClass,
			byte channelNumber,
			TbttInformation tbttInformation
		) {
			this.tbttInformationHeader = tbttInformationHeader;
			this.operatingClass = operatingClass;
			this.channelNumber = channelNumber;
			this.tbttInformation = tbttInformation;
		}

		/** Parse NeighborApInformation from JSON object */
		// TODO rename fields as necessary - we don't know how the data format yet
		public static NeighborApInformation parse(JsonObject contents) {
			return new NeighborApInformation(
				TbttInformationHeader.parse(
					contents.get("TBTT Information Header").getAsJsonObject()
				),
				contents.get("Operating Class").getAsByte(),
				contents.get("Channel Number").getAsByte(),
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

		@Override
		public String toString() {
			return String.format(
				"NeighborApInformation[tbttInformationHeader=%s, operatingClass=%d, channelNumber=%d, tbttInformation=%s]",
				tbttInformationHeader,
				operatingClass,
				channelNumber,
				tbttInformation
			);
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
	// TODO rename fields as necessary - we don't know how the data format yet
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

	@Override
	public String toString() {
		return String.format(
			"ReducedNeighborReport[neighborApInformations=%s]",
			neighborApInformations
		);
	}
}
