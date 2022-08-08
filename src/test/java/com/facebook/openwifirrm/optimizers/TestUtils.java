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

import org.apache.commons.codec.binary.Base64;

import com.facebook.openwifirrm.ChannelWidth;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.Utils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

public class TestUtils {
	/** The Gson instance. */
	private static final Gson gson = new Gson();

	public static final Instant DEFAULT_START_TIME = Instant.parse("2022-01-01T00:00:00Z");

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

	/**
	 *
	 * @param channel channel index. See
	 *                {@link ChannelOptimizer#AVAILABLE_CHANNELS_BAND} for channels
	 *                in each band.
	 * @return the center frequency of the given channel in MHz
	 */
	private static int channelToFrequencyMHz(int channel) {
		if (ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(UCentralConstants.BAND_2G).contains(channel)) {
			return 2407 + 5 * channel;
		} else if (ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(UCentralConstants.BAND_5G).contains(channel)) {
			return 5000 + channel;
		} else {
			throw new IllegalArgumentException("Must provide a valid channel.");
		}
	}

	/** Create a wifi scan entry with the given channel. */
	public static WifiScanEntry createWifiScanEntry(int channel) {
		WifiScanEntry entry = new WifiScanEntry();
		entry.channel = channel;
		entry.frequency = channelToFrequencyMHz(channel);
		entry.signal = -60;
		entry.unixTimeMs = TestUtils.DEFAULT_START_TIME.toEpochMilli();
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
	 * responsibility to make sure {@code channel}, {@code ht_oper}, and
	 * {@code vht_oper} are consistent.
	 */
	public static WifiScanEntry createWifiScanEntryWithWidth(
		int channel,
		String htOper,
		String vhtOper
	) {
		WifiScanEntry entry = new WifiScanEntry();
		entry.channel = channel;
		entry.frequency = channelToFrequencyMHz(channel);
		entry.signal = -60;
		entry.ht_oper = htOper;
		entry.vht_oper = vhtOper;
		entry.unixTimeMs = TestUtils.DEFAULT_START_TIME.toEpochMilli();
		return entry;
	}

	/**
	 * Create a list of wifi scan entries with the given channels and channel width
	 * info (in the format of HT operation and VHT operation). It is the caller's
	 * responsibility to make sure {@code channels}, {@code ht_oper}, and
	 * {@code vht_oper} are consistent.
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

	/**
	 *
	 * NOTE: some combinations of channelWidth, channel, channel2, and vhtMcsAtNss
	 * are invalid as defined by 802.11-2020, but this is not checked here. If
	 * fidelity to 802.11 is required, the caller of this method must make sure to
	 * pass in valid parameters.
	 *
	 * @param channelWidth
	 * @param channel1     If the channel is 20 MHz, 40 MHz, or 80 MHz wide, this
	 *                     parameter should be the channel index. E.g., channel 36
	 *                     is the channel centered at 5180 MHz. For a 160 MHz wide
	 *                     channel, this parameter should be the channel index of
	 *                     the 80MHz channel that contains the primary channel. For
	 *                     a 80+80 MHz wide channel, this parameter should be the
	 *                     channel index of the primary channel.
	 * @param channel2     This should be zero unless the channel is 160MHz or 80+80
	 *                     MHz wide. If the channel is 160 MHz wide, this parameter
	 *                     should contain the channel index of the 160 MHz wide
	 *                     channel. If the channel is 80+80 MHz wide, it should be
	 *                     the channel index of the secondary 80 MHz wide channel.
	 * @param vhtMcsForNss An 8-element array where each element is between 0 and 4
	 *                     inclusive. MCS means Modulation and Coding Scheme. NSS
	 *                     means Number of Spatial Streams. There can be 1, 2, ...,
	 *                     or 8 spatial streams. For each NSS, the corresponding
	 *                     element in the array should specify which MCSs are
	 *                     supported for that NSS in the following manner: 0
	 *                     indicates support for VHT-MCS 0-7, 1 indicates support
	 *                     for VHT-MCS 0-8, 2 indicates support for VHT-MCS 0-9, and
	 *                     3 indicates that no VHT-MCS is supported for that NSS.
	 *                     For the specifics of what each VHT-MCS is, see IEEE
	 *                     802.11-2020, Table "21-29" through Table "21-60".
	 * @return base64 encoded vht operator as a String
	 */
	public static String getVhtOper(ChannelWidth channelWidth, byte channel1, byte channel2, byte[] vhtMcsForNss) {
		byte[] vht_oper = new byte[5];
		boolean channelWidthByte = !(channelWidth == ChannelWidth.MHz_20 || channelWidth == ChannelWidth.MHz_40);
		// overflow shouldn't matter, we only care about the raw bit representation
		byte channelCenterFrequencySegment0 = channel1;
		byte channelCenterFrequencySegment1 = channel2;

		vht_oper[0] = (byte) (Utils.boolToInt(channelWidthByte));
		vht_oper[1] = channelCenterFrequencySegment0;
		vht_oper[2] = channelCenterFrequencySegment1;
		vht_oper[3] = (byte) (vhtMcsForNss[0] << 6 | vhtMcsForNss[1] << 4 | vhtMcsForNss[2] << 2 | vhtMcsForNss[3]);
		vht_oper[4] = (byte) (vhtMcsForNss[4] << 6 | vhtMcsForNss[5] << 4 | vhtMcsForNss[6] << 2 | vhtMcsForNss[7]);
		return Base64.encodeBase64String(vht_oper);
	}

	public static String getVhtOper() {
		return getVhtOper(ChannelWidth.MHz_20, (byte) 36, (byte) 0, new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 });
	}

	/**
	 * NOTE: some combinations of these parameters may be invalid as defined by
	 * 802.11-2020, but this is not checked here. If fidelity to 802.11 is required,
	 * the caller of this method must make sure to pass in valid parameters. The
	 * 802.11-2020 specification has more details about the parameters.
	 *
	 * @param primaryChannel                 channel index
	 * @param secondaryChannelOffset
	 * @param staChannelWidth
	 * @param rifsMode
	 * @param htProtection
	 * @param nongreenfieldHtStasPresent
	 * @param obssNonHtStasPresent
	 * @param channelCenterFrequencySegment2
	 * @param dualBeacon
	 * @param dualCtsProtection
	 * @param stbcBeacon
	 * @return base64 encoded ht operator as a String
	 */
	public static String getHtOper(byte primaryChannel, byte secondaryChannelOffset, boolean staChannelWidth,
			boolean rifsMode, byte htProtection, boolean nongreenfieldHtStasPresent, boolean obssNonHtStasPresent,
			byte channelCenterFrequencySegment2, boolean dualBeacon, boolean dualCtsProtection, boolean stbcBeacon) {
		byte[] ht_oper = new byte[22];
		ht_oper[0] = primaryChannel;
		ht_oper[1] = (byte) (secondaryChannelOffset << 6 | Utils.boolToInt(staChannelWidth) << 5
				| Utils.boolToInt(rifsMode) << 4);
		ht_oper[2] = (byte) (htProtection << 6 | Utils.boolToInt(nongreenfieldHtStasPresent) << 5
				| Utils.boolToInt(obssNonHtStasPresent) << 3 | channelCenterFrequencySegment2 >>> 5);
		ht_oper[3] = (byte) (channelCenterFrequencySegment2 << 5);
		ht_oper[4] = (byte) (Utils.boolToInt(dualBeacon) << 1 | Utils.boolToInt(dualCtsProtection));
		ht_oper[5] = (byte) (Utils.boolToInt(stbcBeacon) << 7);
		// the next 16 bytes are for the basic HT-MCS set
		// a default is chosen; if needed, we can add a parameter to set these
		ht_oper[6] = 0;
		ht_oper[7] = 0;
		ht_oper[8] = 0;
		ht_oper[9] = 0;
		ht_oper[10] = 0;
		ht_oper[11] = 0;
		ht_oper[12] = 0;
		ht_oper[13] = 0;
		ht_oper[14] = 0;
		ht_oper[15] = 0;
		ht_oper[16] = 0;
		ht_oper[17] = 0;

		ht_oper[18] = 0;
		ht_oper[19] = 0;
		ht_oper[20] = 0;
		ht_oper[21] = 0;

		return Base64.encodeBase64String(ht_oper);
	}

	public static String getHtOper() {
		return getHtOper((byte) 1, (byte) 0, false, false, (byte) 0, true, false, (byte) 0, false, false, false);
	}
}
