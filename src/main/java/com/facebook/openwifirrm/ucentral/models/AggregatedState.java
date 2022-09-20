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
import java.util.Objects;

import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID.Association;
import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID.Association.Rate;

public class AggregatedState {

	public class AggregatedRate {
		public long bitrate;
		public int chwidth;
		public List<Integer> mcs;

		/** Constructor with no args */
		public AggregatedRate() {
			this.mcs = new ArrayList<>();
		}

		/** Constructor */
		public AggregatedRate(List<Rate> rates) {
			int size = rates.size();
			if (size == 0) {
				return;
			}
			add(rates);
		}

		/** Add a list of Rates to the AggregatedRate */
		public void add(List<Rate> rates) {
			if (rates.isEmpty()) {
				return;
			}
			for (Rate rate : rates) {
				this.mcs.add(rate.mcs);
			}
		}

		/** Add a Rate to the AggregatedRate */
		public void add(Rate rate) {
			if (rate == null) {
				return;
			}
			if (mcs == null || mcs.isEmpty()) {
				this.bitrate = rate.bitrate;
				this.chwidth = rate.chwidth;
			}
			this.mcs.add(rate.mcs);
		}

		/** Add an AggregatedRate with the same channel_width to the AggregatedRate */
		public void add(AggregatedRate rate) {
			if (rate.chwidth != chwidth) {
				return;
			}
			if (mcs == null || mcs.isEmpty()) {
				this.bitrate = rate.bitrate;
				this.chwidth = rate.chwidth;
			}
			this.mcs.addAll(rate.mcs);
		}
	}

	public String bssid;
	public String station;
	public long connected;
	public long inactive;
	public List<Integer> rssi;
	public long rx_bytes;
	public long rx_packets;
	public AggregatedRate rx_rate;
	public long tx_bytes;
	public long tx_duration;
	public long tx_failed;
	public long tx_packets;
	public AggregatedRate tx_rate;
	public long tx_retries;
	public int ack_signal;
	public int ack_signal_avg;
	public Radio radio;

	public class Radio {
		public int channel;
		public int channel_width;
		public int tx_power;

		public Radio() {}

		public Radio(int channel, int channel_width, int tx_power) {
			this.channel = channel;
			this.channel_width = channel_width;
			this.tx_power = tx_power;
		}

		public Radio(int[] radios) {
			this.channel = radios[0];
			this.channel_width = radios[1];
			this.tx_power = radios[2];
		}

		@Override
		public int hashCode() {
			return Objects.hash(channel, channel_width, tx_power);
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
				channel_width == other.channel_width &&
				tx_power == other.tx_power;
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(bssid, station, radio);
	}

	/** Constructor with no args */
	public AggregatedState() {
		this.rx_rate = new AggregatedRate();
		this.tx_rate = new AggregatedRate();
		this.rssi = new ArrayList<>();
		this.radio = new Radio();
	}

	/** Construct from Aggregatedstate */
	public AggregatedState(AggregatedState state) {
		this.rx_rate = new AggregatedRate();
		this.tx_rate = new AggregatedRate();
		this.rssi = new ArrayList<>();
		add(state);
	}

	/** Construt from Asscociation and radio */
	public AggregatedState(Association association, int[] radios) {
		this.rx_rate = new AggregatedRate();
		this.tx_rate = new AggregatedRate();
		this.rssi = new ArrayList<>();

		this.bssid = association.bssid;
		this.station = association.station;
		this.connected = association.connected;
		this.inactive = association.inactive;
		this.rssi.add(association.rssi);
		this.rx_bytes = association.rx_bytes;
		this.rx_packets = association.rx_packets;
		this.rx_rate.add(association.rx_rate);
		this.tx_bytes = association.tx_bytes;
		this.tx_duration = association.tx_duration;
		this.tx_failed = association.tx_failed;
		this.tx_packets = association.tx_packets;
		this.tx_rate.add(association.tx_rate);
		this.tx_retries = association.tx_retries;
		this.ack_signal = association.ack_signal;
		this.ack_signal_avg = association.ack_signal_avg;
		this.radio = new Radio(radios);
	}

	/** Add an AggregatedState to this AggregatedState. Succeed only when the two
	 * matches in hashCode.
	 *
	 * @param state input AggregatedState
	 * @return boolean true if the two matches in bssid, station, channel, channel_width and tx_power
	 */
	public boolean add(AggregatedState state) {
		if (hashCode() == state.hashCode()) {
			this.bssid = state.bssid;
			this.station = state.station;
			this.connected = state.connected;
			this.inactive = state.inactive;
			this.rssi.addAll(state.rssi);
			this.rx_bytes = state.rx_bytes;
			this.rx_packets = state.rx_packets;
			this.rx_rate.add(state.rx_rate);
			this.tx_bytes = state.tx_bytes;
			this.tx_duration = state.tx_duration;
			this.tx_failed = state.tx_failed;
			this.tx_packets = state.tx_packets;
			this.tx_rate.add(state.tx_rate);
			this.tx_retries = state.tx_retries;
			this.ack_signal = state.ack_signal;
			this.ack_signal_avg = state.ack_signal_avg;
			this.radio = new Radio(
				state.radio.channel,
				state.radio.channel_width,
				state.radio.tx_power
			);
			return true;
		}
		return false;
	}
}
