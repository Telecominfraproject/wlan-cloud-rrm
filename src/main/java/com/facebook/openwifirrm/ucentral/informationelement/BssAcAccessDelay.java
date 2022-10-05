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
 * not appear in these entries AFAICT. It's called "BSS AC Access Delay" in
 * 802.11 specs (section 9.4.2.43). Refer to the specification for more details.
 * Language in javadocs is taken from the specification.
 */
public class BssAcAccessDelay {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 68;

	/**
	 * Subfield that goes into Access Category Access Delay field in BSS AC
	 * Access Delay. For information on what the values mean, check section
	 * 9.4.2.43
	 */
	public static class AccessCategoryAccessDelay {
		/**
		 * Unsigned int that represents a scaled representation of best effort AC
		 * access delay
		 */
		public final short averageAccessDelayForBestEffort;
		/**
		 * Unsigned int that represents a scaled representation of background AC
		 * access delay
		 */
		public final short averageAccessDelayForBackground;
		/**
		 * Unsigned int that represents a scaled representation of video AC access
		 * delay
		 */
		public final short averageAccessDelayForVideo;
		/**
		 * Unsigned int that represents a scaled representation of voice AC access
		 * delay
		 */
		public final short averageAccessDelayForVoice;

		/** Constructor */
		public AccessCategoryAccessDelay(
			short averageAccessDelayForBestEffort,
			short averageAccessDelayForBackground,
			short averageAccessDelayForVideo,
			short averageAccessDelayForVoice
		) {
			this.averageAccessDelayForBestEffort =
				averageAccessDelayForBestEffort;
			this.averageAccessDelayForBackground =
				averageAccessDelayForBackground;
			this.averageAccessDelayForVideo = averageAccessDelayForVideo;
			this.averageAccessDelayForVoice = averageAccessDelayForVoice;
		}

		/** Parse AccessCategoryAccessDelay from JSON object */
		// TODO rename fields as necessary - we don't know how the data format yet
		public static AccessCategoryAccessDelay parse(JsonObject contents) {
			return new AccessCategoryAccessDelay(
				contents.get("Average Access Delay For Best Effort").getAsShort(),
				contents.get("Average Access Delay For Background").getAsShort(),
				contents.get("Average Access Delay For Video").getAsShort(),
				contents.get("Average Access Delay For Voice").getAsShort()
			);
		}

		@Override
		public int hashCode() {
			return Objects.hash(
				averageAccessDelayForBestEffort,
				averageAccessDelayForBestEffort,
				averageAccessDelayForVideo,
				averageAccessDelayForVoice
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

			AccessCategoryAccessDelay other = (AccessCategoryAccessDelay) obj;
			return averageAccessDelayForBestEffort ==
				other.averageAccessDelayForBestEffort &&
				averageAccessDelayForBackground ==
					other.averageAccessDelayForBackground &&
				averageAccessDelayForVideo ==
					other.averageAccessDelayForVideo &&
				averageAccessDelayForVoice == other.averageAccessDelayForVoice;
		}

		@Override
		public String toString() {
			return String.format(
				"AccessCategoryAccessDelay[averageAccessDelayForBestEffort=%d, averageAccessDelayForBackground=%d, averageAccessDelayForVideo=%d, averageAccessDelayForVoice=%d]",
				averageAccessDelayForBestEffort,
				averageAccessDelayForBackground,
				averageAccessDelayForVideo,
				averageAccessDelayForVoice
			);
		}
	}

	/** Holds AccessCategoryAccessDelay subfield */
	public final AccessCategoryAccessDelay accessCategoryAccessDelay;

	/** Constructor */
	public BssAcAccessDelay(
		AccessCategoryAccessDelay accessCategoryAccessDelay
	) {
		this.accessCategoryAccessDelay = accessCategoryAccessDelay;
	}

	/** Parse BssAcAccessDelay from JSON object */
	// TODO rename fields as necessary - we don't know how the data format yet
	public static BssAcAccessDelay parse(JsonObject contents) {
		return new BssAcAccessDelay(
			AccessCategoryAccessDelay.parse(
				contents.get("AP Average Access Delay").getAsJsonObject()
			)
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(accessCategoryAccessDelay);
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

		BssAcAccessDelay other = (BssAcAccessDelay) obj;
		return accessCategoryAccessDelay == other.accessCategoryAccessDelay;
	}

	@Override
	public String toString() {
		return String.format(
			"BssAcAccessDelay[accessCategoryAccessDelay=%s]",
			accessCategoryAccessDelay
		);
	}
}
