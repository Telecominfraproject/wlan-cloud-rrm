/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class State {
	public static class Interface {
		public static class Client {
			public String mac;
			public String[] ipv4_addresses;
			public String[] ipv6_addresses;
			public String[] ports;
			// TODO last_seen
		}

		public static class SSID {
			public static class Association {
				public static class Rate {
					public long bitrate;
					public int chwidth;
					public boolean sgi;
					public boolean ht;
					public boolean vht;
					public boolean he;
					public int mcs;
					public int nss;
					public int he_gi;
					public int he_dcm;
				}

				public String bssid;
				public String station;
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
				public JsonObject[] tid_stats; // TODO: see cfg80211_tid_stats
			}

			public Association[] associations;
			public String bssid;
			public String ssid;
			public Counters counters;
			public String iface;
			public String mode;
			public String phy;
			public JsonObject radio;
		}

		public static class Counters {
			public long collisions;
			public long multicast;
			public long rx_bytes;
			public long rx_packets;
			public long rx_errors;
			public long rx_dropped;
			public long tx_bytes;
			public long tx_packets;
			public long tx_errors;
			public long tx_dropped;
		}

		public Client[] clients;
		public SSID[] ssids;
		public Counters counters;
		public String location;
		public String name;
		public String ntp_server;
		public String[] dns_servers;
		public long uptime;
		// TODO
		public JsonObject ipv4;
		public JsonObject ipv6;
		public JsonObject[] lldp;
		// TODO ports ?
	}

	public Interface[] interfaces;

	public static class Unit {
		public static class Memory {
			public long buffered;
			public long cached;
			public long free;
			public long total;
		}

		public double[] load;
		public long localtime;
		public Memory memory;
		public long uptime;
	}

	public Unit unit;

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

	public Radio[] radios;

	// TODO
	@SerializedName("link-state") public JsonObject linkState;
	public JsonObject gps;
	public JsonObject poe;
}
