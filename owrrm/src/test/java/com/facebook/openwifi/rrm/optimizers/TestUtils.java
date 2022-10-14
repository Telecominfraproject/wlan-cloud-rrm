/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.facebook.openwifi.cloudsdk.AggregatedState;
import com.facebook.openwifi.cloudsdk.UCentralConstants;
import com.facebook.openwifi.cloudsdk.UCentralUtils;
import com.facebook.openwifi.cloudsdk.WifiScanEntry;
import com.facebook.openwifi.cloudsdk.models.ap.State;
import com.facebook.openwifi.rrm.DeviceTopology;
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

	/**
	 *  Default local time in unix timestamps in seconds
	 * 	GMT: Fri Sep 24 2021 23:47:55 GMT+0000
	 */
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
	 * Given the channel, gets the lowest band that contains that channel (there
	 * may be multiple bands that contain the same channel number due to channel
	 * numbering schemes).
	 *
	 * @param channel channel number
	 * @return band lowest band containing the channel; null if no such band
	 */
	private static String channelToLowestMatchingBand(int channel) {
		for (String band : UCentralConstants.BANDS) {
			if (UCentralUtils.isChannelInBand(channel, band)) {
				return band;
			}
		}
		return null;
	}

	/**
	 * Converts channel number to that channel's center frequency in MHz. If,
	 * due to channel numbering schemes, the channel number appears in multiple
	 * bands, use the lowest such band.
	 *
	 * @param channel channel number
	 * @return the center frequency of the given channel in MHz
	 */
	private static int channelToFrequencyMHzInLowestMatchingBand(int channel) {
		if (UCentralUtils.isChannelInBand(channel, UCentralConstants.BAND_2G)) {
			if (channel <= 13) {
				return 2407 + 5 * channel;
			} else {
				// special case
				return 2484;
			}
		} else {
			// 5G
			return 5000 + channel;
		}
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
			int channel = UCentralUtils.getLowerChannelLimit(band);
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
	 * Create an array with one radio info entry with the given tx power and
	 * channel.
	 */
	public static JsonArray createDeviceStatusSingleBand(
		int channel,
		int txPower2G
	) {
		JsonArray jsonList = new JsonArray();
		jsonList.add(
			createDeviceStatusRadioObject(
				channelToLowestMatchingBand(channel),
				channel,
				DEFAULT_CHANNEL_WIDTH,
				txPower2G
			)
		);
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
		entry.frequency = channelToFrequencyMHzInLowestMatchingBand(channel);
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
		entry.frequency = channelToFrequencyMHzInLowestMatchingBand(channel);
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
	 * Generate the String for the "phy" field of a State's radio at a given
	 * index. This field also appears in the device capability object.
	 *
	 * @param index index of the radio in the state
	 * @return the String value for the "phy" field of the radio
	 */
	private static String generatePhyString(int index) {
		String phyId = "platform/soc/c000000.wifi";
		if (index > 0) {
			phyId += String.format("+%d", index);
		}
		return phyId;
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
		final String phyId = generatePhyString(index);
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
				"			\"phy\": \"%s\",\n" +
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
				phyId,
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

	/** Create an element of {@link State#radios} at a given index. */
	private static State.Radio createStateRadio(int index) {
		State.Radio radio = new State.Radio();
		radio.active_ms = 564328;
		radio.busy_ms = 36998;
		radio.noise = 4294967193L;
		radio.phy = generatePhyString(index);
		radio.receive_ms = 28;
		radio.transmit_ms = 4893;
		return radio;
	}

	/** Create a {@code State.Unit} with specifying localtime in unix timestamp in seconds. */
	private static State.Unit createStateUnit(long localtime) {
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
		// @formatter:on
		return gson.fromJson(
			jsonStr,
			State.Unit.class
		);
	}

	/**
	 * Create a state with the appropriate interfaces and radios for the given
	 * channels with the associated channel widths and bssids.
	 * <p>
	 * All arguments must have the same length, and an index represents the
	 * details of a radio (e.g., the first radio is on {@code channels[0]} with
	 * a channel width of {@code channelWidths[0]} and its bssid is
	 * {@code bssids[0]}).
	 *
	 * @param channels array of channel numbers
	 * @param channelWidths array of channel widths (MHz)
	 * @param txPowers array of tx powers (dBm)
	 * @param bssids array of BSSIDs
	 * @param stations 2-D array of client station codes
	 * @param clientRssis 2-D array of client RSSIs
	 * @param localtime unix timestamp in seconds.
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
				bssids.length == stations.length &&
				stations.length == clientRssis.length)
		) {
			throw new IllegalArgumentException(
				"All array-type arguments must have the same length."
			);
		}
		final int numRadios = channels.length;
		State state = new State();
		state.interfaces = new State.Interface[numRadios + 1];
		for (int index = 0; index < numRadios; index++) {
			state.interfaces[index] = createUpStateInterface(index);
		}
		state.interfaces[numRadios] = createDownStateInterface(numRadios);
		state.radios = new State.Radio[numRadios];
		for (int i = 0; i < numRadios; i++) {
			state.radios[i] = createStateRadio(i);
			state.radios[i].channel = channels[i];
			state.radios[i].channel_width = Integer.toString(channelWidths[i]);
			state.radios[i].tx_power = txPowers[i];
			state.interfaces[i].ssids[0].bssid = bssids[i];
			state.interfaces[i].ssids[0].associations =
				new State.Interface.SSID.Association[clientRssis[i].length];
			for (int j = 0; j < clientRssis[i].length; j++) {
				state.interfaces[i].ssids[0].associations[j] =
					new State.Interface.SSID.Association();
				state.interfaces[i].ssids[0].associations[j].rssi =
					clientRssis[i][j];
				state.interfaces[i].ssids[0].associations[j].station =
					stations[i][j];
				state.interfaces[i].ssids[0].associations[j].bssid = bssids[i];
				state.interfaces[i].ssids[0].radio = gson
					.fromJson(gson.toJson(state.radios[i]), JsonObject.class);
			}
		}
		state.unit = createStateUnit(localtime);
		return state;
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
	 * @param localtime unix timestamp in seconds.
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
	 * @param bssid bssid
	 * @return the state of an AP with one radio
	 */
	public static State createState(
		int channel,
		int channelWidth,
		String bssid
	) {
		return createState(channel, channelWidth, DEFAULT_TX_POWER, bssid);
	}

	/**
	 * Create a device state object with one radio.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width in MHz
	 * @param txPower tx power in dBm
	 * @param bssid bssid
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
	 * @param clientRssis array of client RSSIs
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
			new String[][] { new String[clientRssis.length] },
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
			new int[] { channelA, channelB },
			new int[] { channelWidthA, channelWidthB },
			new int[] { txPowerA, txPowerB },
			new String[] { bssidA, bssidB },
			new String[][] { new String[] {}, new String[] {} },
			new int[][] { new int[] {}, new int[] {} },
			DEFAULT_LOCAL_TIME
		);
	}

	/**
	* Create a device state object with two radios.
	*
	* @param channelA channel number
	* @param channelWidthA channel width (MHz) of channelA
	* @param txPowerA tx power (dB) for channelA
	* @param bssidA bssid for radio on channelA
	* @param clientRssisA array of client RSSIs for channelA
	* @param channelB channel number
	* @param channelWidthB channel width (MHz) of channelB
	* @param txPowerB tx power (dB) for channelB
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

	/**
	 * Create a radio capability object which is part of the device capability.
	 */
	public static JsonObject createRadioCapability(String band) {
		JsonObject radioCapability = new JsonObject();
		JsonArray bandArray = new JsonArray();
		bandArray.add(band);
		radioCapability.add("band", bandArray);
		// the following fields are present but unused so they are excluded here
		// channels
		// dfs_channels
		// frequencies
		// he_mac_capa
		// he_phy_capa
		// ht_capa
		// htmode
		// rx_ant
		// tx_ant
		// vht_capa
		return radioCapability;
	}

	/** Create a device capability object with radios in the given bands. */
	public static JsonObject createDeviceCapability(String[] bands) {
		JsonObject deviceCapability = new JsonObject();
		for (int i = 0; i < bands.length; i++) {
			String phyId = generatePhyString(i);
			JsonObject radioCapability = createRadioCapability(bands[i]);
			deviceCapability.add(phyId, radioCapability);
		}
		return deviceCapability;
	}

	/** Create a device capability object with a radio in the given band. */
	public static JsonObject createDeviceCapability(String band) {
		return createDeviceCapability(new String[] { band });
	}

	/**
	 * Create an AggregatedState from given radio info.
	 *
	 * @param channel channel number
	 * @param channelWidth channel width (MHz) of channelA
	 * @param txPower tx power (db) for this channel
	 * @param bssid bssid for radio on this channel
	 * @param station station string for radio on this channel
	 * @param clientRssi array of client RSSIs.
	 * @return AggregatedState creating from the given radio.
	 */
	public static AggregatedState createAggregatedState(
		int channel,
		int channelWidth,
		int txPower,
		String bssid,
		String station,
		int[] clientRssi
	) {
		AggregatedState state = new AggregatedState();
		state.radio = new AggregatedState.Radio(channel, channelWidth, txPower);
		state.bssid = bssid;
		state.station = station;
		for (int rssi : clientRssi) {
			state.rssi.add(rssi);
		}
		return state;
	}
}
