/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.Map;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.ConfigManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;

/**
 * TPC (Transmit Power Control) base class.
 */
public abstract class TPC {
	/** Minimum supported tx power (dBm), inclusive. */
	public static final int MIN_TX_POWER = 0;

	/** Maximum supported tx power (dBm), inclusive. */
	public static final int MAX_TX_POWER = 30;

	/** The input data model. */
	protected final DataModel model;

	/** The RF zone. */
	protected final String zone;

	/** The device configs within {@link #zone}, keyed on serial number. */
	protected final Map<String, DeviceConfig> deviceConfigs;

	/** Constructor. */
	public TPC(
		DataModel model, String zone, DeviceDataManager deviceDataManager
	) {
		this.model = model;
		this.zone = zone;
		this.deviceConfigs = deviceDataManager.getAllDeviceConfigs(zone);

		// TODO!! Actually use device configs (allowedTxPowers, userTxPowers)

		// Remove model entries not in the given zone
		this.model.latestWifiScans.keySet().removeIf(serialNumber ->
			!deviceConfigs.containsKey(serialNumber)
		);
		this.model.latestState.keySet().removeIf(serialNumber ->
			!deviceConfigs.containsKey(serialNumber)
		);
		this.model.latestDeviceStatus.keySet().removeIf(serialNumber ->
			!deviceConfigs.containsKey(serialNumber)
		);
		this.model.latestDeviceCapabilities.keySet().removeIf(serialNumber ->
			!deviceConfigs.containsKey(serialNumber)
		);
	}

	/**
	 * Compute tx power assignments.
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
				Map.Entry<String, Map<String, Integer>> entry :
				txPowerMap.entrySet()
			) {
				DeviceConfig deviceConfig = apConfig.computeIfAbsent(
					entry.getKey(), k -> new DeviceConfig()
				);
				deviceConfig.autoTxPowers = entry.getValue();
			}
		});

		// Trigger config update now
		configManager.wakeUp();
	}
}
