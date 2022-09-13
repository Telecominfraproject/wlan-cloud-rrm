/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Measurement-based AP-AP TPC algorithm.
 *
 * TODO: implement the channel-specific TPC operation
 */
public class MeasurementBasedApApTPC extends TPC {
	private static final Logger logger =
		LoggerFactory.getLogger(MeasurementBasedApApTPC.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "measure_ap_ap";

	/**
	 * Default coverage threshold between APs, in dBm.
	 *
	 * This has been picked because various devices try to roam below this
	 * threshold. iOS devices try to roam to another device below -70dBm.
	 * Other devices roam below -75dBm or -80dBm, so a conservative threshold
	 * of -70dBm has been selected.
	 */
	public static final int DEFAULT_COVERAGE_THRESHOLD = -70;

	/**
	 * Default Nth smallest RSSI is used for Tx power calculation.
	 */
	public static final int DEFAULT_NTH_SMALLEST_RSSI = 0;

	/** coverage threshold between APs, in dB */
	private final int coverageThreshold;

	/** Nth smallest RSSI (zero-indexed) is used for Tx power calculation */
	private final int nthSmallestRssi; // TODO non-zero values untested

	/** Factory method to parse generic args map into the proper constructor */
	public static MeasurementBasedApApTPC makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		int coverageThreshold = DEFAULT_COVERAGE_THRESHOLD;
		int nthSmallestRssi = DEFAULT_NTH_SMALLEST_RSSI;

		String arg;
		if ((arg = args.get("coverageThreshold")) != null) {
			try {
				int parsedCoverageThreshold = Integer.parseInt(arg);
				if (parsedCoverageThreshold > 30) {
					logger.error(
						"Invalid value passed for coverageThreshold - must be less than 30. Using default value."
					);
				} else {
					coverageThreshold = parsedCoverageThreshold;
				}
			} catch (NumberFormatException e) {
				logger.error(
					"Invalid integer passed to parameter coverageThreshold, using default value",
					e
				);
			}
		}

		if ((arg = args.get("nthSmallestRssi")) != null) {
			try {
				int parsedNthSmallestRssi = Integer.parseInt(arg);
				if (parsedNthSmallestRssi < 0) {
					logger.error(
						"Invalid value passed for nthSmallestRssi - must be greater than 0. Using default value."
					);
				} else {
					nthSmallestRssi = parsedNthSmallestRssi;
				}
			} catch (NumberFormatException e) {
				logger.error(
					"Invalid integer passed to parameter nthSmallestRssi, using default value",
					e
				);
			}
		}

		return new MeasurementBasedApApTPC(
			model,
			zone,
			deviceDataManager,
			coverageThreshold,
			nthSmallestRssi
		);
	}

	/** Constructor. */
	public MeasurementBasedApApTPC(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this(
			model,
			zone,
			deviceDataManager,
			DEFAULT_COVERAGE_THRESHOLD,
			DEFAULT_NTH_SMALLEST_RSSI
		);
	}

	/** Constructor. */
	public MeasurementBasedApApTPC(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		int coverageThreshold,
		int nthSmallestRssi
	) {
		super(model, zone, deviceDataManager);

		if (coverageThreshold > MAX_TX_POWER) {
			throw new RuntimeException(
				"Invalid coverage threshold " + coverageThreshold
			);
		}
		this.coverageThreshold = coverageThreshold;
		this.nthSmallestRssi = nthSmallestRssi;
	}

	/**
	 * Retrieve BSSIDs of APs we are managing.
	 */
	protected static Set<String> getManagedBSSIDs(DataModel model) {
		Set<String> managedBSSIDs = new HashSet<>();
		for (Map.Entry<String, State> e : model.latestState.entrySet()) {
			State state = e.getValue();
			if (state.interfaces == null) {
				continue;
			}
			for (State.Interface iface : state.interfaces) {
				if (iface.ssids == null) {
					continue;
				}
				for (State.Interface.SSID ssid : iface.ssids) {
					if (ssid.bssid == null) {
						continue;
					}
					managedBSSIDs.add(ssid.bssid);
				}
			}
		}
		return managedBSSIDs;
	}

	/**
	 * Get the current band radio tx power (the first one found) for an AP using
	 * the latest device status.
	 *
	 * @param latestDeviceStatus JsonArray containing radio config for the AP
	 * @param band band (e.g., "2G")
	 * @return an Optional containing the tx power if one exists, or else an
	 *         empty Optional
	 */
	protected static Optional<Integer> getCurrentTxPower(
		JsonArray latestDeviceStatus,
		String band
	) {
		for (JsonElement e : latestDeviceStatus) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject radioObject = e.getAsJsonObject();
			String radioBand = radioObject.get("band").getAsString();
			if (radioBand.equals(band) && radioObject.has("tx-power")) {
				return Optional.of(radioObject.get("tx-power").getAsInt());
			}
		}
		return Optional.empty();
	}

	/**
	 * Get a map from BSSID to the received signal strength at neighboring APs (RSSI).
	 * List of RSSIs are returned in sorted, ascending order.
	 *
	 * If no neighboring APs have received signal from a source, then it gets an
	 * entry in the map with an empty list of RSSI values.
	 *
	 * @param managedBSSIDs set of all BSSIDs of APs we are managing
	 */

	/**
	 * Build a map from BSSID to a sorted (ascending) list of RSSIs from
	 * neighboring APs. Every managed BSSID is a key in the returned map; if
	 * that BSSID does not have an RSSI for that AP, the BSSID is mapped to an
	 * empty list.
	 *
	 * @param managedBSSIDs   set of all BSSIDs of APs we are managing
	 * @param latestWifiScans {@link DataModel#latestWifiScans} for data
	 *                        structure
	 * @param band            "2G" or "5G"
	 * @return a map from BSSID to a sorted (ascending) list of RSSIs from
	 *         neighboring APs.
	 */
	protected static Map<String, List<Integer>> buildRssiMap(
		Set<String> managedBSSIDs,
		Map<String, List<List<WifiScanEntry>>> latestWifiScans,
		String band
	) {
		Map<String, List<Integer>> bssidToRssiValues = new HashMap<>();
		managedBSSIDs.stream()
			.forEach(bssid -> bssidToRssiValues.put(bssid, new ArrayList<>()));

		for (
			Map.Entry<String, List<List<WifiScanEntry>>> e : latestWifiScans
				.entrySet()
		) {
			List<List<WifiScanEntry>> bufferedScans = e.getValue();
			List<WifiScanEntry> latestScan =
				bufferedScans.get(bufferedScans.size() - 1);

			// At a given AP, if we receive a signal from ap_2, then it gets added to the rssi list for ap_2
			latestScan.stream()
				.filter(entry -> (managedBSSIDs.contains(entry.bssid) && UCentralUtils.isChannelInBand(entry.channel, band)))
				.forEach(
					entry -> {
						bssidToRssiValues.get(entry.bssid).add(entry.signal);
					}
				);
		}
		bssidToRssiValues.values()
			.stream()
			.forEach(rssiList -> Collections.sort(rssiList));
		return bssidToRssiValues;
	}

	/**
	 * Compute adjusted tx power (dBm) based on inputs.
	 *
	 * @param serialNumber      serial number of the AP
	 * @param currentTxPower    the current tx power (dBm)
	 * @param rssiValues        sorted (ascending) list of RSSIs from neighboring
	 *                          APs
	 * @param coverageThreshold desired value for the {@code nthSmallestRssi}
	 * @param nthSmallestRssi   which RSSI to use to "calibrate" to determine the
	 *                          new tx power
	 * @return new tx power (dBm)
	 */
	protected static int computeTxPower(
		String serialNumber,
		int currentTxPower,
		List<Integer> rssiValues,
		int coverageThreshold,
		int nthSmallestRssi,
		List<Integer> txPowerChoices
	) {
		int maxTxPower = Collections.max(txPowerChoices);
		if (rssiValues.isEmpty()) {
			return maxTxPower;
		}
		int minTxPower = Collections.min(txPowerChoices);

		// We may not optimize for the closest AP, but the Nth closest
		int targetRSSI =
			rssiValues.get(Math.min(rssiValues.size() - 1, nthSmallestRssi));
		int txDelta = maxTxPower - currentTxPower;
		// Represents the highest possible RSSI to be received by that neighboring AP
		int estimatedRSSI = targetRSSI + txDelta;
		// this is the same as the following (easier to understand):
		// newTxPower = (coverageThreshold - targetRSSI) + currentTxPower
		int newTxPower = maxTxPower + coverageThreshold - estimatedRSSI;
		// Bound tx_power by [MIN_TX_POWER, MAX_TX_POWER]
		if (newTxPower > maxTxPower) {
			logger.info(
				"Device {}: computed tx power > maximum {}, using maximum",
				serialNumber,
				maxTxPower
			);
			newTxPower = maxTxPower;
		} else if (newTxPower < minTxPower) {
			logger.info(
				"Device {}: computed tx power < minimum {}, using minimum",
				serialNumber,
				minTxPower
			);
			newTxPower = minTxPower;
		}
		int closestTxPower = txPowerChoices.get(0);
		for (int txPowerChoice : txPowerChoices) {
			if (
				Math.abs(txPowerChoice - newTxPower) <
					Math.abs(closestTxPower - newTxPower)
			) {
				closestTxPower = newTxPower;
			}
		}
		return closestTxPower;
	}

	/**
	 * Calculate new tx powers for the given channel on the given APs .
	 *
	 * @param channel channel
	 * @param serialNumbers the serial numbers of the APs with the channel
	 * @param txPowerMap this maps from serial number to band to new tx power (dBm)
	 *                   and is updated by this method with the new tx powers.
	 */
	protected void buildTxPowerMapForChannel(
		int channel,
		List<String> serialNumbers,
		Map<String, Map<String, Integer>> txPowerMap
	) {
		String band = UCentralUtils.getBandFromChannel(channel);
		Set<String> managedBSSIDs = getManagedBSSIDs(model);
		Map<String, List<Integer>> bssidToRssiValues =
			buildRssiMap(managedBSSIDs, model.latestWifiScans, band);
		logger.debug("Starting TPC for the {} band", band);
		Map<String, JsonArray> allStatuses = model.latestDeviceStatus;
		for (String serialNumber : serialNumbers) {
			State state = model.latestState.get(serialNumber);
			if (
				state == null || state.radios == null ||
					state.radios.length == 0
			) {
				logger.debug(
					"Device {}: No radios found, skipping...",
					serialNumber
				);
				continue;
			}
			if (state.interfaces == null || state.interfaces.length == 0) {
				logger.debug(
					"Device {}: No interfaces found, skipping...",
					serialNumber
				);
				continue;
			}
			if (
				state.interfaces[0].ssids == null ||
					state.interfaces[0].ssids.length == 0
			) {
				logger.debug(
					"Device {}: No SSIDs found, skipping...",
					serialNumber
				);
				continue;
			}
			JsonArray radioStatuses =
				allStatuses.get(serialNumber).getAsJsonArray();
			Optional<Integer> possibleCurrentTxPower = getCurrentTxPower(
				radioStatuses,
				band
			);
			if (possibleCurrentTxPower.isEmpty()) {
				// this AP is not on the band of interest
				continue;
			}
			int currentTxPower = possibleCurrentTxPower.get();
			String bssid = state.interfaces[0].ssids[0].bssid;
			List<Integer> rssiValues = bssidToRssiValues.get(bssid);
			logger.debug("Device <{}> : BSSID <{}>", serialNumber, bssid);
			for (int rssi : rssiValues) {
				logger.debug("  Neighbor received RSSI: {}", rssi);
			}
			List<Integer> txPowerChoices = updateTxPowerChoices(
				band,
				serialNumber,
				DEFAULT_TX_POWER_CHOICES
			);
			int newTxPower = computeTxPower(
				serialNumber,
				currentTxPower,
				rssiValues,
				coverageThreshold,
				nthSmallestRssi,
				txPowerChoices
			);
			logger.debug("  Old tx_power: {}", currentTxPower);
			logger.debug("  New tx_power: {}", newTxPower);
			txPowerMap.computeIfAbsent(serialNumber, k -> new TreeMap<>())
				.put(band, newTxPower);
		}
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();
		Map<Integer, List<String>> apsPerChannel = getApsPerChannel();
		for (Map.Entry<Integer, List<String>> e : apsPerChannel.entrySet()) {
			buildTxPowerMapForChannel(e.getKey(), e.getValue(), txPowerMap);
		}
		return txPowerMap;
	}
}
