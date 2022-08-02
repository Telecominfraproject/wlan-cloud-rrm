/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.Constants;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.ProcessedWifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;

/**
 * Least used channel optimizer.
 * <p>
 * Randomly assign APs to the least loaded channels.
 */
public class LeastUsedChannelOptimizer extends ChannelOptimizer {
	private static final Logger logger = LoggerFactory.getLogger(LeastUsedChannelOptimizer.class);

	/** The window size for overlapping channels. */
	protected static final int OVERLAP_WINDOW = 4;

	/** The PRNG instance. */
	protected final Random rng = new Random();

	/** Constructor. */
	public LeastUsedChannelOptimizer(
		DataModel model, String zone, DeviceDataManager deviceDataManager
	) {
		super(model, zone, deviceDataManager);
	}

	/**
	 * Get the sorted APs list to determine the visit ordering.
	 * @param deviceToWifiScans the filtered and reorganized wifiscan results
	 * @return list of the name of the sorted APs
	 */
	protected static List<String> getSortedAPs(
		Map<String, List<ProcessedWifiScanEntry>> deviceToWifiScans
	) {
		return deviceToWifiScans.entrySet()
			.stream()
			.sorted(
				(e1, e2) ->
				Integer.compare(e2.getValue().size(), e1.getValue().size())
			)
			.map(e -> e.getKey())
			.collect(Collectors.toList());
	}

	/**
	 * Update the occupied channel info to include the overlapping channels (for 2.4G).
	 * @param occupiedChannels the current occupied channels info of the device
	 * @return map of channel to score/weight
	 */
	protected static Map<Integer, Integer> getOccupiedOverlapChannels(
		Map<Integer, Integer> occupiedChannels
	) {
		int maxChannel = UPPER_CHANNEL_LIMIT.get(Constants.BAND_2G);
		int minChannel = LOWER_CHANNEL_LIMIT.get(Constants.BAND_2G);
		Map<Integer, Integer> occupiedOverlapChannels = new TreeMap<>();
		for (int overlapChannel : AVAILABLE_CHANNELS_BAND.get(Constants.BAND_2G)) {
			int occupancy = 0;
			int windowStart = Math.max(
				minChannel,
				overlapChannel - OVERLAP_WINDOW
			);
			int windowEnd = Math.min(
				maxChannel,
				overlapChannel + OVERLAP_WINDOW
			);
			for (int i = windowStart; i <= windowEnd; i++) {
				// Sum up # STAs/APs for a channel within a window
				occupancy += occupiedChannels.getOrDefault(i, 0);
			}
			if (occupancy != 0) {
				occupiedOverlapChannels.put(overlapChannel, occupancy);
			}
		}
		return occupiedOverlapChannels;
	}

	/**
	 * Get the wifiscan results based on the bandwidth info
	 * @param band the operational band
	 * @param serialNumber the device
	 * @param channelWidth the channel bandwidth (MHz)
	 * @param deviceToWifiScans the filtered and reorganized wifiscan results
	 * @return the wifiscan results on the bandwidth-specific primary channels
	 */
	protected List<ProcessedWifiScanEntry> getScanRespsByBandwidth(
		String band,
		String serialNumber,
		int channelWidth,
		Map<String, List<ProcessedWifiScanEntry>> deviceToWifiScans
	) {
		List<ProcessedWifiScanEntry> scanResps = deviceToWifiScans.get(serialNumber);

		// 2.4G only supports 20 MHz bandwidth
		if (band.equals(Constants.BAND_2G)) {
			return scanResps;
		}

		// Aggregate the scan results into the primary channels based on the bandwidth info
		// For example, if the scan results are channels 36 and 40 and channel width is 40,
		// the aggregated scan results will change the one on channel 40 to channel 36 by
		// checking CHANNELS_WIDTH_TO_PRIMARY.
		List<ProcessedWifiScanEntry> scanRespsProcessed = new ArrayList<ProcessedWifiScanEntry>();
		Map<Integer, Map<String, Integer>> channelDeviceMap = new TreeMap<>();
		for (ProcessedWifiScanEntry entry : scanResps) {
			int primaryChannel = getPrimaryChannel(entry.channel, channelWidth);
			if (primaryChannel == 0) {
				continue;
			}
			if (channelDeviceMap.get(primaryChannel) != null) {
				continue;
			}
			ProcessedWifiScanEntry newEntry = new ProcessedWifiScanEntry(entry);
			newEntry.channel = primaryChannel;
			scanRespsProcessed.add(newEntry);
		}
		return scanRespsProcessed;
	}

	/**
	 * Get the current occupied channel info of the device.
	 * @param band the operational band
	 * @param serialNumber the device
	 * @param channelWidth the channel bandwidth (MHz)
	 * @param availableChannelsList the available channels of the device
	 * @param deviceToWifiScans the filtered and reorganized wifiscan results
	 * @return map of channel to score/weight/# APs
	 */
	protected Map<Integer, Integer> getOccupiedChannels(
		String band,
		String serialNumber,
		int channelWidth,
		List<Integer> availableChannelsList,
		Map<String, List<ProcessedWifiScanEntry>> deviceToWifiScans,
		Map<String, Map<String, Integer>> channelMap,
		Map<String, String> bssidsMap
	) {
		// Find occupied channels (and # associated stations)
		Map<Integer, Integer> occupiedChannels = new TreeMap<>();
		List<ProcessedWifiScanEntry> scanResps = getScanRespsByBandwidth(
			band,
			serialNumber,
			channelWidth,
			deviceToWifiScans
		);

		// Get the occupied channels information
		for (ProcessedWifiScanEntry entry : scanResps) {
			occupiedChannels.compute(
				entry.channel, (k, v) -> (v == null) ? 1 : v + 1
			);
		}

		// For 2.4G, we prioritize the orthogonal channels
		// by considering the overlapping channels
		if (band.equals(Constants.BAND_2G)) {
			Map<Integer, Integer> occupiedOverlapChannels =
				getOccupiedOverlapChannels(occupiedChannels);
			occupiedChannels = new TreeMap<>(occupiedOverlapChannels);
		}
		logger.debug(
			"Device {}: Occupied channels: {} with total # entries: {}",
			serialNumber,
			occupiedChannels.keySet().toString(),
			occupiedChannels.values().stream().mapToInt(i -> i).sum()
		);
		return occupiedChannels;
	}

	/**
	 * Get a new/current channel for the device.
	 * @param band the operational band
	 * @param serialNumber the device
	 * @param availableChannelsList the available channels of the device
	 * @param currentChannel the current channel of the device (for comparison)
	 * @param occupiedChannels the occupied channels info of the device
	 * @return the new/current channel of the device
	 */
	protected int getNewChannel(
		String band,
		String serialNumber,
		List<Integer> availableChannelsList,
		int currentChannel,
		Map<Integer, Integer> occupiedChannels
	) {
		int newChannel = 0;

		// If userChannel is specified or the availableChannelsList only has one element
		if (availableChannelsList.size() == 1) {
			newChannel = availableChannelsList.get(0);
			logger.info(
				"Device {}: only one channel is available, assigning to {}",
				serialNumber,
				newChannel
			);
			return newChannel;
		}

		// If no APs on the same channel, keep this channel
		if (
			!occupiedChannels.containsKey(currentChannel) &&
			availableChannelsList.contains(currentChannel)
		) {
			logger.info(
				"Device {}: No APs on current channel {}, assigning to {}",
				serialNumber,
				currentChannel,
				currentChannel
			);
			newChannel = currentChannel;
		} else {
			// Remove occupied channels from list of possible channels
			List<Integer> candidateChannels =
				new ArrayList<>(availableChannelsList);
			candidateChannels.removeAll(occupiedChannels.keySet());
			if (candidateChannels.isEmpty()) {
				// No free channels: assign AP to least occupied channel
				// Need to update the occupied channels based on the available channels
				Map<Integer, Integer> newOccupiedChannels = new TreeMap<>();
				for (Map.Entry<Integer,Integer> e : occupiedChannels.entrySet()) {
					if (availableChannelsList.contains(e.getKey())) {
						newOccupiedChannels.put(e.getKey(), e.getValue());
					}
				}
				Map.Entry<Integer, Integer> entry =
					newOccupiedChannels.entrySet()
						.stream()
						.min(
							(a, b) ->
							Integer.compare(a.getValue(), b.getValue()))
						.get();
				logger.info(
					"Device {}: No free channels, assigning to least " +
					"weighted/occupied channel {} (weight: {}), {}",
					serialNumber,
					entry.getKey(),
					entry.getValue(),
					newOccupiedChannels
				);
				newChannel = entry.getKey();
			} else {
				// Prioritize channels 1, 6, and/or 11 for 2G
				// if any of them is in the candidate list
				if (band.equals(Constants.BAND_2G)) {
					Set<Integer> priorityMap = new HashSet<>(
						PRIORITY_CHANNELS_2G
					);
					List<Integer> priorityChannels = new ArrayList<>();
					for (
						int chnIndex = 0;
						chnIndex < candidateChannels.size();
						chnIndex++
					) {
						int tempChannel = candidateChannels.get(chnIndex);
						if (priorityMap.contains(tempChannel)) {
							priorityChannels.add(tempChannel);
						}
					}
					if (!priorityChannels.isEmpty()) {
						logger.info(
							"Device {}: Update candidate channels to {} (was {})",
							serialNumber,
							priorityChannels,
							candidateChannels
						);
						candidateChannels = priorityChannels;
					}
				}
				// Randomly assign to any free channel
				int channelIndex = rng.nextInt(candidateChannels.size());
				newChannel = candidateChannels.get(channelIndex);
				logger.info(
					"Device {}: Assigning to random free channel {} (from " +
					"available list: {})",
					serialNumber,
					newChannel,
					candidateChannels.toString()
				);
			}
		}
		return newChannel;
	}

	@Override
	public Map<String, Map<String, Integer>> computeChannelMap() {
		Map<String, Map<String, Integer>> channelMap = new TreeMap<>();
		Map<String, List<String>> bandsMap = UCentralUtils
			.getBandsMap(model.latestDeviceStatus);

		Map<String, Map<String, List<Integer>>> deviceAvailableChannels =
			UCentralUtils.getDeviceAvailableChannels(
				model.latestDeviceStatus,
				model.latestDeviceCapabilities,
				AVAILABLE_CHANNELS_BAND
			);

		Map<String, String> bssidsMap = UCentralUtils.getBssidsMap(model.latestState);

		for (String band : bandsMap.keySet()) {
			// Performance metrics
			Map<String, Integer> oldChannelMap = new TreeMap<>();
			Map<String, Integer> newChannelMap = new TreeMap<>();

			// Only use last wifi scan result for APs (TODO)
			Map<String, List<ProcessedWifiScanEntry>> deviceToWifiScans = getDeviceToWiFiScans(
				band, model.latestWifiScans, bandsMap
			);

			// Order by number of nearby APs detected in wifi scan (descending)
			List<String> sortedAPs = getSortedAPs(deviceToWifiScans);

			// Assign channel to each AP
			for (String serialNumber : sortedAPs) {
				// Get available channels of the device
				List<Integer> availableChannelsList = deviceAvailableChannels
					.get(band).get(serialNumber);
				if (availableChannelsList == null || availableChannelsList.isEmpty()) {
					availableChannelsList = AVAILABLE_CHANNELS_BAND.get(band);
				}

				// Get current channel of the device
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
				int[] currentChannelInfo = getCurrentChannel(band, serialNumber, state);
				int currentChannel = currentChannelInfo[0];
				int currentChannelWidth = currentChannelInfo[1];
				// Filter out APs if the number of radios in the state and config mismatches
				// Happen when an AP's radio is enabled/disabled on the fly
				if (currentChannel == 0) {
					logger.debug(
						"Device {}: No {} radio, skipping...",
						serialNumber,
						band
					);
					continue;
				}

				// Get the occupied channels info of the device
				Map<Integer, Integer> occupiedChannels = getOccupiedChannels(
					band, serialNumber, currentChannelWidth, availableChannelsList,
					deviceToWifiScans, channelMap, bssidsMap
				);

				// Update the availableChannelsList by usersChannels and allowedChannels
				availableChannelsList = updateAvailableChannelsList(
					band, serialNumber, currentChannelWidth, availableChannelsList
				);

				// Get a (new) channel of the device
				int newChannel = getNewChannel(
					band, serialNumber, availableChannelsList,
					currentChannel, occupiedChannels
				);

				channelMap.computeIfAbsent(
					serialNumber, k -> new TreeMap<>()
				)
				.put(band, newChannel);

				// Gather the info for the performance metrics
				oldChannelMap.put(serialNumber, currentChannel);
				newChannelMap.put(serialNumber, newChannel);
			}
			// Get and log the performance metrics
			logPerfMetrics(oldChannelMap, newChannelMap, deviceToWifiScans, bssidsMap);
		}

		return channelMap;
	}
}
