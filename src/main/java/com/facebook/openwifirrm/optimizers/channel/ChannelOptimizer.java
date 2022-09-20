/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.channel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.ConfigManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.facebook.openwifirrm.ucentral.operationelement.HTOperationElement;
import com.facebook.openwifirrm.ucentral.operationelement.VHTOperationElement;

/**
 * Channel optimizer base class.
 */
public abstract class ChannelOptimizer {
	private static final Logger logger =
		LoggerFactory.getLogger(ChannelOptimizer.class);

	/** Minimum supported channel width (MHz), inclusive. */
	public static final int MIN_CHANNEL_WIDTH = 20;

	/** List of available channels per band for use. */
	public static final Map<String, List<Integer>> AVAILABLE_CHANNELS_BAND =
		new HashMap<>();
	static {
		AVAILABLE_CHANNELS_BAND.put(
			UCentralConstants.BAND_5G,
			Collections.unmodifiableList(
				Arrays.asList(36, 40, 44, 48, 149, 153, 157, 161, 165)
			)
		);
		AVAILABLE_CHANNELS_BAND.put(
			UCentralConstants.BAND_2G,
			Collections.unmodifiableList(
				Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
			)
		);
	}

	/** Map of channel width (MHz) to available (primary) channels */
	protected static final Map<Integer, List<Integer>> AVAILABLE_CHANNELS_WIDTH =
		new HashMap<>();
	static {
		AVAILABLE_CHANNELS_WIDTH.put(
			40,
			Collections.unmodifiableList(
				Arrays.asList(
					36,
					44,
					52,
					60,
					100,
					108,
					116,
					124,
					132,
					140,
					149,
					157
				)
			)
		);
		AVAILABLE_CHANNELS_WIDTH.put(
			80,
			Collections.unmodifiableList(
				Arrays.asList(36, 52, 100, 116, 132, 149)
			)
		);
		AVAILABLE_CHANNELS_WIDTH.put(
			160,
			Collections.unmodifiableList(
				Arrays.asList(36, 100)
			)
		);
	}

	/**
	 * Map of channel number to channel width (MHz) to its corresponding (primary) channels.
	 * The channel width is a list of fixed numbers (20, 40, 80, 160).
	 * */
	private static final Map<Integer, List<Integer>> CHANNELS_WIDTH_TO_PRIMARY =
		new HashMap<>();
	static {
		CHANNELS_WIDTH_TO_PRIMARY.put(36, Arrays.asList(36, 36, 36, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(40, Arrays.asList(40, 36, 36, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(44, Arrays.asList(44, 44, 36, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(48, Arrays.asList(48, 44, 36, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(52, Arrays.asList(52, 52, 52, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(56, Arrays.asList(56, 52, 52, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(60, Arrays.asList(60, 60, 52, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(64, Arrays.asList(64, 60, 52, 36));
		CHANNELS_WIDTH_TO_PRIMARY.put(100, Arrays.asList(100, 100, 100, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(104, Arrays.asList(104, 100, 100, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(108, Arrays.asList(108, 108, 100, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(112, Arrays.asList(112, 108, 100, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(116, Arrays.asList(116, 116, 116, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(120, Arrays.asList(120, 116, 116, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(124, Arrays.asList(124, 124, 116, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(128, Arrays.asList(128, 124, 116, 100));
		CHANNELS_WIDTH_TO_PRIMARY.put(132, Arrays.asList(132, 132, 132));
		CHANNELS_WIDTH_TO_PRIMARY.put(136, Arrays.asList(136, 132, 132));
		CHANNELS_WIDTH_TO_PRIMARY.put(140, Arrays.asList(140, 140, 132));
		CHANNELS_WIDTH_TO_PRIMARY.put(144, Arrays.asList(144, 140, 132));
		CHANNELS_WIDTH_TO_PRIMARY.put(149, Arrays.asList(149, 149, 149));
		CHANNELS_WIDTH_TO_PRIMARY.put(153, Arrays.asList(153, 149, 149));
		CHANNELS_WIDTH_TO_PRIMARY.put(157, Arrays.asList(157, 157, 149));
		CHANNELS_WIDTH_TO_PRIMARY.put(161, Arrays.asList(161, 157, 149));
		CHANNELS_WIDTH_TO_PRIMARY.put(165, Arrays.asList(165));
	}

	/** List of priority channels on 2.4GHz. */
	protected static final List<Integer> PRIORITY_CHANNELS_2G =
		Collections.unmodifiableList(Arrays.asList(1, 6, 11));

	/** The input data model. */
	protected final DataModel model;

	/** The RF zone. */
	protected final String zone;

	/** The device configs within {@link #zone}, keyed on serial number. */
	protected final Map<String, DeviceConfig> deviceConfigs;

	/** Constructor. */
	public ChannelOptimizer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this.model = model;
		this.zone = zone;
		this.deviceConfigs = deviceDataManager.getAllDeviceConfigs(zone);

		// Remove model entries not in the given zone
		this.model.latestWifiScans.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestState.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestDeviceStatusRadios.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestDeviceCapabilities.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
	}

	/**
	 * Get the primary channel depending on the given channel and channel width.
	 * @param channel the current channel (from the scan result)
	 * @param channelWidth the channel bandwidth (MHz)
	 * @return the primary channel, 0 if error
	 */
	protected static int getPrimaryChannel(
		int channel,
		int channelWidth
	) {
		if (CHANNELS_WIDTH_TO_PRIMARY.get(channel) == null) {
			return 0;
		}

		int index = (int) (Math.log(channelWidth / 20) / Math.log(2));
		if (CHANNELS_WIDTH_TO_PRIMARY.get(channel).size() > index) {
			return CHANNELS_WIDTH_TO_PRIMARY.get(channel).get(index);
		} else {
			return 0;
		}
	}

	/**
	 * Get the channel width based on the HT operation and VHT operation in wifi scan.
	 * @param channel the current channel (from the scan result)
	 * @param htOper the HT operation information element
	 * @param vhtOper the VHT operation information element
	 * @return the channel width, default = MIN_CHANNEL_WIDTH
	 */
	protected static int getChannelWidthFromWiFiScan(
		int channel,
		String htOper,
		String vhtOper
	) {
		if (
			AVAILABLE_CHANNELS_BAND.get(UCentralConstants.BAND_2G)
				.contains(channel)
		) {
			// 2.4G, it only supports 20 MHz
			return 20;
		}
		if (htOper == null) {
			// Obsolete OpenWiFi APs (< v2.5)
			return MIN_CHANNEL_WIDTH;
		}

		HTOperationElement htOperObj = new HTOperationElement(htOper);
		if (vhtOper == null) {
			// HT mode only supports 20/40 MHz
			return htOperObj.staChannelWidth ? 40 : 20;
		} else {
			// VHT/HE mode supports 20/40/160/80+80 MHz
			VHTOperationElement vhtOperObj = new VHTOperationElement(vhtOper);
			if (!htOperObj.staChannelWidth && vhtOperObj.channelWidth == 0) {
				return 20;
			} else if (
				htOperObj.staChannelWidth && vhtOperObj.channelWidth == 0
			) {
				return 40;
			} else if (
				htOperObj.staChannelWidth && vhtOperObj.channelWidth == 1 &&
					vhtOperObj.channel2 == 0
			) {
				return 80;
			} else if (
				htOperObj.staChannelWidth && vhtOperObj.channelWidth == 1 &&
					vhtOperObj.channel2 != 0
			) {
				// if it is 160 MHz, it use two consecutive 80 MHz bands
				// the difference of 8 means it is consecutive
				int channelDiff =
					Math.abs(vhtOperObj.channel1 - vhtOperObj.channel2);
				// the "8080" below does not mean 8080 MHz wide, it refers to 80+80 MHz channel
				return channelDiff == 8 ? 160 : 8080;
			} else {
				return MIN_CHANNEL_WIDTH;
			}
		}
	}

	/**
	 * Get the actual covered channels of a neighboring AP
	 * based on the given channel and channel width.
	 * @param channel the current channel (from the scan result)
	 * @param primaryChannel the primary channel corresponding to the channelWidth
	 * @param channelWidth the channel bandwidth (MHz)
	 * @return the list of the covered channels, the current channel if it is 2.4 GHz or error
	 */
	protected static List<Integer> getCoveredChannels(
		int channel,
		int primaryChannel,
		int channelWidth
	) {
		if (primaryChannel == 0) {
			// if it is 2.4 GHz or the AP doesn't support this feature
			return Arrays.asList(channel);
		}
		int numOfChannels = channelWidth / 20;
		List<Integer> coveredChannels = new ArrayList<>(numOfChannels);
		for (int index = 0; index < numOfChannels; index++) {
			coveredChannels.add(primaryChannel + index * 4);
		}
		return coveredChannels;
	}

	/**
	 * Get the filtered and reorganized wifiscan results per device.
	 * @param band the operational band
	 * @param latestWifiScans the raw wifiscan results from upstream
	 * @param bandsMap the participated OWF APs on this band
	 * @return map of device (serial number) to wifiscan results
	 */
	protected static Map<String, List<WifiScanEntry>> getDeviceToWiFiScans(
		String band,
		Map<String, List<List<WifiScanEntry>>> latestWifiScans,
		Map<String, List<String>> bandsMap
	) {
		Map<String, List<WifiScanEntry>> deviceToWifiScans = new HashMap<>();

		for (
			Map.Entry<String, List<List<WifiScanEntry>>> e : latestWifiScans
				.entrySet()
		) {
			String serialNumber = e.getKey();

			if (!bandsMap.get(band).contains(serialNumber)) {
				// 1. Filter out APs without radio on a specific band
				logger.debug(
					"Device {}: No {} radio, skipping...",
					serialNumber,
					band
				);
				continue;
			}

			List<List<WifiScanEntry>> wifiScanList = e.getValue();
			if (wifiScanList.isEmpty()) {
				// 2. Filter out APs with empty scan results
				logger.debug(
					"Device {}: Empty wifi scan results, skipping...",
					serialNumber
				);
				continue;
			}

			// 1. Remove the wifi scan results on different bands
			// 2. Duplicate the wifi scan result from a channel to multiple channels
			//    if the neighboring AP is using a wider bandwidth (> 20 MHz)
			List<WifiScanEntry> scanResps =
				wifiScanList.get(wifiScanList.size() - 1);
			List<WifiScanEntry> scanRespsFiltered =
				new ArrayList<WifiScanEntry>();
			for (WifiScanEntry entry : scanResps) {
				if (UCentralUtils.isChannelInBand(entry.channel, band)) {
					int channelWidth = getChannelWidthFromWiFiScan(
						entry.channel,
						entry.ht_oper,
						entry.vht_oper
					);
					int primaryChannel =
						getPrimaryChannel(entry.channel, channelWidth);
					List<Integer> coveredChannels =
						getCoveredChannels(
							entry.channel,
							primaryChannel,
							channelWidth
						);
					for (Integer newChannel : coveredChannels) {
						WifiScanEntry newEntry = new WifiScanEntry(entry);
						newEntry.channel = newChannel;
						scanRespsFiltered.add(newEntry);
					}
				}
			}

			if (scanRespsFiltered.size() == 0) {
				// 3. Filter out APs with empty scan results (on a particular band)
				logger.debug(
					"Device {}: Empty wifi scan results on {} band, skipping...",
					serialNumber,
					band
				);
				continue;
			}

			deviceToWifiScans.put(
				serialNumber,
				scanRespsFiltered
			);
		}
		return deviceToWifiScans;
	}

	/**
	 * Get the current channel and channel width (MHz) of the device (from state data).
	 *
	 * @param band the operational band (e.g., "2G")
	 * @param serialNumber the device's serial number
	 * @param state the latest state of all the devices
	 * @return the current channel and channel width (MHz) of the device in the
	 * given band; returns a current channel of 0 if no channel in the given
	 * band is found.
	 */
	protected static int[] getCurrentChannel(
		String band,
		String serialNumber,
		State state
	) {
		int currentChannel = 0;
		int currentChannelWidth = MIN_CHANNEL_WIDTH;
		// Use the channel value to check the corresponding radio for the band
		for (
			int radioIndex = 0;
			radioIndex < state.radios.length;
			radioIndex++
		) {
			int tempChannel = state.radios[radioIndex].channel;
			if (UCentralUtils.isChannelInBand(tempChannel, band)) {
				currentChannel = tempChannel;
				// treat as two separate 80MHz channel and only assign to one
				// TODO: support 80p80 properly
				Integer parsedChannelWidth = UCentralUtils
					.parseChannelWidth(
						state.radios[radioIndex].channel_width,
						true
					);
				if (parsedChannelWidth != null) {
					currentChannelWidth = parsedChannelWidth;
					break;
				}

				logger.error(
					"Invalid channel width {}",
					state.radios[radioIndex].channel_width
				);
				continue;
			}
		}
		return new int[] { currentChannel, currentChannelWidth };
	}

	/**
	 * Update the available channels based on bandwidth-specific, user, allowed channels
	 * (the last two are from deviceConfig).
	 * @param band the operational band
	 * @param serialNumber the device
	 * @param channelWidth the channel bandwidth (MHz)
	 * @param availableChannelsList the available channels of the device
	 * @return the updated available channels of the device
	 */
	protected List<Integer> updateAvailableChannelsList(
		String band,
		String serialNumber,
		int channelWidth,
		List<Integer> availableChannelsList
	) {
		List<Integer> newAvailableChannelsList =
			new ArrayList<>(availableChannelsList);

		// Update the available channels if the bandwidth info is taken into account
		if (band.equals(UCentralConstants.BAND_5G) && channelWidth > 20) {
			newAvailableChannelsList.retainAll(
				AVAILABLE_CHANNELS_WIDTH
					.getOrDefault(channelWidth, availableChannelsList)
			);
		}

		// Update the available channels based on user channels or allowed channels
		DeviceConfig deviceCfg = deviceConfigs.get(serialNumber);
		if (deviceCfg == null) {
			return newAvailableChannelsList;
		}
		if (
			deviceCfg.userChannels != null &&
				deviceCfg.userChannels.get(band) != null
		) {
			newAvailableChannelsList = Arrays.asList(
				deviceCfg.userChannels.get(band)
			);
			logger.debug(
				"Device {}: userChannels {}",
				serialNumber,
				deviceCfg.userChannels.get(band)
			);
		} else if (
			deviceCfg.allowedChannels != null &&
				deviceCfg.allowedChannels.get(band) != null
		) {
			List<Integer> allowedChannels = deviceCfg.allowedChannels.get(band);
			logger.debug(
				"Device {}: allowedChannels {}",
				serialNumber,
				allowedChannels
			);
			newAvailableChannelsList.retainAll(allowedChannels);
		}

		// If the intersection of the above steps gives an empty list,
		// turn back to use the default available channels list
		if (newAvailableChannelsList.isEmpty()) {
			logger.debug(
				"Device {}: the updated availableChannelsList is empty!!! " +
					"userChannels or allowedChannels might be invalid " +
					"Fall back to the default available channels list"
			);
			if (band.equals(UCentralConstants.BAND_5G) && channelWidth > 20) {
				newAvailableChannelsList =
					new ArrayList<>(availableChannelsList);
				newAvailableChannelsList.retainAll(
					AVAILABLE_CHANNELS_WIDTH
						.getOrDefault(channelWidth, availableChannelsList)
				);
			} else {
				newAvailableChannelsList = availableChannelsList;
			}
		}
		logger.debug(
			"Device {}: the updated availableChannelsList is {}",
			serialNumber,
			newAvailableChannelsList
		);
		return newAvailableChannelsList;
	}

	/**
	 * Calculate the performance metrics based on the given assignment.
	 * @param tempChannelMap the map of device (serial number) to its given channel
	 * @param deviceToWifiScans the map of device (serial number) to wifiscan results
	 * @param bssidsMap the map of bssid to device (serial number)
	 * @param mode true for the new assignment and false for the current assignment
	 */
	protected void calculatePerfMetrics(
		Map<String, Integer> tempChannelMap,
		Map<String, List<WifiScanEntry>> deviceToWifiScans,
		Map<String, String> bssidsMap,
		boolean mode
	) {
		for (Map.Entry<String, Integer> e : tempChannelMap.entrySet()) {
			String serialNumber = e.getKey();
			int channel = e.getValue();
			double avgInterferenceDB = 0.0;
			double sumInterference = 0.0;
			double maxInterferenceDB = Double.NEGATIVE_INFINITY;
			double numEntries = 0.0;
			Map<String, Integer> owfSignal = new HashMap<>();
			Map<Integer, Integer> channelOccupancy = new HashMap<>();

			// Calculate the co-channel interference
			List<WifiScanEntry> scanResps = deviceToWifiScans.get(serialNumber);
			if (scanResps != null) {
				for (WifiScanEntry entry : scanResps) {
					// Store the detected signal of the OWF APs
					// for the new assignment calculation
					if (mode && bssidsMap.containsKey(entry.bssid)) {
						owfSignal.put(bssidsMap.get(entry.bssid), entry.signal);
						continue;
					}
					channelOccupancy.compute(
						entry.channel,
						(k, v) -> (v == null) ? 1 : v + 1
					);
					if (entry.channel == channel) {
						double signal = entry.signal;
						avgInterferenceDB += signal;
						sumInterference += Math.pow(10.0, signal / 10.0);
						maxInterferenceDB = Math.max(maxInterferenceDB, signal);
						numEntries += 1.0;
					}
				}
			}

			// Calculate the co-channel interference and channel occupancy
			// based on the new assignment of the OWF APs
			if (mode) {
				for (Map.Entry<String, Integer> f : tempChannelMap.entrySet()) {
					String nSerialNumber = f.getKey();
					int nChannel = f.getValue();
					if (
						serialNumber == nSerialNumber ||
							owfSignal.get(nSerialNumber) == null
					) {
						continue;
					}
					// If the nearby OWF AP is able to be detected by this AP,
					// it should be part of the channel occupancy calculation.
					channelOccupancy.compute(
						nChannel,
						(k, v) -> (v == null) ? 1 : v + 1
					);
					// Only if the nearby OWF AP is on the same channel of this AP,
					// it contributes to the "co-channel" interference.
					if (channel == nChannel) {
						double signal = owfSignal.get(nSerialNumber);
						avgInterferenceDB += signal;
						sumInterference += Math.pow(10.0, signal / 10.0);
						maxInterferenceDB = Math.max(maxInterferenceDB, signal);
						numEntries += 1.0;
					}
				}
			}

			// Add self into the channel occupancy calculation
			channelOccupancy.compute(
				channel,
				(k, v) -> (v == null) ? 1 : v + 1
			);

			// Log the interference info
			logger.info(
				"Device {} on channel {} with average interference: {}, " +
					"sum interference: {}, max interference: {}, number of nearby APs: {}",
				serialNumber,
				channel,
				avgInterferenceDB / numEntries,
				10 * Math.log10(sumInterference),
				maxInterferenceDB,
				numEntries
			);

			// Log the channel occupancy info
			logger.info(
				"Device {}: its view of channel occupancy: {}",
				serialNumber,
				channelOccupancy
			);
		}
	}

	/**
	 * Log the performance metrics before and after the algorithm.
	 * @param oldChannelMap the map of device (serial number) to its current channel
	 * @param newChannelMap the map of device (serial number) to its new channel
	 * @param deviceToWifiScans the map of device (serial number) to wifiscan results
	 * @param bssidsMap the map of bssid to device (serial number)
	 */
	protected void logPerfMetrics(
		Map<String, Integer> oldChannelMap,
		Map<String, Integer> newChannelMap,
		Map<String, List<WifiScanEntry>> deviceToWifiScans,
		Map<String, String> bssidsMap
	) {
		// Calculate the old performance
		calculatePerfMetrics(
			oldChannelMap,
			deviceToWifiScans,
			bssidsMap,
			false
		);

		// Calculate the new performance
		calculatePerfMetrics(newChannelMap, deviceToWifiScans, bssidsMap, true);

		// Calculate the number of channel changes
		int numOfChannelChanges = 0;
		for (Map.Entry<String, Integer> e : newChannelMap.entrySet()) {
			// Check whether the channel is changed or not
			// The key of the newChannelMap and oldChannelMap is the same
			int newChannel = e.getValue();
			int oldChannel = oldChannelMap.getOrDefault(e.getKey(), 0);
			if (newChannel != oldChannel) {
				numOfChannelChanges++;
			}
		}
		logger.info(
			"Total number of channel changes: {}",
			numOfChannelChanges
		);
	}

	/**
	 * Compute channel assignments.
	 * @return the map of devices (by serial number) to radio to channel
	 */
	public abstract Map<String, Map<String, Integer>> computeChannelMap();

	/**
	 * Program the given channel map into the AP config and notify the config
	 * manager.
	 *
	 * @param deviceDataManager the DeviceDataManager instance
	 * @param configManager the ConfigManager instance
	 * @param channelMap the map of devices (by serial number) to radio to channel
	 */
	public void applyConfig(
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Map<String, Map<String, Integer>> channelMap
	) {
		// Update device AP config layer
		deviceDataManager.updateDeviceApConfig(apConfig -> {
			for (
				Map.Entry<String, Map<String, Integer>> entry : channelMap
					.entrySet()
			) {
				DeviceConfig deviceConfig = apConfig.computeIfAbsent(
					entry.getKey(),
					k -> new DeviceConfig()
				);
				deviceConfig.autoChannels = entry.getValue();
			}
		});

		// Trigger config update now
		configManager.wakeUp(zone);
	}
}
