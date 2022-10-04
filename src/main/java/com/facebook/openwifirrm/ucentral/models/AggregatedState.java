/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID.Association;
import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID.Association.Rate;

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
		public AggregatedRate() {}

		/** Add a Rate to the AggregatedRate */
		public void add(Rate rate) {
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
		public void add(AggregatedRate rate) {
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

		public Radio() {}

		public Radio(int channel, int channelWidth, int txPower) {
			this.channel = channel;
			this.channelWidth = channelWidth;
			this.txPower = txPower;
		}

		public Radio(Map<String, Integer> radioInfo) {
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

	@Override
	public int hashCode() {
		return Objects.hash(bssid, station, radio);
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

		AggregatedState other = (AggregatedState) obj;

		return bssid == other.bssid && station == other.station &&
			connected == other.connected && inactive == other.inactive && rssi
				.equals(other.rssi) &&
			rxBytes == other.rxBytes && rxBytes == other.rxPackets &&
			Objects.equals(rxRate, other.rxRate) &&
			txBytes == other.txBytes && txDuration == other.txDuration &&
			txFailed == other.txFailed && txPackets == other.txPackets &&
			Objects.equals(txRate, other.txRate) &&
			txRetries == other.txRetries && ackSignal == other.ackSignal &&
			ackSignalAvg == other.ackSignalAvg &&
			Objects.equals(radio, other.radio);
	}

	/**
	 * Add an AggregatedState to this AggregatedState. Succeed only when the two
	 * matches in hashCode.
	 *
	 * @param state input AggregatedState
	 * @return boolean true if the two matches in bssid, station, channel,
	 *         channel_width and tx_power
	 */
	public boolean add(AggregatedState state) {
		if (hashCode() == state.hashCode()) {
			this.bssid = state.bssid;
			this.station = state.station;
			this.connected = state.connected;
			this.inactive = state.inactive;
			this.rssi.addAll(state.rssi);
			this.rxBytes = state.rxBytes;
			this.rxPackets = state.rxPackets;
			this.rxRate.add(state.rxRate);
			this.txBytes = state.txBytes;
			this.txDuration = state.txDuration;
			this.txFailed = state.txFailed;
			this.txPackets = state.txPackets;
			this.txRate.add(state.txRate);
			this.txRetries = state.txRetries;
			this.ackSignal = state.ackSignal;
			this.ackSignalAvg = state.ackSignalAvg;
			this.radio = new Radio(
				state.radio.channel,
				state.radio.channelWidth,
				state.radio.txPower
			);
			return true;
		}
		return false;
	}
}
