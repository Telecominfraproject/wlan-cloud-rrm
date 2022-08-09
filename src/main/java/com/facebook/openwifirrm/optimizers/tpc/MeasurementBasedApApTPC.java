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
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Measurement-based AP-AP TPC algorithm.
 *
 * TODO: support both 2G and 5G
 * TODO: implement the channel-specific TPC operation
 */
public class MeasurementBasedApApTPC extends TPC {
	private static final Logger logger = LoggerFactory.getLogger(MeasurementBasedApApTPC.class);

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

	/** Nth smallest RSSI is used for Tx power calculation */
	private final int nthSmallestRssi;

	/** Constructor. */
	public MeasurementBasedApApTPC(
		DataModel model, String zone, DeviceDataManager deviceDataManager
	) {
		this(model, zone, deviceDataManager, DEFAULT_COVERAGE_THRESHOLD, DEFAULT_NTH_SMALLEST_RSSI);
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
			throw new RuntimeException("Invalid coverage threshold " + coverageThreshold);
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
	 * Get the current band radio tx power (the first one found) for an AP using the
	 * latest device status.
	 *
	 * @param latestDeviceStatus JsonArray containing radio config for the AP
	 * @return the tx power, or 0 if none found
	 */
	protected static int getCurrentTxPower(JsonArray latestDeviceStatus, String band) {
		for (JsonElement e : latestDeviceStatus) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject radioObject = e.getAsJsonObject();
			String radioBand = radioObject.get("band").getAsString();
			if (radioBand.equals(band) && radioObject.has("tx-power")) {
				return radioObject.get("tx-power").getAsInt();
			}
		}
		return 0;
	}

	/**
	 *
	 * @param channel channel index (e.g., 36)
	 * @param band    "2G" or "5G"
	 * @return true if the given channel is in the given band
	 */
	protected static boolean isChannelInBand(int channel, String band) {
		return ChannelOptimizer.LOWER_CHANNEL_LIMIT.get(band) <= channel
				&& channel <= ChannelOptimizer.UPPER_CHANNEL_LIMIT.get(band);
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
	protected static Map<String, List<Integer>> buildRssiMap(
			Set<String> managedBSSIDs, Map<String, List<List<WifiScanEntry>>> latestWifiScans, String band
	) {
		Map<String, List<Integer>> bssidToRssiValues = new HashMap<>();
		managedBSSIDs.stream()
			.forEach(bssid -> bssidToRssiValues.put(bssid, new ArrayList<>()));

		for (Map.Entry<String, List<List<WifiScanEntry>>> e : latestWifiScans.entrySet()) {
			List<List<WifiScanEntry>> bufferedScans = e.getValue();
			List<WifiScanEntry> latestScan = bufferedScans.get(bufferedScans.size() - 1);

			// At a given AP, if we receive a signal from ap_2, then it gets added to the rssi list for ap_2
			latestScan.stream()
					.filter(entry -> (managedBSSIDs.contains(entry.bssid) && isChannelInBand(entry.channel, band)))
					.forEach(entry -> {
						bssidToRssiValues.get(entry.bssid).add(entry.signal);
					});
		}
		bssidToRssiValues.values().stream()
			.forEach(rssiList -> Collections.sort(rssiList));
		return bssidToRssiValues;
	}

	/**
	 * Compute adjusted tx power (dBm) based on inputs.
	 * @param currentTxPower the current tx power (dBm)
	 * @param rssiValues RSSI values received by managed neighboring APs in ascending order
	 */
	protected static int computeTxPower(
		String serialNumber,
		int currentTxPower,
		List<Integer> rssiValues,
		int coverageThreshold,
		int nthSmallestRssi
	) {
		if (rssiValues.isEmpty()) {
			return MAX_TX_POWER;
		}

		// We may not optimize for the closest AP, but the Nth closest
		int targetRSSI = rssiValues.get(Math.min(rssiValues.size() - 1, nthSmallestRssi));
		int txDelta = MAX_TX_POWER - currentTxPower;
		// Represents the highest possible RSSI to be received by that neighboring AP
		int estimatedRSSI = targetRSSI + txDelta;
		int newTxPower = MAX_TX_POWER + coverageThreshold - estimatedRSSI;
		// Bound tx_power by [MIN_TX_POWER, MAX_TX_POWER]
		if (newTxPower > MAX_TX_POWER) {
			logger.info(
				"Device {}: computed tx power > maximum {}, using maximum",
				serialNumber,
				MAX_TX_POWER
			);
			newTxPower = MAX_TX_POWER;
		} else if (newTxPower < MIN_TX_POWER) {
			logger.info(
				"Device {}: computed tx power < minimum {}, using minimum",
				serialNumber,
				MIN_TX_POWER
			);
			newTxPower = MIN_TX_POWER;
		}
		return newTxPower;
	}

	protected void buildTxPowerMapForBand(String band, Map<String, Map<String, Integer>> txPowerMap) {
		Set<String> managedBSSIDs = getManagedBSSIDs(model);
		Map<String, List<Integer>> bssidToRssiValues = buildRssiMap(managedBSSIDs, model.latestWifiScans, band);
		logger.debug("Starting {}", band);
		Map<String, JsonArray> allStatuses = model.latestDeviceStatus;
		for (String serialNumber : allStatuses.keySet()) {
			State state = model.latestState.get(serialNumber);
			if (state == null || state.radios == null || state.radios.length == 0) {
				logger.debug(
					"Device {}: No radios found, skipping...", serialNumber
				);
				continue;
			}
			if (state.interfaces == null || state.interfaces.length == 0) {
				logger.debug(
					"Device {}: No interfaces found, skipping...", serialNumber
				);
				continue;
			}
			if (state.interfaces[0].ssids == null || state.interfaces[0].ssids.length == 0) {
				logger.debug(
					"Device {}: No SSIDs found, skipping...", serialNumber
				);
				continue;
			}
			JsonArray radioStatuses = allStatuses.get(serialNumber).getAsJsonArray();
			int currentTxPower = getCurrentTxPower(radioStatuses, band);
			String bssid = state.interfaces[0].ssids[0].bssid;
			List<Integer> rssiValues = bssidToRssiValues.get(bssid);
			logger.debug("Device <{}> : BSSID <{}>", serialNumber, bssid);
			for (int rssi : rssiValues) {
				logger.debug("  Neighbor received RSSI: {}", rssi);
			}
			int newTxPower = computeTxPower(
				serialNumber,
				currentTxPower,
				rssiValues,
				coverageThreshold,
				nthSmallestRssi
			);
			logger.debug("  New tx_power: {}", newTxPower);

			Map<String, Integer> radioMap = new TreeMap<>();
			radioMap.put(band, newTxPower);
			txPowerMap.put(serialNumber, radioMap);
		}
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();
		buildTxPowerMapForBand(UCentralConstants.BAND_2G, txPowerMap);
		buildTxPowerMapForBand(UCentralConstants.BAND_5G, txPowerMap);
		return txPowerMap;
	}
}
