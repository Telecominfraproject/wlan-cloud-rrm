/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.cloudsdk.ies.Country;
import com.facebook.openwifi.cloudsdk.ies.LocalPowerConstraint;
import com.facebook.openwifi.cloudsdk.ies.QbssLoad;
import com.facebook.openwifi.cloudsdk.ies.TxPwrInfo;
import com.facebook.openwifi.cloudsdk.models.ap.Capabilities;
import com.facebook.openwifi.cloudsdk.models.ap.State;
import com.facebook.openwifi.cloudsdk.models.gw.CommandInfo;
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

	/** Information Element (IE) content field key */
	private static final String IE_CONTENT_FIELD_KEY = "content";

	/** The Gson instance. */
	private static final Gson gson = new Gson();

	/** Map from band to ordered (increasing) list of available channels */
	public static final Map<String, List<Integer>> AVAILABLE_CHANNELS_BAND =
		Collections
			.unmodifiableMap(buildBandToChannelsMap());

	// This class should not be instantiated.
	private UCentralUtils() {}

	/**
	 * Builds map from band to ordered (increasing) list of available channels.
	 */
	private static Map<String, List<Integer>> buildBandToChannelsMap() {
		Map<String, List<Integer>> bandToChannelsMap = new HashMap<>();
		bandToChannelsMap.put(
			UCentralConstants.BAND_5G,
			Collections.unmodifiableList(
				Arrays.asList(36, 40, 44, 48, 149, 153, 157, 161, 165)
			)
		);
		// NOTE: later, we may want to support channels 12, 13, and/or 14, if
		// the AP supports it and OWF vendors will use them
		bandToChannelsMap.put(
			UCentralConstants.BAND_2G,
			Collections.unmodifiableList(
				Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
			)
		);
		return bandToChannelsMap;
	}

	/** Return the lowest available channel for the given band */
	public static int getLowerChannelLimit(String band) {
		return AVAILABLE_CHANNELS_BAND.get(band).get(0);
	}

	/** Return the lowest available channel for the given band */
	public static int getUpperChannelLimit(String band) {
		List<Integer> channels = AVAILABLE_CHANNELS_BAND.get(band);
		return channels.get(channels.size() - 1);
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
				extractIEs(e, entry);
				entries.add(entry);
			}
		} catch (Exception e) {
			logger.debug("Exception when parsing wifiscan entries", e);
			return null;
		}
		return entries;
	}

	/**
	 * Extract desired information elements (IEs) from the wifiscan entry.
	 * Modifies {@code entry} argument. Skips invalid IEs (IEs with missing
	 * fields).
	 */
	private static void extractIEs(
		JsonElement entryJsonElement,
		WifiScanEntry entry
	) {
		JsonElement iesJsonElement =
			entryJsonElement.getAsJsonObject().get("ies");
		if (iesJsonElement == null) {
			logger.debug("Wifiscan entry does not contain 'ies' field.");
			return;
		}
		JsonArray iesJsonArray = iesJsonElement.getAsJsonArray();
		InformationElements ieContainer = new InformationElements();
		for (JsonElement ieJsonElement : iesJsonArray) {
			JsonElement typeElement =
				ieJsonElement.getAsJsonObject().get("type");
			if (typeElement == null) { // shouldn't happen
				continue;
			}
			if (!ieJsonElement.isJsonObject()) {
				// the IEs we are interested in are Json objects
				continue;
			}
			JsonObject ie = ieJsonElement.getAsJsonObject();
			JsonElement contentsJsonElement = ie.get(IE_CONTENT_FIELD_KEY);
			if (
				contentsJsonElement == null ||
					!contentsJsonElement.isJsonObject()
			) {
				continue;
			}
			JsonObject contents = contentsJsonElement.getAsJsonObject();
			try {
				switch (typeElement.getAsInt()) {
				case Country.TYPE:
					ieContainer.country = Country.parse(contents);
					break;
				case QbssLoad.TYPE:
					ieContainer.qbssLoad = QbssLoad.parse(contents);
					break;
				case LocalPowerConstraint.TYPE:
					ieContainer.localPowerConstraint =
						LocalPowerConstraint.parse(contents);
					break;
				case TxPwrInfo.TYPE:
					ieContainer.txPwrInfo = TxPwrInfo.parse(contents);
					break;
				}
			} catch (Exception e) {
				logger.error(String.format("Skipping invalid IE %s", ie), e);
				continue;
			}
		}
		entry.ieContainer = ieContainer;
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

			// Compare vs. existing value.
			// not all values are int so override those values
			Integer currentValue = null;
			JsonElement fieldValue = radioConfig.get(fieldName);
			if (
				fieldValue.isJsonPrimitive() &&
					fieldValue.getAsJsonPrimitive().isNumber()
			) {
				currentValue = fieldValue.getAsInt();
			} else {
				logger.debug(
					"Unable to get field '{}' as int, value was {}",
					fieldName,
					fieldValue.toString()
				);
			}

			if (currentValue != null && currentValue == newValue) {
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
					currentValue != null ? currentValue : fieldValue.toString()
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
		Map<String, Map<String, Capabilities.Phy>> deviceCapabilities,
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

				Map<String, Capabilities.Phy> capabilitiesPhyMap =
					deviceCapabilities.get(serialNumber);
				List<Integer> availableChannels = new ArrayList<>();
				if (capabilitiesPhyMap == null) {
					availableChannels
						.addAll(defaultAvailableChannels.get(band));
				} else {
					Set<Entry<String, Capabilities.Phy>> entrySet =
						capabilitiesPhyMap
							.entrySet();
					for (Map.Entry<String, Capabilities.Phy> f : entrySet) {
						Capabilities.Phy phy = f.getValue();
						String bandInsideObject = phy.band.toString();
						if (bandInsideObject.equals(band)) {
							// (TODO) Remove the following dfsChannels code block
							// when the DFS channels are available
							Set<Integer> dfsChannels = new HashSet<>();
							try {
								int[] channelInfo = phy.dfs_channels;
								for (int d : channelInfo) {
									dfsChannels.add(d);
								}
							} catch (Exception d) {}
							try {
								int[] channelInfo = phy.channels;
								for (int channel : channelInfo) {
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
		Map<String, ? extends State> latestState
	) {
		Map<String, String> bssidMap = new HashMap<>();
		for (Entry<String, ? extends State> e : latestState.entrySet()) {
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

	/**
	 * Determines if the given channel is in the given band.
	 *
	 * @param channel channel number
	 * @param band    "2G" or "5G"
	 * @return true if the given channel is in the given band; false otherwise
	 */
	public static boolean isChannelInBand(int channel, String band) {
		return AVAILABLE_CHANNELS_BAND.get(band).contains(channel);
	}

	/** Return which band contains the given frequency (MHz). */
	public static String freqToBand(int freqMHz) {
		if (2412 <= freqMHz && freqMHz <= 2484) {
			return "2G";
		} else {
			return "5G";
		}
	}

	/**
	 * Tries to parse channel width, if it encounters an error it will return null.
	 * It can handle 80p80 in two ways. First it can just treat it as 160. Second,
	 * it can just apply to the first 80 channel and ignore the second. This is
	 * controlled by treatSeparate.
	 *
	 * @param channelWidthStr the channel width
	 * @param treatSeparate treats each band separately
	 * @return channel width in MHz
	 */
	public static Integer parseChannelWidth(
		String channelWidthStr,
		boolean treatSeparate
	) {
		// 80p80 is the only case where it can't be parsed into an integer
		if (channelWidthStr.equals("80p80")) {
			return treatSeparate ? 80 : 160;
		}
		try {
			return Integer.parseInt(channelWidthStr);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Tries to parse the index from the reference string in the JSON returned from
	 * other services. Note that this only returns the index, the caller is
	 * responsible for making sure that the correct field is passed in and the
	 * index is used in the correct fields. If there's an error parsing, it will
	 * return null.
	 *
	 * @param reference The reference string, keyed under "$ref"
	 * @return the index of the reference or null if an error occurred.
	 */
	public static Integer parseReferenceIndex(String reference) {
		try {
			return Integer.parseInt(
				reference,
				reference.lastIndexOf("/") + 1,
				reference.length(),
				10
			);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * Return a map of Wi-Fi client (STA) MAC addresses to the Client structure
	 * found for that interface. This does NOT support clients connected on
	 * multiple interfaces simultaneously.
	 */
	public static Map<String, State.Interface.Client> getWifiClientInfo(
		State state
	) {
		Map<String, State.Interface.Client> ret = new HashMap<>();

		// Aggregate over all interfaces
		for (State.Interface iface : state.interfaces) {
			if (iface.ssids == null || iface.clients == null) {
				continue;
			}

			// Convert client array to map (for faster lookups)
			Map<String, State.Interface.Client> ifaceMap = new HashMap<>();
			for (State.Interface.Client client : iface.clients) {
				ifaceMap.put(client.mac, client);
			}

			// Loop over all SSIDs and connected clients
			for (State.Interface.SSID ssid : iface.ssids) {
				if (ssid.associations == null) {
					continue;
				}
				for (
					State.Interface.SSID.Association association : ssid.associations
				) {
					State.Interface.Client client =
						ifaceMap.get(association.station);
					if (client != null) {
						ret.put(association.station, client);
					}
				}
			}
		}

		return ret;
	}

	/**
	 * Decompress (inflate) a UTF-8 string using ZLIB.
	 *
	 * @param compressed the compressed string
	 * @param uncompressedSize the uncompressed size (must be known)
	 */
	private static String inflate(String compressed, int uncompressedSize)
		throws DataFormatException {
		if (compressed == null) {
			throw new NullPointerException("Null compressed string");
		}
		if (uncompressedSize < 0) {
			throw new IllegalArgumentException("Invalid size");
		}

		byte[] input = compressed.getBytes(StandardCharsets.UTF_8);
		byte[] output = new byte[uncompressedSize];

		Inflater inflater = new Inflater();
		inflater.setInput(input, 0, input.length);
		inflater.inflate(output);
		inflater.end();

		return new String(output, StandardCharsets.UTF_8);
	}

	/**
	 * Given the result of the "script" API, return the actual script output
	 * (decoded/decompressed if needed), or null if the script returned an
	 * error.
	 *
	 * @see UCentralClient#runScript(String, String, int, String)
	 */
	public static String getScriptOutput(CommandInfo info) {
		if (info == null || info.results == null) {
			return null;
		}
		if (!info.results.has("status")) {
			return null;
		}
		JsonObject status = info.results.get("status").getAsJsonObject();
		if (!status.has("error")) {
			return null;
		}
		int errorCode = status.get("error").getAsInt();
		if (errorCode != 0) {
			logger.error("Script failed with code {}", errorCode);
			return null;
		}
		if (status.has("result")) {
			// Raw result
			return status.get("result").getAsString();
		} else if (status.has("result_64") && status.has("result_sz")) {
			// Base64+compressed result
			// NOTE: untested, not actually implemented on ucentral-client?
			try {
				String encoded = status.get("result_64").getAsString();
				int uncompressedSize = status.get("result_sz").getAsInt();
				String decoded = new String(
					Base64.getDecoder().decode(encoded),
					StandardCharsets.UTF_8
				);
				return inflate(decoded, uncompressedSize);
			} catch (Exception e) {
				logger.error("Failed to decode or inflate script result", e);
			}
		}
		return null;
	}
}
