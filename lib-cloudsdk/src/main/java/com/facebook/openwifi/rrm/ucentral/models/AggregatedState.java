/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.facebook.openwifi.rrm.ucentral.models.State.Interface.SSID.Association;
import com.facebook.openwifi.rrm.ucentral.models.State.Interface.SSID.Association.Rate;

/**
 * Aggregation model for State aggregation. Only contains info useful for
 * analysis.
 */
public class AggregatedState {

	/** Rate information with aggregated fields. */
	public static class AggregatedRate {
		/**
		 * This is the common bitRate for all the aggregated fields.
		 */
		public long bitRate;

		/**
		 * This is the common channel width for all the aggregated fields.
		 */
		public int chWidth;

		/**
		 * Aggregated fields mcs
		 */
		public List<Integer> mcs = new ArrayList<>();

		/** Constructor with no args */
		private AggregatedRate() {}

		/** Add a Rate to the AggregatedRate */
		private void add(Rate rate) {
			if (rate == null) {
				return;
			}
			if (mcs.isEmpty()) {
				bitRate = rate.bitrate;
				chWidth = rate.chwidth;
			}
			mcs.add(rate.mcs);
		}

		/**
		 * Add an AggregatedRate with the same channel_width to the
		 * AggregatedRate
		 */
		private void add(AggregatedRate rate) {
			if (rate == null || rate.chWidth != chWidth) {
				return;
			}
			if (mcs.isEmpty()) {
				bitRate = rate.bitRate;
				chWidth = rate.chWidth;
			}
			mcs.addAll(rate.mcs);
		}
	}

	/**
	 * Radio information with channel, channel_width and tx_power.
	 */
	public static class Radio {
		public int channel;
		public int channelWidth;
		public int txPower;

		private Radio() {}

		public Radio(int channel, int channelWidth, int txPower) {
			this.channel = channel;
			this.channelWidth = channelWidth;
			this.txPower = txPower;
		}

		private Radio(Map<String, Integer> radioInfo) {
			channel = radioInfo.getOrDefault("channel", -1);
			channelWidth = radioInfo.getOrDefault("channel_width", -1);
			txPower = radioInfo.getOrDefault("tx_power", -1);
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

			Radio other = (Radio) obj;
			return channel == other.channel &&
				channelWidth == other.channelWidth && txPower == other.txPower;
		}
	}

	public String bssid;
	public String station;
	public long connected;
	public long inactive;
	public List<Integer> rssi;
	public long rxBytes;
	public long rxPackets;
	public AggregatedRate rxRate;
	public long txBytes;
	public long txDuration;
	public long txFailed;
	public long txPackets;
	public AggregatedRate txRate;
	public long txRetries;
	public int ackSignal;
	public int ackSignalAvg;
	public Radio radio;

	/** Constructor with no args */
	public AggregatedState() {
		this.rxRate = new AggregatedRate();
		this.txRate = new AggregatedRate();
		this.rssi = new ArrayList<>();
		this.radio = new Radio();
	}

	/** Construct from Association and radio */
	public AggregatedState(
		Association association,
		Map<String, Integer> radioInfo
	) {
		this.rxRate = new AggregatedRate();
		this.txRate = new AggregatedRate();
		this.rssi = new ArrayList<>();

		this.bssid = association.bssid;
		this.station = association.station;
		this.connected = association.connected;
		this.inactive = association.inactive;
		this.rssi.add(association.rssi);
		this.rxBytes = association.rx_bytes;
		this.rxPackets = association.rx_packets;
		this.rxRate.add(association.rx_rate);
		this.txBytes = association.tx_bytes;
		this.txDuration = association.tx_duration;
		this.txFailed = association.tx_failed;
		this.txPackets = association.tx_packets;
		this.txRate.add(association.tx_rate);
		this.txRetries = association.tx_retries;
		this.ackSignal = association.ack_signal;
		this.ackSignalAvg = association.ack_signal_avg;
		this.radio = new Radio(radioInfo);
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
			Objects.equals(radio, state.radio);
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
			this.rssi.addAll(state.rssi);
			this.rxRate.add(state.rxRate);
			this.txRate.add(state.txRate);
			return true;
		}
		return false;
	}
}
