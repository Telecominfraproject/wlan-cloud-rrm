/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.channel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.WifiScanEntry;

/**
 * Unmanaged AP aware least used channel optimizer.
 * <p>
 * Randomly assign APs to the channel with the least channel weight,
 * where channel weight = DEFAULT_WEIGHT * (number of unmanaged APs) + (number of managed APs).
 */
public class UnmanagedApAwareChannelOptimizer
	extends LeastUsedChannelOptimizer {
	private static final Logger logger =
		LoggerFactory.getLogger(UnmanagedApAwareChannelOptimizer.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "unmanaged_aware";

	/** The default weight for nonOWF APs. */
	private static final int DEFAULT_WEIGHT = 2;

	/** Factory method to parse generic args map into the proper constructor */
	public static UnmanagedApAwareChannelOptimizer makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		return new UnmanagedApAwareChannelOptimizer(
			model,
			zone,
			deviceDataManager
		);
	}

	/** Constructor. */
	public UnmanagedApAwareChannelOptimizer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		super(model, zone, deviceDataManager);
	}

	@Override
	protected Map<Integer, Integer> getOccupiedChannels(
		String band,
		String serialNumber,
		int channelWidth,
		List<Integer> availableChannelsList,
		Map<String, List<WifiScanEntry>> deviceToWifiScans,
		Map<String, Map<String, Integer>> channelMap,
		Map<String, String> bssidsMap
	) {
		// Find occupied channels by nonOWF APs (and # associated nonOWF APs)
		// Distinguish OWF APs from nonOWF APs
		Map<Integer, Integer> occupiedChannels = new TreeMap<>();
		List<WifiScanEntry> scanResps = getScanRespsByBandwidth(
			band,
			serialNumber,
			channelWidth,
			deviceToWifiScans
		);
		List<WifiScanEntry> scanRespsOWF = new ArrayList<WifiScanEntry>();

		// Remove OWF APs here
		for (WifiScanEntry entry : scanResps) {
			if (bssidsMap.containsKey(entry.bssid)) {
				scanRespsOWF.add(entry);
			} else {
				occupiedChannels.compute(
					entry.channel,
					(k, v) -> (v == null) ? DEFAULT_WEIGHT : v + DEFAULT_WEIGHT
				);
			}
		}
		logger.debug(
			"Device {}: Occupied channels for nonOWF APs: {} " +
				"with total weight: {}",
			serialNumber,
			occupiedChannels.keySet().toString(),
			occupiedChannels.values().stream().mapToInt(i -> i).sum()
		);

		// Find occupied channels by OWF APs (and # associated OWF APs)
		for (WifiScanEntry entry : scanRespsOWF) {
			String nSerialNumber = bssidsMap.get(entry.bssid);
			int assignedChannel = channelMap
				.getOrDefault(nSerialNumber, new HashMap<>())
				.getOrDefault(band, 0);
			// 0 means the bssid has not been assigned yet.
			if (assignedChannel == 0) {
				continue;
			}
			logger.debug(
				"Device {}: Neighbor device: {} on channel {}",
				serialNumber,
				nSerialNumber,
				assignedChannel
			);
			occupiedChannels.compute(
				assignedChannel,
				(k, v) -> (v == null) ? 1 : v + 1
			);
		}
		logger.debug(
			"Device {}: Occupied channels for all APs: {} " +
				"with total weight: {}",
			serialNumber,
			occupiedChannels.keySet().toString(),
			occupiedChannels.values().stream().mapToInt(i -> i).sum()
		);

		// For 2.4G, we prioritize the orthogonal channels
		// by considering the overlapping channels
		if (band.equals(UCentralConstants.BAND_2G)) {
			Map<Integer, Integer> occupiedOverlapChannels =
				getOccupiedOverlapChannels(occupiedChannels);
			occupiedChannels = new TreeMap<>(occupiedOverlapChannels);
			logger.debug(
				"Device {}: Occupied channels for 2G APs: {} " +
					"with total weight: {}",
				serialNumber,
				occupiedChannels.keySet().toString(),
				occupiedChannels.values().stream().mapToInt(i -> i).sum()
			);
		}
		return occupiedChannels;
	}
}
