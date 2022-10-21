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
		// either "auto" or int
		public JsonPrimitive channel;
		@SerializedName("valid-channels") public int[] validChannels;
		public String country;
		@SerializedName("allow-dfs") public boolean allowDfs = true;
		@SerializedName("channel-mode") public String channelMode = "HE";
		@SerializedName("channel-wdith") public int channelWidth = 80;
		@SerializedName("require-mode") public String requireMode;
		public String mimo;
		@SerializedName("tx-power") public int txPower;
		@SerializedName("legacy-rates") public boolean legacyRates = false;
		@SerializedName("beacon-interval") public int beaconInterval = 100;
		@SerializedName("dtim-period") public int dtimPeriod = 2;
		@SerializedName("maximum-clients") public int maximumClients;

		public static class Rates {
			public int beacon = 6000;
			public int multicast = 24000;
		}

		public Rates rates;

		public static class HESettings {
			@SerializedName(
				"multiple-bssid"
			) public boolean multipleBssid = false;
			public boolean ema = false;
			@SerializedName("bss-color") public int bssColor = 64;
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

	// metrics
	// TODO the below fields are unused right now - include them as necessary
	// unit
	// globals
	// definitions
	// ethernet
	// switch
	// interfaces
	// services
}
