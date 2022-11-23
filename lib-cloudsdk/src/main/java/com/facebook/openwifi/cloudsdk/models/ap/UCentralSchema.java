/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import java.util.List;

import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;

public class UCentralSchema {
	public static class Radio {
		public String band;
		public int bandwidth;
		public JsonPrimitive channel; // either "auto" or int
		@SerializedName("valid-channels") public int[] validChannels;
		public String country;
		@SerializedName("allow-dfs") public boolean allowDfs;
		@SerializedName("channel-mode") public String channelMode;
		@SerializedName("channel-width") public int channelWidth;
		@SerializedName("require-mode") public String requireMode;
		public String mimo;
		@SerializedName("tx-power") public int txPower;
		@SerializedName("legacy-rates") public boolean legacyRates;
		@SerializedName("beacon-interval") public int beaconInterval;
		@SerializedName("dtim-period") public int dtimPeriod;
		@SerializedName("maximum-clients") public int maximumClients;

		public static class Rates {
			public int beacon;
			public int multicast;
		}

		public Rates rates;

		public static class HESettings {
			@SerializedName("multiple-bssid") public boolean multipleBssid;
			public boolean ema;
			@SerializedName("bss-color") public int bssColor;
		}

		@SerializedName("he-settings") public HESettings heSettings;

		@SerializedName("hostapd-iface-raw") public String[] hostapdIfaceRaw;
	}

	public List<Radio> radios;

	public static class Metrics {
		public static class Statistics {
			public int interval;
			public List<String> types;
		}

		public Statistics statistics;

		public static class Health {
			public int interval;
		}

		public Health health;

		public static class WifiFrames {
			public List<String> filters;
		}

		@SerializedName("wifi-frames") public WifiFrames wifiFrames;

		public static class DhcpSnooping {
			public List<String> filters;
		}

		@SerializedName("dhcp-snooping") public DhcpSnooping dhcpSnooping;
	}

	public Metrics metrics;

	// TODO also add fields below as needed
	// unit
	// globals
	// definitions
	// ethernet
	// switch
	// interfaces
	// services
}
