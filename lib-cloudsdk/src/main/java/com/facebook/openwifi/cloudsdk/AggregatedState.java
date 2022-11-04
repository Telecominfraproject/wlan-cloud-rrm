/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.facebook.openwifi.cloudsdk.models.ap.State.Interface.Counters;
import com.facebook.openwifi.cloudsdk.models.ap.State.Interface.SSID.Association;
import com.google.gson.JsonObject;

/**
 * Aggregation model for State aggregation. Only contains info useful for
 * analysis.
 */
public class AggregatedState {

	/**
	 * Radio information with channel, channel_width and tx_power.
	 */
	public static class RadioConfig {
		public int channel;
		public int channelWidth;
		public int txPower;
		public String phy;

		/** Default constructor with no args */
		private RadioConfig() {}

		/** Constructor with args */
		public RadioConfig(JsonObject radio) {
			this.channel = radio.get("channel").getAsInt();
			this.channelWidth = radio.get("channel_width").getAsInt();
			this.txPower = radio.get("tx_power").getAsInt();
			this.phy = radio.get("phy").getAsString();
		}

		public RadioConfig(int channel, int channelWidth, int txPower) {
			this.channel = channel;
			this.channelWidth = channelWidth;
			this.txPower = txPower;
		}

		@Override
		public int hashCode() {
			return Objects.hash(channel, channelWidth, txPower);
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

			RadioConfig other = (RadioConfig) obj;
			return channel == other.channel &&
				channelWidth == other.channelWidth && txPower == other.txPower;
		}
	}

	/**
	 * Data model to keep raw data from {@link State.Interface.SSID.Association},
	 * {@link State.Radio} and {@link State.Interface.Counters}.
	 */
	public static class AssociationInfo {
		/** Rate information with aggregated fields. */
		public static class Rate {
			/**
			 * Aggregated fields bitRate
			 */
			public long bitRate;

			/**
			 * Aggregated fields chWidth
			 */
			public int chWidth;

			/**
			 * Aggregated fields mcs
			 */
			public int mcs;

			/** Constructor with no args */
			private Rate() {}

			/** Constructor with args */
			private Rate(long bitRate, int chWidth, int mcs) {
				this.bitRate = bitRate;
				this.chWidth = chWidth;
				this.mcs = mcs;
			}
		}

		public long connected;
		public long inactive;
		public int rssi;
		public long rxBytes;
		public long rxPackets;
		public Rate rxRate;
		public long txBytes;
		public long txDuration;
		public long txFailed;
		public long txPackets;
		public Rate txRate;
		public long txRetries;
		public int ackSignal;
		public int ackSignalAvg;
		public long txPacketsCounters;
		public long txErrorsCounters;
		public long txDroppedCounters;
		public long activeMsRadio;
		public long busyMsRadio;
		public long noiseRadio;
		public long receiveMsRadio;
		public long transmitMsRadio;

		/** unix time in ms */
		public long timestamp;

		/** Default Constructor. */
		public AssociationInfo() {}

		/** Constructor with only rssi(for test purpose) */
		public AssociationInfo(int rssi) {
			this.rssi = rssi;
		}

		/** Constructor with args */
		public AssociationInfo(
			Association association,
			Counters counters,
			JsonObject radio,
			long timestamp
		) {
			// Association info
			connected = association.connected;
			inactive = association.inactive;
			rssi = association.rssi;
			rxBytes = association.rx_bytes;
			rxPackets = association.rx_packets;
			if (association.rx_rate != null) {
				rxRate = new Rate(
					association.rx_rate.bitrate,
					association.rx_rate.chwidth,
					association.rx_rate.mcs
				);
			} else {
				rxRate = new Rate();
			}

			txBytes = association.tx_bytes;
			txPackets = association.tx_packets;

			if (association.tx_rate != null) {
				txRate = new Rate(
					association.tx_rate.bitrate,
					association.tx_rate.chwidth,
					association.tx_rate.mcs
				);
			} else {
				txRate = new Rate();
			}
			txRetries = association.tx_retries;
			ackSignal = association.ack_signal;
			ackSignalAvg = association.ack_signal_avg;

			//Counters info
			txPacketsCounters = counters.tx_packets;
			txErrorsCounters = counters.tx_errors;
			txDroppedCounters = counters.tx_dropped;

			// Radio info
			activeMsRadio = radio.get("active_ms").getAsLong();
			busyMsRadio = radio.get("busy_ms").getAsLong();
			transmitMsRadio = radio.get("transmit_ms").getAsLong();
			receiveMsRadio = radio.get("receive_ms").getAsLong();
			noiseRadio = radio.get("noise").getAsLong();

			this.timestamp = timestamp;
		}
	}

	// Aggregate AssociationInfo over bssid, station and RadioConfig.
	public String bssid;
	public String station;
	public RadioConfig radioConfig;

	// Store a list of AssociationInfo of the same link and radio configuration. */
	public List<AssociationInfo> associationInfoList;

	/** Constructor with no args. For test purpose. */
	public AggregatedState() {
		this.associationInfoList = new ArrayList<>();
		this.radioConfig = new RadioConfig();
	}

	/** Construct from Association, Counters, Radio and time stamp */
	public AggregatedState(
		Association association,
		Counters counters,
		JsonObject radio,
		long timestamp
	) {
		this.bssid = association.bssid;
		this.station = association.station;
		this.associationInfoList = new ArrayList<>();
		associationInfoList
			.add(new AssociationInfo(association, counters, radio, timestamp));
		this.radioConfig = new RadioConfig(radio);
	}

	/**
	 * Check whether the passed-in AggregatedState and this one match for aggregation.
	 * If the two match in bssid, station and radio. Then they could be aggregated.
	 *
	 * @param state the reference AggregatedState with which to check with.
	 * @return boolean return true if the two matches for aggregation.
	 */
	public boolean matchesForAggregation(AggregatedState state) {
		return bssid == state.bssid && station == state.station &&
			Objects.equals(radioConfig, state.radioConfig);
	}

	/**
	 * Add an AggregatedState to this AggregatedState. Succeed only when the two
	 * match for aggregation.
	 *
	 * @param state input AggregatedState
	 * @return boolean true if the two match in bssid, station, channel,
	 *         channel_width and tx_power
	 */
	public boolean add(AggregatedState state) {
		if (matchesForAggregation(state)) {
			associationInfoList.addAll(state.associationInfoList);
			return true;
		}
		return false;
	}
}
