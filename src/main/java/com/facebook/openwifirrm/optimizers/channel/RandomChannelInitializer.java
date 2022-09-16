/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.channel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;

/**
 * Random channel initializer.
 * <p>
 * Randomly assign APs to the same channel unless otherwise specified.
 * If specified, all APs will be assigned a random channel.
 */
public class RandomChannelInitializer extends ChannelOptimizer {
	private static final Logger logger =
		LoggerFactory.getLogger(RandomChannelInitializer.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "random";

	/** Default value for setDifferentChannelPerAp */
	public static final boolean DEFAULT_SET_DIFFERENT_CHANNEL_PER_AP = false;

	/** The PRNG instance. */
	private final Random rng;

	/** Whether to set a different value per AP or use a single value for all APs */
	private final boolean setDifferentChannelPerAp;

	/** Factory method to parse generic args map into the proper constructor */
	public static RandomChannelInitializer makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		boolean setDifferentChannelPerAp = DEFAULT_SET_DIFFERENT_CHANNEL_PER_AP;

		String arg;
		if ((arg = args.get("setDifferentChannelPerAp")) != null) {
			setDifferentChannelPerAp = Boolean.parseBoolean(arg);
		}

		return new RandomChannelInitializer(
			model,
			zone,
			deviceDataManager,
			setDifferentChannelPerAp
		);
	}

	/** Constructor. */
	public RandomChannelInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this(
			model,
			zone,
			deviceDataManager,
			DEFAULT_SET_DIFFERENT_CHANNEL_PER_AP
		);
	}

	/** Constructor (allows setting different channel per AP) */
	public RandomChannelInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		boolean setDifferentChannelPerAp
	) {
		this(
			model,
			zone,
			deviceDataManager,
			setDifferentChannelPerAp,
			new Random()
		);
	}

	/**
	 * Constructor (allows setting different channel per AP and passing
	 * in a custom Random class to allow seeding)
	 */
	public RandomChannelInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		boolean setDifferentChannelPerAp,
		Random rng
	) {
		super(model, zone, deviceDataManager);
		this.setDifferentChannelPerAp = setDifferentChannelPerAp;
		this.rng = rng;
	}

	@Override
	public Map<String, Map<String, Integer>> computeChannelMap() {
		Map<String, Map<String, Integer>> channelMap = new TreeMap<>();
		Map<String, List<String>> bandsMap =
			UCentralUtils.getBandsMap(model.latestDeviceStatusRadios);

		Map<String, Map<String, List<Integer>>> deviceAvailableChannels =
			UCentralUtils.getDeviceAvailableChannels(
				model.latestDeviceStatusRadios,
				model.latestDeviceCapabilities,
				AVAILABLE_CHANNELS_BAND
			);

		Map<String, String> bssidsMap =
			UCentralUtils.getBssidsMap(model.latestState);

		for (Map.Entry<String, List<String>> entry : bandsMap.entrySet()) {
			// Performance metrics
			Map<String, Integer> oldChannelMap = new TreeMap<>();
			Map<String, Integer> newChannelMap = new TreeMap<>();

			// Use last wifi scan result for the performance metrics calculation
			String band = entry.getKey();
			Map<String, List<WifiScanEntry>> deviceToWifiScans =
				getDeviceToWiFiScans(
					band,
					model.latestWifiScans,
					bandsMap
				);

			// Get the common available channels for all the devices
			// to get the valid result for single channel assignment
			// If the intersection is empty, then turn back to the default channels list
			List<Integer> availableChannelsList = new ArrayList<>(
				AVAILABLE_CHANNELS_BAND.get(band)
			);
			for (String serialNumber : entry.getValue()) {
				List<Integer> deviceChannelsList = deviceAvailableChannels
					.get(band)
					.get(serialNumber);
				if (
					deviceChannelsList == null || deviceChannelsList.isEmpty()
				) {
					deviceChannelsList = AVAILABLE_CHANNELS_BAND.get(band);
				}
				availableChannelsList.retainAll(deviceChannelsList);
			}
			if (
				availableChannelsList == null || availableChannelsList.isEmpty()
			) {
				availableChannelsList = AVAILABLE_CHANNELS_BAND.get(band);
				logger.debug(
					"The intersection of the device channels lists is empty!!! " +
						"Fall back to the default channels list"
				);
			}

			// Randomly assign all the devices to the same channel if
			// setDifferentChannelPerAp is false otherwise, assigns
			// each device to a random channel
			int defaultChannelIndex = rng.nextInt(availableChannelsList.size());

			for (String serialNumber : entry.getValue()) {
				int newChannel = availableChannelsList.get(
					this.setDifferentChannelPerAp
						? rng.nextInt(availableChannelsList.size()) : defaultChannelIndex
				);

				State state = model.latestState.get(serialNumber);
				if (state == null) {
					logger.debug(
						"Device {}: No state found, skipping...",
						serialNumber
					);
					continue;
				}
				if (state.radios == null || state.radios.length == 0) {
					logger.debug(
						"Device {}: No radios found, skipping...",
						serialNumber
					);
					continue;
				}
				int[] currentChannelInfo =
					getCurrentChannel(band, serialNumber, state);
				int currentChannel = currentChannelInfo[0];
				int currentChannelWidth = currentChannelInfo[1];
				if (currentChannel == 0) {
					// Filter out APs if the number of radios in the state and config mismatches
					// Happen when an AP's radio is enabled/disabled on the fly
					logger.debug(
						"Device {}: No {} radio, skipping...",
						serialNumber,
						band
					);
					continue;
				}

				// Log the notice when the updated one and the original one are not equal
				List<Integer> newAvailableChannelsList =
					updateAvailableChannelsList(
						band,
						serialNumber,
						currentChannelWidth,
						availableChannelsList
					);
				Set<Integer> availableChannelsSet = new TreeSet<>(
					availableChannelsList
				);
				Set<Integer> newAvailableChannelsSet = new TreeSet<>(
					newAvailableChannelsList
				);
				if (!availableChannelsSet.equals(newAvailableChannelsSet)) {
					logger.info(
						"Device {}: userChannels/allowedChannels are disabled in " +
							"single channel assignment.",
						serialNumber
					);
				}

				channelMap.computeIfAbsent(
					serialNumber,
					k -> new TreeMap<>()
				)
					.put(band, newChannel);
				logger.info(
					"Device {}: Assigning to random free channel {} (from " +
						"available list: {})",
					serialNumber,
					newChannel,
					availableChannelsList.toString()
				);

				// Gather the info for the performance metrics
				oldChannelMap.put(serialNumber, currentChannel);
				newChannelMap.put(serialNumber, newChannel);
			}
			// Get and log the performance metrics
			logPerfMetrics(
				oldChannelMap,
				newChannelMap,
				deviceToWifiScans,
				bssidsMap
			);
		}

		return channelMap;
	}
}
