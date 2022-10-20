/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.tpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.cloudsdk.models.ap.Capabilities;
import com.facebook.openwifi.cloudsdk.models.ap.State;
import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.modules.ConfigManager;
import com.facebook.openwifi.rrm.modules.Modeler.DataModel;
import com.facebook.openwifi.rrm.modules.ModelerUtils;

/**
 * TPC (Transmit Power Control) base class.
 */
public abstract class TPC {
	private static final Logger logger = LoggerFactory.getLogger(TPC.class);

	/** Minimum supported tx power (dBm), inclusive. */
	public static final int MIN_TX_POWER = 0;

	/** Maximum supported tx power (dBm), inclusive. */
	public static final int MAX_TX_POWER = 30;

	/** Default tx power choices. */
	public static final List<Integer> DEFAULT_TX_POWER_CHOICES =
		Collections.unmodifiableList(
			IntStream
				.rangeClosed(MIN_TX_POWER, MAX_TX_POWER)
				.boxed()
				.collect(Collectors.toList())
		);

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
		this.model.latestStates.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
		this.model.latestDeviceStatusRadios.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
		this.model.latestDeviceCapabilitiesPhy.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber)
			);
	}

	/**
	 * Determine the new tx power choices based on user and allowed channels from deviceConfig.
	 *
	 * @param band the operational band
	 * @param serialNumber the device's serial number
	 * @param txPowerChoices the device's available tx powers
	 * @return the device's updated tx powers
	 */
	protected List<Integer> updateTxPowerChoices(
		String band,
		String serialNumber,
		List<Integer> txPowerChoices
	) {
		List<Integer> newTxPowerChoices = new ArrayList<>(txPowerChoices);

		// Update the available tx powers based on user tx powers or allowed tx powers
		DeviceConfig deviceCfg = deviceConfigs.get(serialNumber);
		if (deviceCfg == null) {
			return newTxPowerChoices;
		}
		if (
			deviceCfg.userTxPowers != null &&
				deviceCfg.userTxPowers.get(band) != null
		) {
			newTxPowerChoices = Arrays.asList(
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
			newTxPowerChoices.retainAll(allowedTxPowers);
		}

		// If newTxPowerChoices is empty, use default available tx powers list
		if (newTxPowerChoices.isEmpty()) {
			logger.debug(
				"Device {}: the updated availableTxPowersList is empty!!! " +
					"userTxPowers or allowedTxPowers might be invalid " +
					"Fall back to the default available tx powers list"
			);
			newTxPowerChoices = new ArrayList<>(DEFAULT_TX_POWER_CHOICES);
		}
		logger.debug(
			"Device {}: the updated availableTxPowersList is {}",
			serialNumber,
			newTxPowerChoices
		);
		return newTxPowerChoices;
	}

	/**
	 * Compute tx power assignments. This is the core method of this class.
	 *
	 * @return the map of devices (by serial number) to radio to tx power
	 */
	public abstract Map<String, Map<String, Integer>> computeTxPowerMap();

	/**
	 * Program the given tx power map into the AP config.
	 *
	 * @param deviceDataManager the DeviceDataManager instance
	 * @param configManager the ConfigManager instance
	 * @param txPowerMap the map of devices (by serial number) to radio to tx power
	 */
	public void updateDeviceApConfig(
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
	}

	/**
	 * Get AP serial numbers per channel.
	 *
	 * @return map from band to channel to list of AP serial numbers
	 */
	protected Map<String, Map<Integer, List<String>>> getApsPerChannel() {
		Map<String, Map<Integer, List<String>>> apsPerChannel = new TreeMap<>();
		for (Map.Entry<String, List<State>> e : model.latestStates.entrySet()) {
			String serialNumber = e.getKey();
			List<State> states = e.getValue();
			State state = states.get(states.size() - 1);

			if (state.radios == null || state.radios.length == 0) {
				logger.debug(
					"Device {}: No radios found, skipping...",
					serialNumber
				);
				continue;
			}

			for (State.Radio radio : state.radios) {
				Integer currentChannel = radio.channel;
				if (currentChannel == 0) {
					continue;
				}
				Map<String, Capabilities.Phy> capabilitiesPhy =
					model.latestDeviceCapabilitiesPhy.get(serialNumber);
				if (capabilitiesPhy == null) {
					continue;
				}
				final String band = ModelerUtils.getBand(
					radio,
					capabilitiesPhy
				);
				if (band == null) {
					continue;
				}
				apsPerChannel
					.computeIfAbsent(band, k -> new TreeMap<>())
					.computeIfAbsent(currentChannel, k -> new ArrayList<>())
					.add(serialNumber);
			}
		}
		return apsPerChannel;
	}
}
