/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

public class TestUtils {
	/** The Gson instance. */
	private static final Gson gson = new Gson();

	public static final Instant DEFAULT_WIFISCANENTRY_TIME = Instant.parse("2022-01-01T00:00:00Z");

	/** Create a topology from the given devices in a single zone. */
	public static DeviceTopology createTopology(String zone, String... devices) {
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(devices)));
		return topology;
	}

	/** Create a radio info entry with the given channel on a given band. */
	public static JsonArray createDeviceStatus(String band, int channel) {
		JsonArray jsonList = gson.fromJson(
			String.format(
				"[{\"band\": %s,\"channel\": %d,\"channel-mode\":\"HE\"," +
				"\"channel-width\":20,\"country\":\"CA\",\"tx-power\":20}]",
				band,
				channel
			),
			JsonArray.class
		);
		return jsonList;
	}

	/** Create a radio info entry with the given tx powers and channels. */
	public static JsonArray createDeviceStatusDualBand(int channel2G, int txPower2G, int channel5G, int txPower5G) {
		JsonArray jsonList = gson.fromJson(
			String.format(
				"[{\"band\": \"2G\",\"channel\": %d,\"channel-mode\":\"HE\"," +
				"\"channel-width\":20,\"country\":\"CA\",\"tx-power\":%d}," +
				"{\"band\": \"5G\",\"channel\": %d,\"channel-mode\":\"HE\"," +
				"\"channel-width\":20,\"country\":\"CA\",\"tx-power\":%d}]",
				channel2G,
				txPower2G,
				channel5G,
				txPower5G
			),
			JsonArray.class
		);
		return jsonList;
	}

	/** Create a wifi scan entry with the given channel. */
	public static WifiScanEntry createWifiScanEntry(int channel) {
		WifiScanEntry entry = new WifiScanEntry();
		entry.channel = channel;
		entry.frequency = UCentralUtils.channelToFrequencyMHz(channel);
		entry.signal = -60;
		entry.unixTimeMs = TestUtils.DEFAULT_WIFISCANENTRY_TIME.toEpochMilli();
		return entry;
	}

	/** Create a list of wifi scan entries with the given channels. */
	public static List<WifiScanEntry> createWifiScanList(List<Integer> channels) {
		return channels
			.stream()
			.map(c -> createWifiScanEntry(c))
			.collect(Collectors.toList());
	}

	/** Create a wifi scan entry with the given BSSID and RSSI. */
	public static WifiScanEntry createWifiScanEntryWithBssid(String bssid, Integer rssi) {
		final int channel = 36;
		WifiScanEntry entry = createWifiScanEntry(channel);
		entry.bssid = bssid;
		entry.signal = rssi; // overwrite
		return entry;
	}

	/** Create a list of wifi scan entries with the BSSIDs and RSSIs. */
	public static List<WifiScanEntry> createWifiScanListWithBssid(Map<String, Integer> bssidToRssi) {
		Set<String> bssidSet = bssidToRssi.keySet();
		return bssidSet
			.stream()
			.map(bssid -> createWifiScanEntryWithBssid(bssid, bssidToRssi.get(bssid)))
			.collect(Collectors.toList());
	}

	/**
	 * Create a wifi scan entry with the given channel and channel width info (in
	 * the format of HT operation and VHT operation). It is the caller's
	 * responsibility to make sure {@code channel}, {@code htOper}, and
	 * {@code vhtOper} are consistent.
	 */
	public static WifiScanEntry createWifiScanEntryWithWidth(
		int channel,
		String htOper,
		String vhtOper
	) {
		WifiScanEntry entry = new WifiScanEntry();
		entry.channel = channel;
		entry.frequency = UCentralUtils.channelToFrequencyMHz(channel);
		entry.signal = -60;
		entry.ht_oper = htOper;
		entry.vht_oper = vhtOper;
		entry.unixTimeMs = TestUtils.DEFAULT_WIFISCANENTRY_TIME.toEpochMilli();
		return entry;
	}

	/**
	 * Create a list of wifi scan entries with the given channels and channel width
	 * info (in the format of HT operation and VHT operation). It is the caller's
	 * responsibility to make sure {@code channels}, {@code htOper}, and
	 * {@code vhtOper} are consistent.
	 */
	public static List<WifiScanEntry> createWifiScanListWithWidth(
		List<Integer> channels,
		List<String> htOper,
		List<String> vhtOper
	) {
		List<WifiScanEntry> wifiScanResults = new ArrayList<>();
		for (int i = 0; i < channels.size(); i++) {
			WifiScanEntry wifiScanResult = createWifiScanEntryWithWidth(
				channels.get(i),
				((i >= htOper.size()) ? null : htOper.get(i)),
				((i >= vhtOper.size()) ? null : vhtOper.get(i))
			);
			wifiScanResults.add(wifiScanResult);
		}
		return wifiScanResults;
	}

	/** Create a wifi scan entry with the given channel and bssid. */
	public static WifiScanEntry createWifiScanEntryWithBssid(
		int channel, String bssid
	) {
		WifiScanEntry entry = createWifiScanEntry(channel);
		entry.bssid = bssid;
		return entry;
	}

	/** Create a list of wifi scan entries with the given channels and bssids. */
	public static List<WifiScanEntry> createWifiScanList(
		List<Integer> channels, List<String> bssids
	) {
		List<WifiScanEntry> wifiScanList = new ArrayList<>();
		for (
			int chnIndex = 0;
			chnIndex < channels.size();
			chnIndex ++
		) {
			wifiScanList.add(createWifiScanEntryWithBssid(
				channels.get(chnIndex), bssids.get(chnIndex))
			);
		}
		return wifiScanList;
	}

	/** Create a device state object with the given radio channel. */
	public static State createState(int channel, int channelWidth, String bssid) {
		return createState(channel, channelWidth, 20, 1, 20, 0, bssid);
	}

	/** Create a device state object with the two given radio channels. */
	public static State createState(
		int channelA,
		int channelWidthA,
		int txPowerA,
		int channelB,
		int channelWidthB,
		int txPowerB,
		String bssid
	) {
		State state = gson.fromJson(
			"{\n" +
			"  \"interfaces\": [\n" +
			"    {\n" +
			"      \"counters\": {\n" +
			"        \"collisions\": 0,\n" +
			"        \"multicast\": 6,\n" +
			"        \"rx_bytes\": 13759,\n" +
			"        \"rx_dropped\": 0,\n" +
			"        \"rx_errors\": 0,\n" +
			"        \"rx_packets\": 60,\n" +
			"        \"tx_bytes\": 7051,\n" +
			"        \"tx_dropped\": 0,\n" +
			"        \"tx_errors\": 0,\n" +
			"        \"tx_packets\": 27\n" +
			"      },\n" +
			"      \"location\": \"/interfaces/0\",\n" +
			"      \"name\": \"up0v0\",\n" +
			"	   \"ssids\": [\n" +
			"		 {\n" +
			"			\"counters\": {\n" +
			"        		\"collisions\": 0,\n" +
			"        		\"multicast\": 6,\n" +
			"        		\"rx_bytes\": 13759,\n" +
			"        		\"rx_dropped\": 0,\n" +
			"        		\"rx_errors\": 0,\n" +
			"        		\"rx_packets\": 60,\n" +
			"        		\"tx_bytes\": 7051,\n" +
			"        		\"tx_dropped\": 0,\n" +
			"        		\"tx_errors\": 0,\n" +
			"        		\"tx_packets\": 27\n" +
			"      		},\n" +
			"			\"iface\": \"wlan0\",\n" +
			"			\"mode\": \"ap\",\n" +
			"			\"phy\": \"platform/soc/c000000.wifi\",\n" +
			"           \"radio\": {\n" +
			"				\"$ref\": \"#/radios/0\"\n" +
			"			},\n" +
			"			\"ssid\": \"OpenWifi_dddd\"\n" +
			"		}\n" +
			"	 ]\n" +
			"    },\n" +
			"    {\n" +
			"      \"counters\": {\n" +
			"        \"collisions\": 0,\n" +
			"        \"multicast\": 0,\n" +
			"        \"rx_bytes\": 0,\n" +
			"        \"rx_dropped\": 0,\n" +
			"        \"rx_errors\": 0,\n" +
			"        \"rx_packets\": 0,\n" +
			"        \"tx_bytes\": 4660,\n" +
			"        \"tx_dropped\": 0,\n" +
			"        \"tx_errors\": 0,\n" +
			"        \"tx_packets\": 10\n" +
			"      },\n" +
			"      \"location\": \"/interfaces/1\",\n" +
			"      \"name\": \"down1v0\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"radios\": [\n" +
			"    {\n" +
			"      \"active_ms\": 564328,\n" +
			"      \"busy_ms\": 36998,\n" +
			"      \"noise\": 4294967193,\n" +
			"      \"phy\": \"platform/soc/c000000.wifi\",\n" +
			"      \"receive_ms\": 28,\n" +
			"      \"temperature\": 45,\n" +
			"      \"transmit_ms\": 4893\n" +
			"    },\n" +
			"    {\n" +
			"      \"active_ms\": 564328,\n" +
			"      \"busy_ms\": 36998,\n" +
			"      \"noise\": 4294967193,\n" +
			"      \"phy\": \"platform/soc/c000000.wifi\",\n" +
			"      \"receive_ms\": 28,\n" +
			"      \"temperature\": 45,\n" +
			"      \"transmit_ms\": 4893\n" +
			"    }\n" +
			"  ],\n" +
			"  \"unit\": {\n" +
			"    \"load\": [\n" +
			"      0,\n" +
			"      0,\n" +
			"      0\n" +
			"    ],\n" +
			"    \"localtime\": 1632527275,\n" +
			"    \"memory\": {\n" +
			"      \"free\": 788930560,\n" +
			"      \"total\": 973561856\n" +
			"    },\n" +
			"    \"uptime\": 684456\n" +
			"  }\n" +
			"}",
			State.class
		);
		state.radios[0].addProperty("channel", channelA);
		state.radios[0].addProperty("channel_width", channelWidthA);
		state.radios[0].addProperty("tx_power", txPowerA);
		state.radios[1].addProperty("channel", channelB);
		state.radios[1].addProperty("channel_width", channelWidthB);
		state.radios[1].addProperty("tx_power", txPowerB);
		state.interfaces[0].ssids[0].bssid = bssid;
		return state;
	}
}
