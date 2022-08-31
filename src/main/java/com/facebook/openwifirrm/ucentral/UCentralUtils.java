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
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.RRMConfig;
import com.facebook.openwifirrm.Utils;
import com.facebook.openwifirrm.optimizers.channel.ChannelOptimizer;
import com.facebook.openwifirrm.ucentral.models.State;
import com.facebook.openwifirrm.ucentral.models.WifiScanEntryResult;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * uCentral utility methods/structures.
 */
public class UCentralUtils {
	private static final Logger logger =
		LoggerFactory.getLogger(UCentralUtils.class);

	/** The Gson instance. */
	private static final Gson gson = new Gson();

	/** Map of band to the band-specific lowest available channel*/
	public static final Map<String, Integer> LOWER_CHANNEL_LIMIT =
		new HashMap<>();
	static {
		UCentralUtils.LOWER_CHANNEL_LIMIT.put(UCentralConstants.BAND_2G, 1);
		UCentralUtils.LOWER_CHANNEL_LIMIT.put(UCentralConstants.BAND_5G, 36);
	}

	/** Map of band to the band-specific highest available channel*/
	public static final Map<String, Integer> UPPER_CHANNEL_LIMIT =
		new HashMap<>();
	static {
		UCentralUtils.UPPER_CHANNEL_LIMIT.put(UCentralConstants.BAND_2G, 11);
		UCentralUtils.UPPER_CHANNEL_LIMIT.put(UCentralConstants.BAND_5G, 165);
	}

	// This class should not be instantiated.
	private UCentralUtils() {}

	/**
	 * Extends {@link WifiScanEntryResult} to track the response time of the entry.
	 */
	public static class WifiScanEntry extends WifiScanEntryResult {
		/**
		 * Unix time in milliseconds (ms). This field is not defined in the uCentral
		 * API. This is added it because {@link WifiScanEntryResult#tsf} is an unknown
		 * time reference.
		 */
		public long unixTimeMs;

		/** Default Constructor. */
		public WifiScanEntry() {}

		/** Copy Constructor. */
		public WifiScanEntry(WifiScanEntry o) {
			super(o);
			this.unixTimeMs = o.unixTimeMs;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = super.hashCode();
			result = prime * result + Objects.hash(unixTimeMs);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!super.equals(obj)) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			WifiScanEntry other = (WifiScanEntry) obj;
			return unixTimeMs == other.unixTimeMs;
		}

		@Override
		public String toString() {
			return String.format(
				"WifiScanEntry[signal=%d, bssid=%s, unixTimeMs=%d]",
				signal,
				bssid,
				unixTimeMs
			);
		}
	}

	/**
	 * Parse a JSON wifi scan result into a list of WifiScanEntry objects.
	 *
	 * @param result      result of the wifiscan
	 * @param timestampMs Unix time in ms
	 * @return list of wifiscan entries, or null if any parsing/deserialization
	 *         error occurred.
	 */
	public static List<WifiScanEntry> parseWifiScanEntries(
		JsonObject result,
		long timestampMs
	) {
		List<WifiScanEntry> entries = new ArrayList<>();
		try {
			JsonArray scanInfo = result
				.getAsJsonObject("status")
				.getAsJsonArray("scan");
			for (JsonElement e : scanInfo) {
				WifiScanEntry entry = gson.fromJson(e, WifiScanEntry.class);
				entry.unixTimeMs = timestampMs;
				entries.add(entry);

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
			if (radioConfig == null) {
				continue;
			}
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
			JsonArray radioList =
				deviceStatus.get(serialNumber).getAsJsonArray();
			for (
				int radioIndex = 0; radioIndex < radioList.size(); radioIndex++
			) {
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
			JsonArray radioList =
				deviceStatus.get(serialNumber).getAsJsonArray();
			for (
				int radioIndex = 0; radioIndex < radioList.size(); radioIndex++
			) {
				JsonElement e = radioList.get(radioIndex);
				if (!e.isJsonObject()) {
					return null;
				}
				JsonObject radioObject = e.getAsJsonObject();
				String band = radioObject.get("band").getAsString();

				JsonObject capabilitesObject =
					deviceCapabilities.get(serialNumber);
				List<Integer> availableChannels = new ArrayList<>();
				if (capabilitesObject == null) {
					availableChannels
						.addAll(defaultAvailableChannels.get(band));
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
					band,
					k -> new HashMap<>()
				)
					.put(
						serialNumber,
						availableChannels
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
	public static Map<String, String> getBssidsMap(
		Map<String, State> latestState
	) {
		Map<String, String> bssidMap = new HashMap<>();
		for (Map.Entry<String, State> e : latestState.entrySet()) {
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
	public static String generateServiceKey(
		RRMConfig.ServiceConfig serviceConfig
	) {
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

	/**
	 * Converts channel number to that channel's center frequency in MHz.
	 *
	 * @param channel channel number. See
	 *                {@link ChannelOptimizer#AVAILABLE_CHANNELS_BAND} for channels
	 *                in each band.
	 * @return the center frequency of the given channel in MHz
	 */
	public static int channelToFrequencyMHz(int channel) {
		if (
			ChannelOptimizer.AVAILABLE_CHANNELS_BAND
				.get(UCentralConstants.BAND_2G)
				.contains(channel)
		) {
			return 2407 + 5 * channel;
		} else if (
			ChannelOptimizer.AVAILABLE_CHANNELS_BAND
				.get(UCentralConstants.BAND_5G)
				.contains(channel)
		) {
			return 5000 + channel;
		} else {
			throw new IllegalArgumentException("Must provide a valid channel.");
		}
	}

	/**
	 * Determines if the given channel is in the given band.
	 *
	 * @param channel channel number
	 * @param band    "2G" or "5G"
	 * @return true if the given channel is in the given band; false otherwise
	 */
	public static boolean isChannelInBand(int channel, String band) {
		return LOWER_CHANNEL_LIMIT.get(band) <= channel &&
			channel <= UPPER_CHANNEL_LIMIT.get(band);
	}

	/**
	 * Given the channel, gets the band by checking lower bound and upper bound
	 * of each band
	 *
	 * @param channel channel number
	 * @return band if the channel can be mapped to a valid band; null otherwise
	 */
	public static String getBandFromChannel(int channel) {
		for (String band : UCentralConstants.BANDS) {
			if (isChannelInBand(channel, band)) {
				return band;
			}
		}
		return null;
	}
}
