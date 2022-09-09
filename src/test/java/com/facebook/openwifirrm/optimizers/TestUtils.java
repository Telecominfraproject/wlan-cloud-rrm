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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class TestUtils {
	/** The Gson instance. */
	private static final Gson gson = new Gson();

	/** Default value for {@link WifiScanEntry#unixTimeMs} for testing. */
	public static final Instant DEFAULT_WIFISCANENTRY_TIME =
		Instant.parse("2022-01-01T00:00:00Z");

	/** Default channel width in MHz */
	public static final int DEFAULT_CHANNEL_WIDTH = 20;

	/** Default tx power in dBm */
	public static final int DEFAULT_TX_POWER = 20;

	/** Default local time */
	public static final long DEFAULT_LOCAL_TIME = 1632527275;

	/** Create a topology from the given devices in a single zone. */
	public static DeviceTopology createTopology(
		String zone,
		String... devices
	) {
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(devices)));
		return topology;
	}

	/**
	 * Create a radio info object which forms one element of the list of radio
	 * info objects representing a device's status.
	 *
	 * @param band band (e.g., "2G")
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @return a radio info object as a {@code JsonObject}
	 */
	private static JsonObject createDeviceStatusRadioObject(
		String band,
		int channel,
		int channelWidth,
		int txPower
	) {
		return gson.fromJson(
			String.format(
				"{\"band\": %s,\"channel\": %d,\"channel-mode\":\"HE\"," +
					"\"channel-width\":%d,\"country\":\"CA\",\"tx-power\":%d}",
				band,
				channel,
				channelWidth,
				txPower
			),
			JsonObject.class
		);
	}

	/**
	 * Create a radio info object by given lists of keys and values.
	 *
	 * @param keys a list of key strings.
	 * @param values a list of values.
	 * @return a radio info as a {@code JsonNode}
	 */
	public static JsonNode createRadioObject(String[] keys, int[] values) {
		if (
			!(keys.length == values.length)
		) {
			throw new IllegalArgumentException(
				"All arguments must have the same length."
			);
		}
		Map<String, Integer> map = new HashMap<>();
		for (int i = 0; i < keys.length; i++) {
			map.put(keys[i], values[i]);
		}
		return new ObjectMapper().valueToTree(map);
	}

	/**
	 * Create an array with one radio info entry with the given channel on a
	 * given band.
	 */
	public static JsonArray createDeviceStatus(String band, int channel) {
		JsonArray jsonList = new JsonArray();
		jsonList.add(
			createDeviceStatusRadioObject(
				band,
				channel,
				DEFAULT_CHANNEL_WIDTH,
				DEFAULT_TX_POWER
			)
		);
		return jsonList;
	}

	/**
	 * Create the device status array.
	 *
	 * @param band band (e.g., "2G")
	 * @param channel channel number
	 * @param txPower tx power in dBm
	 * @return an array with one radio info entry with the given band, channel,
	 *         and tx power
	 */
	public static JsonArray createDeviceStatus(
		String band,
		int channel,
		int txPower
	) {
		JsonArray jsonList = new JsonArray();
		jsonList.add(
			createDeviceStatusRadioObject(
				band,
				channel,
				DEFAULT_CHANNEL_WIDTH,
				txPower
			)
		);
		return jsonList;
	}

	/**
	 * Create an array with one radio info entry per given band (using the
	 * lowest channel).
	 */
	public static JsonArray createDeviceStatus(List<String> bands) {
		JsonArray jsonList = new JsonArray();
		for (String band : bands) {
			int channel = UCentralUtils.LOWER_CHANNEL_LIMIT.get(band);
			jsonList.add(
				createDeviceStatusRadioObject(
					band,
					channel,
					DEFAULT_CHANNEL_WIDTH,
					DEFAULT_TX_POWER
				)
			);
		}
		return jsonList;
	}

	/**
	 * Create an array with two radio info entries (2G and 5G), with the given
	 * tx powers and channels.
	 */
	public static JsonArray createDeviceStatusDualBand(
		int channel2G,
		int txPower2G,
		int channel5G,
		int txPower5G
	) {
		JsonArray jsonList = new JsonArray();
		jsonList.add(
			createDeviceStatusRadioObject(
				UCentralConstants.BAND_2G,
				channel2G,
				DEFAULT_CHANNEL_WIDTH,
				txPower2G
			)
		);
		jsonList.add(
			createDeviceStatusRadioObject(
				UCentralConstants.BAND_5G,
				channel5G,
				DEFAULT_CHANNEL_WIDTH,
				txPower5G
			)
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
	public static List<WifiScanEntry> createWifiScanList(
		List<Integer> channels
	) {
		return channels
			.stream()
			.map(c -> createWifiScanEntry(c))
			.collect(Collectors.toList());
	}

	/** Create a wifi scan entry with the given BSSID, RSSI, and channel. */
	public static WifiScanEntry createWifiScanEntryWithBssid(
		String bssid,
		Integer rssi,
		int channel
	) {
		WifiScanEntry entry = createWifiScanEntry(channel);
		entry.bssid = bssid;
		entry.signal = rssi; // overwrite
		return entry;
	}

	/** Create a list of wifi scan entries with the BSSIDs, RSSIs, and channel. */
	public static List<WifiScanEntry> createWifiScanListWithBssid(
		Map<String, Integer> bssidToRssi,
		int channel
	) {
		Set<String> bssidSet = bssidToRssi.keySet();
		return bssidSet
			.stream()
			.map(
				bssid -> createWifiScanEntryWithBssid(
					bssid,
					bssidToRssi.get(bssid),
					channel
				)
			)
			.collect(Collectors.toList());
	}

	/**
	 * Create a wifi scan entry with the given channel and channel width info (in
	 * the format of HT operation and VHT operation). It is the caller's
	 * responsibility to make sure {@code channel}, {@code htOper}, and
	 * {@code vhtOper} are consistent.
	 */
	public static WifiScanEntry createWifiScanEntryWithWidth(
		String bssid,
		int channel,
		String htOper,
		String vhtOper
	) {
		WifiScanEntry entry = new WifiScanEntry();
		entry.bssid = bssid;
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
		String bssid,
		List<Integer> channels,
		List<String> htOper,
		List<String> vhtOper
	) {
		List<WifiScanEntry> wifiScanResults = new ArrayList<>();
		for (int i = 0; i < channels.size(); i++) {
			WifiScanEntry wifiScanResult = createWifiScanEntryWithWidth(
				bssid,
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
		int channel,
		String bssid
	) {
		WifiScanEntry entry = createWifiScanEntry(channel);
		entry.bssid = bssid;
		return entry;
	}

	/** Create a list of wifi scan entries with the given channels and bssids. */
	public static List<WifiScanEntry> createWifiScanList(
		List<Integer> channels,
		List<String> bssids
	) {
		List<WifiScanEntry> wifiScanList = new ArrayList<>();
		for (
			int chnIndex = 0;
			chnIndex < channels.size();
			chnIndex++
		) {
			wifiScanList.add(
				createWifiScanEntryWithBssid(
					channels.get(chnIndex),
					bssids.get(chnIndex)
				)
			);
		}
		return wifiScanList;
	}

	/**
	 * Create an uplink0 interface with one radio, to place in
	 * {@link State#interfaces}.
	 *
	 * @param index index of this interface
	 * @return an uplink interface with no radios, to place in
	 *         {@link State#interfaces}
	 */
	private static State.Interface createUpStateInterface(int index) {
		// @formatter:off
		return gson.fromJson(
			String.format(
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
				"      \"location\": \"/interfaces/%d\",\n" +
				"      \"name\": \"up0v%d\",\n" +
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
				"			\"iface\": \"wlan%d\",\n" +
				"			\"mode\": \"ap\",\n" +
				"			\"phy\": \"platform/soc/c000000.wifi\",\n" +
				"           \"radio\": {\n" +
				"				\"$ref\": \"#/radios/%d\"\n" +
				"			},\n" +
				"			\"ssid\": \"OpenWifi_dddd_%d\"\n" +
				"		 }\n" +
				"	   ]\n" +
				"    }\n",
				index,
				index,
				index,
				index,
				index
			),
			State.Interface.class
		);
		// @formatter:on
	}

	/**
	 * Create a downlink1 interface with no radios, to place in
	 * {@link State#interfaces}.
	 *
	 * @param index index of this interface in {@link State#interfaces}
	 * @return a downlink interface with no radios, to place in
	 *         {@link State#interfaces}
	 */
	private static State.Interface createDownStateInterface(int index) {
		// @formatter:off
		return gson.fromJson(
			String.format(
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
				"      \"location\": \"/interfaces/%d\",\n" +
				"      \"name\": \"down1v0\"\n" +
				"    }\n",
				index
			),
			State.Interface.class
		);
		// @formatter:on
	}

	/** Create an element of {@link State#radios}. */
	private static JsonObject createStateRadio() {
		// @formatter:off
		return gson.fromJson(
			String.format(
				"    {\n" +
				"      \"active_ms\": 564328,\n" +
				"      \"busy_ms\": 36998,\n" +
				"      \"noise\": 4294967193,\n" +
				"      \"phy\": \"platform/soc/c000000.wifi\",\n" +
				"      \"receive_ms\": 28,\n" +
				"      \"temperature\": 45,\n" +
				"      \"transmit_ms\": 4893\n" +
				"    }\n"
			),
			JsonObject.class
		);
		// @formatter:on
	}

	/** Create a {@code State.Unit} with specifying localtime. */
	private static State.Unit createStateUnit(Long localtime) {
		// @formatter:off
		String jsonStr = String.format(
		"  {\n" +
		"    \"load\": [\n" +
		"      0,\n" +
		"      0,\n" +
		"      0\n" +
		"    ],\n" +
		"    \"localtime\": %d,\n" +
		"    \"memory\": {\n" +
		"      \"free\": 788930560,\n" +
		"      \"total\": 973561856\n" +
		"    },\n" +
		"    \"uptime\": 684456\n" +
		"  }\n", localtime);
		return gson.fromJson(
			jsonStr,
			State.Unit.class
		);
		// @formatter:on
	}

	/**
	 * Create a state with the appropriate interfaces and radios for local time
	 * and given channels with the associated channel widths and bssids.
	 * <p>
	 * All arguments must have the same length, and an index represents the
	 * details of a radio (e.g., the first radio is on {@code channels[0]} with
	 * a channel width of {@code channelWidths[0]} and its bssid is
	 * {@code bssids[0]}).
	 *
	 * @param channels array of channel numbers
	 * @param channelWidths array of channel widths (MHz)
	 * @param txPowers array of tx powers (dBm)
	 * @param bssids array of BSSIDs.
	 * @param clientRssis 2-D array of client RSSIs.
	 * @param stations 2-D array of client station codes.
	 * @param local time
	 * @return the state of an AP with radios described by the given parameters
	 */
	public static State createState(
		int[] channels,
		int[] channelWidths,
		int[] txPowers,
		String[] bssids,
		String[][] stations,
		int[][] clientRssis,
		long localtime
	) {
		if (
			!(channels.length == channelWidths.length &&
				channelWidths.length == txPowers.length &&
				txPowers.length == bssids.length &&
				bssids.length == stations.length)
		) {
			throw new IllegalArgumentException(
				"All arguments must have the same length."
			);
		}
		final int numRadios = channels.length;
		State state = new State();
		state.interfaces = new State.Interface[numRadios + 1];
		for (int index = 0; index < numRadios; index++) {
			state.interfaces[index] = createUpStateInterface(index);
		}
		state.interfaces[numRadios] = createDownStateInterface(numRadios);
		state.radios = new JsonObject[numRadios];
		for (int i = 0; i < numRadios; i++) {
			state.radios[i] = createStateRadio();
			state.radios[i].addProperty("channel", channels[i]);
			state.radios[i].addProperty("channel_width", channelWidths[i]);
			state.radios[i].addProperty("tx_power", txPowers[i]);
			state.interfaces[i].ssids[0].bssid = bssids[i];
			state.interfaces[i].ssids[0].associations =
				new State.Interface.SSID.Association[clientRssis[i].length];
			for (int j = 0; j < clientRssis[i].length; j++) {
				if (!(clientRssis[i].length == stations[i].length)) {
					throw new IllegalArgumentException(
						"All arguments must have the same length."
					);
				}
				state.interfaces[i].ssids[0].associations[j] =
					state.interfaces[i].ssids[0].new Association();
				state.interfaces[i].ssids[0].associations[j].rssi =
					clientRssis[i][j];
				state.interfaces[i].ssids[0].associations[j].station =
					stations[i][j];
			}
		}
		for (int i = 0; i < numRadios; i++) {
			int ref = Integer.parseInt(
				state.interfaces[i].ssids[0].radio.get("$ref").getAsString().split("/")[2]
			);
			state.interfaces[i].ssids[0].radio = state.radios[ref];
		}
		state.unit = createStateUnit(localtime);
		return state;
	}

	/**
	 * Create a state with the appropriate interfaces and radios for local time
	 * and given channels with the associated channel widths and bssids.
	 * <p>
	 * All arguments must have the same length, and an index represents the
	 * details of a radio (e.g., the first radio is on {@code channels[0]} with
	 * a channel width of {@code channelWidths[0]} and its bssid is
	 * {@code bssids[0]}).
	 *
	 * @param channels array of channel numbers
	 * @param channelWidths array of channel widths (MHz)
	 * @param txPowers array of tx powers (dBm)
	 * @param bssids array of BSSIDs.
	 * @param clientRssis 2-D array of client RSSIs.
	 * @param local time
	 * @return the state of an AP with radios described by the given parameters
	 */
	public static State createState(
		int[] channels,
		int[] channelWidths,
		int[] txPowers,
		String[] bssids,
		int[][] clientRssis,
		long localtime
	) {
		return createState(
			channels,
			channelWidths,
			txPowers,
			bssids,
			new String[clientRssis.length][clientRssis[0].length],
			clientRssis,
			localtime
		);
	}

	/**
	 * Create a device state object with one radio.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @param bssid bssid
	 * @param localtime local time
	 * @return the state of an AP with one radio
	 */
	public static State createState(
		int channel,
		int channelWidth,
		String bssid
	) {
		return createState(
			channel,
			channelWidth,
			DEFAULT_TX_POWER,
			bssid
		);
	}

	/**
	 * Create a device state object with one radio.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @param txPower tx power in dBm
	 * @param bssid bssid
	 * @param localtime local time
	 * @return the state of an AP with one radio
	 */
	public static State createState(
		int channel,
		int channelWidth,
		int txPower,
		String bssid
	) {
		return createState(
			channel,
			channelWidth,
			txPower,
			bssid,
			new int[] {}
		);
	}

	/**
	 * Create a device state object with one radio.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @param txPower tx power in dBm
	 * @param bssid bssid
	 * @param stations array of station codes
	 * @param clientRssis array of client RSSIs
	 * @param localtime local time
	 * @return the state of an AP with one radio
	 */
	public static State createState(
		int channel,
		int channelWidth,
		int txPower,
		String bssid,
		String[] stations,
		int[] clientRssis,
		long localtime
	) {
		return createState(
			new int[] { channel },
			new int[] { channelWidth },
			new int[] { txPower },
			new String[] { bssid },
			new String[][] { stations },
			new int[][] { clientRssis },
			localtime
		);
	}

	/**
	 * Create a device state object with one radio.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @param txPower tx power in dBm
	 * @param bssid bssid
	 * @param clientRssis array of client RSSIs
	 * @param localtime local time
	 * @return the state of an AP with one radio
	 */
	public static State createState(
		int channel,
		int channelWidth,
		int txPower,
		String bssid,
		int[] clientRssis
	) {
		return createState(
			new int[] { channel },
			new int[] { channelWidth },
			new int[] { txPower },
			new String[] { bssid },
			new int[][] { clientRssis },
			DEFAULT_LOCAL_TIME
		);
	}

	/**
	 * Create a device state object with two radios.
	 *
	 * @param channelA channel number
	 * @param channelWidthA channel width (MHz) of channelA
	 * @param txPowerA tx power for channelA
	 * @param bssidA bssid for radio on channelA
	 * @param channelB channel number
	 * @param channelWidthB channel width (MHz) of channelB
	 * @param txPowerB tx power for channelB
	 * @param bssidB bssid for radio on channelB
	 * @return the state of an AP with two radios
	 */
	public static State createState(
		int channelA,
		int channelWidthA,
		int txPowerA,
		String bssidA,
		int channelB,
		int channelWidthB,
		int txPowerB,
		String bssidB
	) {
		return createState(
			channelA,
			channelWidthA,
			txPowerA,
			bssidA,
			new String[] {},
			new int[] {},
			channelB,
			channelWidthB,
			txPowerB,
			bssidB,
			new String[] {},
			new int[] {},
			DEFAULT_LOCAL_TIME
		);
	}

	/**
	 * Create a device state object with two radios.
	 *
	 * @param channelA channel number
	 * @param channelWidthA channel width (MHz) of channelA
	 * @param txPowerA tx power for channelA
	 * @param bssidA bssid for radio on channelA
	 * @param channelB channel number
	 * @param channelWidthB channel width (MHz) of channelB
	 * @param txPowerB tx power for channelB
	 * @param bssidB bssid for radio on channelB
	 * @param localtime local time
	 * @return the state of an AP with two radios
	 */
	public static State createState(
		int channelA,
		int channelWidthA,
		int txPowerA,
		String bssidA,
		int channelB,
		int channelWidthB,
		int txPowerB,
		String bssidB,
		long localtime
	) {
		return createState(
			channelA,
			channelWidthA,
			txPowerA,
			bssidA,
			new String[] {},
			new int[] {},
			channelB,
			channelWidthB,
			txPowerB,
			bssidB,
			new String[] {},
			new int[] {},
			localtime
		);
	}

	/**
	 * Create a device state object with two radios.
	 *
	 * @param channelA channel number
	 * @param channelWidthA channel width (MHz) of channelA
	 * @param txPowerA tx power for channelA
	 * @param bssidA bssid for radio on channelA
	 * @param clientRssisA array of client RSSIs for channelA
	 * @param channelB channel number
	 * @param channelWidthB channel width (MHz) of channelB
	 * @param txPowerB tx power for channelB
	 * @param bssidB bssid for radio on channelB
	 * @param clientRssisB array of client RSSIs for channelB
	 * @return the state of an AP with two radios
	 */
	public static State createState(
		int channelA,
		int channelWidthA,
		int txPowerA,
		String bssidA,
		int[] clientRssisA,
		int channelB,
		int channelWidthB,
		int txPowerB,
		String bssidB,
		int[] clientRssisB
	) {
		return createState(
			new int[] { channelA, channelB },
			new int[] { channelWidthA, channelWidthB },
			new int[] { txPowerA, txPowerB },
			new String[] { bssidA, bssidB },
			new int[][] { clientRssisA, clientRssisB },
			DEFAULT_LOCAL_TIME
		);
	}

	/**
	 * Create a device state object with two radios.
	 *
	 * @param channelA channel number
	 * @param channelWidthA channel width (MHz) of channelA
	 * @param txPowerA tx power for channelA
	 * @param bssidA bssid for radio on channelA
	 * @param clientRssisA array of client RSSIs for channelA
	 * @param channelB channel number
	 * @param channelWidthB channel width (MHz) of channelB
	 * @param txPowerB tx power for channelB
	 * @param bssidB bssid for radio on channelB
	 * @param clientRssisB array of client RSSIs for channelB
	 * @param localtime local time for the State
	 * @return the state of an AP with two radios
	 */
	public static State createState(
		int channelA,
		int channelWidthA,
		int txPowerA,
		String bssidA,
		String[] stationsA,
		int[] clientRssisA,
		int channelB,
		int channelWidthB,
		int txPowerB,
		String bssidB,
		String[] stationsB,
		int[] clientRssisB,
		long localtime
	) {
		return createState(
			new int[] { channelA, channelB },
			new int[] { channelWidthA, channelWidthB },
			new int[] { txPowerA, txPowerB },
			new String[] { bssidA, bssidB },
			new String[][] { stationsA, stationsB },
			new int[][] { clientRssisA, clientRssisB },
			localtime
		);
	}
}
