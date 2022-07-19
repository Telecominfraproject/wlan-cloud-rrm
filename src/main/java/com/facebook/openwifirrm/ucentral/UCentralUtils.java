/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.RRMConfig;
import com.facebook.openwifirrm.Utils;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * uCentral utility methods/structures.
 */
public class UCentralUtils {
	private static final Logger logger = LoggerFactory.getLogger(UCentralUtils.class);

	/** The Gson instance. */
	private static final Gson gson = new Gson();

	// This class should not be instantiated.
	private UCentralUtils() {}

	/** Represents a single entry in wifi scan results. */
	public static class WifiScanEntry {
		public int channel;
		public long last_seen;
		public int signal;
		public String bssid;
		public String ssid;
		public long tsf;
		public String ht_oper;
		public String vht_oper;
		public int capability;
		public int frequency;
		public JsonArray ies;

		/** Default Constructor. */
		public WifiScanEntry() {}

		/** Copy Constructor. */
		public WifiScanEntry(WifiScanEntry o) {
			this.channel = o.channel;
			this.last_seen = o.last_seen;
			this.signal = o.signal;
			this.bssid = o.bssid;
			this.ssid = o.ssid;
			this.tsf = o.tsf;
			this.ht_oper = o.ht_oper;
			this.vht_oper = o.vht_oper;
			this.capability = o.capability;
			this.frequency = o.frequency;
			this.ies = o.ies;
		}
	}

	/**
	 * Parse a JSON wifi scan result into a list of WifiScanEntry objects.
	 *
	 * Returns null if any parsing/deserialization error occurred.
	 */
	public static List<WifiScanEntry> parseWifiScanEntries(JsonObject result) {
		List<WifiScanEntry> entries = new ArrayList<>();
		try {
			JsonArray scanInfo = result
				.getAsJsonObject("status")
				.getAsJsonArray("scan");
			for (JsonElement e : scanInfo) {
				entries.add(gson.fromJson(e, WifiScanEntry.class));
			}
		} catch (Exception e) {
			return null;
		}
		return entries;
	}

	/**
	 * Set all radios config of an AP to a given value.
	 *
	 * Returns true if changed, or false if unchanged for any reason.
	 */
	public static boolean setRadioConfigField(
		String serialNumber,
		UCentralApConfiguration config,
		String fieldName,
		Map<String, Integer> newValueList
	) {
		boolean wasModified = false;
		int radioCount = config.getRadioCount();

		// Iterate all the radios of an AP to find the corresponding band
		for (int radioIndex = 0; radioIndex < radioCount; radioIndex++) {
			JsonObject radioConfig = config.getRadioConfig(radioIndex);
			String operationalBand = radioConfig.get("band").getAsString();

			if (!newValueList.containsKey(operationalBand)) {
				continue;
			}

			// If the field doesn't exist in config, we generate the fieldName and
			// assign the new value to it.
			int newValue = newValueList.get(operationalBand);
			if (!radioConfig.has(fieldName)) {
				radioConfig.addProperty(fieldName, newValue);
				config.setRadioConfig(radioIndex, radioConfig);
				logger.info(
					"Device {}: setting {} {} to {} (was empty)",
					serialNumber,
					operationalBand,
					fieldName,
					newValue
				);
				wasModified = true;
				continue;
			}

			// Compare vs. existing value
			int currentValue = radioConfig.get(fieldName).getAsInt();
			if (currentValue == newValue) {
				logger.info(
					"Device {}: {} {} is already {}",
					serialNumber,
					operationalBand,
					fieldName,
					newValue
				);
			} else {
				// Update to new value
				radioConfig.addProperty(fieldName, newValue);
				config.setRadioConfig(radioIndex, radioConfig);
				logger.info(
					"Device {}: setting {} {} to {} (was {})",
					serialNumber,
					operationalBand,
					fieldName,
					newValue,
					currentValue
				);
				wasModified = true;
			}
		}
		return wasModified;
	}

	/**
	 * Get the APs on a band who participate in an optimization algorithm.
	 * Get the info from the configuration field in deviceStatus
	 * (Since the State doesn't explicitly show the "band" info)
	 *
	 * Returns the results map
	 */
	public static Map<String, List<String>> getBandsMap(
		Map<String, JsonArray> deviceStatus
	) {
		Map<String, List<String>> bandsMap = new HashMap<>();

		for (String serialNumber : deviceStatus.keySet()) {
			JsonArray radioList = deviceStatus.get(serialNumber).getAsJsonArray();
			for (int radioIndex = 0; radioIndex < radioList.size(); radioIndex++) {
				JsonElement e = radioList.get(radioIndex);
				if (!e.isJsonObject()) {
					return null;
				}
				JsonObject radioObject = e.getAsJsonObject();
				String band = radioObject.get("band").getAsString();
				bandsMap
					.computeIfAbsent(band, k -> new ArrayList<>())
					.add(serialNumber);
			}
		}

		return bandsMap;
	}

	/**
	 * Get the capabilities of the APs who participate in an optimization algorithm.
	 *
	 * @param deviceStatus map of {device, status}
	 * @param deviceCapabilities map of {device, capabilities info}
	 * @param defaultAvailableChannels map of {band, list of available channels}
	 *
	 * @return the results map of {band, {device, list of available channels}}
	 */
	public static Map<String, Map<String, List<Integer>>> getDeviceAvailableChannels(
		Map<String, JsonArray> deviceStatus,
		Map<String, JsonObject> deviceCapabilities,
		Map<String, List<Integer>> defaultAvailableChannels
	) {
		Map<String, Map<String, List<Integer>>> deviceAvailableChannels =
			new HashMap<>();

		for (String serialNumber : deviceStatus.keySet()) {
			JsonArray radioList = deviceStatus.get(serialNumber).getAsJsonArray();
			for (int radioIndex = 0; radioIndex < radioList.size(); radioIndex++) {
				JsonElement e = radioList.get(radioIndex);
				if (!e.isJsonObject()) {
					return null;
				}
				JsonObject radioObject = e.getAsJsonObject();
				String band = radioObject.get("band").getAsString();

				JsonObject capabilitesObject = deviceCapabilities.get(serialNumber);
				List<Integer> availableChannels = new ArrayList<>();
				if (capabilitesObject == null) {
					availableChannels.addAll(defaultAvailableChannels.get(band));
				} else {
					Set<Entry<String, JsonElement>> entrySet = capabilitesObject
						.entrySet();
					for (Map.Entry<String, JsonElement> f : entrySet) {
						String bandInsideObject = f.getValue()
							.getAsJsonObject()
							.get("band")
							.getAsString();
						if (bandInsideObject.equals(band)) {
							// (TODO) Remove the following dfsChannels code block
							// when the DFS channels are available
							Set<Integer> dfsChannels = new HashSet<>();
							try {
								JsonArray channelInfo = f.getValue()
									.getAsJsonObject()
									.get("dfs_channels")
									.getAsJsonArray();

								for (JsonElement d : channelInfo) {
									dfsChannels.add(d.getAsInt());
								}
							} catch (Exception d) {}
							try {
								JsonArray channelInfo = f.getValue()
									.getAsJsonObject()
									.get("channels")
									.getAsJsonArray();
								for (JsonElement c : channelInfo) {
									int channel = c.getAsInt();
									if (!dfsChannels.contains(channel)) {
										availableChannels.add(channel);
									}
								}
							} catch (Exception c) {
								availableChannels
									.addAll(defaultAvailableChannels.get(band));
							}
						}
					}
				}

				deviceAvailableChannels.computeIfAbsent(
					band, k -> new HashMap<>()
				).put(
					serialNumber, availableChannels
				);
			}
		}
		return deviceAvailableChannels;
	}

	/**
	 * Get the mapping between bssids and APs.
	 * Get the info from the State data
	 *
	 * Returns the results map
	 */
	public static Map<String, String> getBssidsMap(Map<String, State> latestState) {
		Map<String, String> bssidMap = new HashMap<>();
		for (Map.Entry<String, State> e: latestState.entrySet()) {
		    State state = e.getValue();
		    for (
		    	int interfaceIndex = 0;
		    	interfaceIndex < state.interfaces.length;
		    	interfaceIndex++
			) {
		    	if (state.interfaces[interfaceIndex].ssids == null) {
		    		continue;
		    	}
		    	for (
		    		int ssidIndex = 0;
		    		ssidIndex < state.interfaces[interfaceIndex].ssids.length;
		    		ssidIndex++
		    	) {
		    		bssidMap.put(
		    			state.interfaces[interfaceIndex].ssids[ssidIndex].bssid,
		    			e.getKey()
		    		);
		    	}
		    }
		}
		return bssidMap;
	}

	/** Generate the RRM service key. */
	public static String generateServiceKey(RRMConfig.ServiceConfig serviceConfig) {
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			sha256.update(serviceConfig.publicEndpoint.getBytes());
			sha256.update(serviceConfig.privateEndpoint.getBytes());
			return Utils.bytesToHex(sha256.digest());
		} catch (NoSuchAlgorithmException e) {
			logger.error("Unable to generate service key", e);
			return "";
		}
	}
}
