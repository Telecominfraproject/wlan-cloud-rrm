/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.ConfigManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TPC (Transmit Power Control) base class.
 */
public abstract class TPC {
	private static final Logger logger = LoggerFactory.getLogger(TPC.class);

	/** Minimum supported tx power (dBm), inclusive. */
	public static final int MIN_TX_POWER = 0;

	/** Maximum supported tx power (dBm), inclusive. */
	public static final int MAX_TX_POWER = 30;

	public static final List<Integer> DEFAULT_TX_POWERS_LIST = IntStream
		.rangeClosed(MIN_TX_POWER, MAX_TX_POWER)
		.boxed()
		.collect(Collectors.toList());

	/** The input data model. */
	protected final DataModel model;

	/** The RF zone. */
	protected final String zone;

	/** The device configs within {@link #zone}, keyed on serial number. */
	protected final Map<String, DeviceConfig> deviceConfigs;

	/** Constructor. */
	public TPC(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this.model = model;
		this.zone = zone;
		this.deviceConfigs = deviceDataManager.getAllDeviceConfigs(zone);

		// Remove model entries not in the given zone
		this.model.latestWifiScans.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
		this.model.latestState.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
		this.model.latestDeviceStatus.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
		this.model.latestDeviceCapabilities.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
	}

	/**
	 * Get the available tx powers based on user and allowed channels from deviceConfig
	 * @param band the operational band
	 * @param serialNumber the device
	 * @return the available tx powers of the device
	 */
	protected List<Integer> getAvailableTxPowersList(
		String band,
		String serialNumber
	) {
		List<Integer> newAvailableTxPowersList =
			new ArrayList<>(DEFAULT_TX_POWERS_LIST);

		// Update the available tx powers based on user tx powers or allowed tx powers
		DeviceConfig deviceCfg = deviceConfigs.get(serialNumber);
		if (deviceCfg == null) {
			return newAvailableTxPowersList;
		}
		if (
			deviceCfg.userTxPowers != null &&
				deviceCfg.userTxPowers.get(band) != null
		) {
			newAvailableTxPowersList = Arrays.asList(
				deviceCfg.userTxPowers.get(band)
			);
			logger.debug(
				"Device {}: userTxPowers {}",
				serialNumber,
				deviceCfg.userTxPowers.get(band)
			);
		} else if (
			deviceCfg.allowedTxPowers != null &&
				deviceCfg.allowedTxPowers.get(band) != null
		) {
			List<Integer> allowedTxPowers = deviceCfg.allowedTxPowers.get(band);
			logger.debug(
				"Device {}: allowedTxPowers {}",
				serialNumber,
				allowedTxPowers
			);
			newAvailableTxPowersList.retainAll(allowedTxPowers);
		}

		// If the intersection of the above steps gives an empty list,
		// turn back to use the default available tx powers list
		if (newAvailableTxPowersList.isEmpty()) {
			logger.debug(
				"Device {}: the updated availableTxPowersList is empty!!! " +
					"userTxPowers or allowedTxPowers might be invalid " +
					"Fall back to the default available tx powers list"
			);
			newAvailableTxPowersList = new ArrayList<>(DEFAULT_TX_POWERS_LIST);
		}
		logger.debug(
			"Device {}: the updated availableTxPowersList is {}",
			serialNumber,
			newAvailableTxPowersList
		);
		return newAvailableTxPowersList;
	}

	/**
	 * Compute tx power assignments. This is the core method of this class.
	 *
	 * @return the map of devices (by serial number) to radio to tx power
	 */
	public abstract Map<String, Map<String, Integer>> computeTxPowerMap();

	/**
	 * Program the given tx power map into the AP config and notify the config
	 * manager.
	 *
	 * @param deviceDataManager the DeviceDataManager instance
	 * @param configManager the ConfigManager instance
	 * @param txPowerMap the map of devices (by serial number) to radio to tx power
	 */
	public void applyConfig(
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Map<String, Map<String, Integer>> txPowerMap
	) {
		// Update device AP config layer
		deviceDataManager.updateDeviceApConfig(apConfig -> {
			for (
				Map.Entry<String, Map<String, Integer>> entry : txPowerMap
					.entrySet()
			) {
				DeviceConfig deviceConfig = apConfig.computeIfAbsent(
					entry.getKey(),
					k -> new DeviceConfig()
				);
				deviceConfig.autoTxPowers = entry.getValue();
			}
		});

		// Trigger config update now
		configManager.wakeUp();
	}
}
