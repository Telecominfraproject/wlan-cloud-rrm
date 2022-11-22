/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca.stats.inputs;

/**
 * Input data model.
 *
 * TODO: very incomplete
  */
public class InputStats {
	/** Radio parameters */
	public static class Radio {
		public long active_ms;
		public long busy_ms;
		public int channel;
		public String channel_width;
		public long noise;
		public String phy;
		public long receive_ms;
		public long transmit_ms;
		public int tx_power;
	}

	public static class SSID {
		public static class Association {
			public static class Rate {
				public long bitrate;
				public int chwidth;
				public int mcs;
			}

			public String bssid; // bssid of the AP radio
			public String station; // client MAC
			public long connected;
			public long inactive;
			public int rssi;
			public long rx_bytes;
			public long rx_packets;
			public Rate rx_rate;
			public long tx_bytes;
			public long tx_duration;
			public long tx_failed;
			public long tx_offset;
			public long tx_packets;
			public Rate tx_rate;
			public long tx_retries;
			public int ack_signal;
			public int ack_signal_avg;
		}

		public Association[] associations;
		public Radio radio;
	}

	/** Counters are for the wireless interface as a whole */
	public static class Counters {
		public long rx_bytes;
		public long rx_packets;
		public long rx_errors;
		public long rx_dropped;
		public long tx_bytes;
		public long tx_packets;
		public long tx_errors;
		public long tx_dropped;
	}

	public SSID[] ssids;
	public Counters counters;

	/** Unix time in milliseconds */
	public long timestamp;
}
