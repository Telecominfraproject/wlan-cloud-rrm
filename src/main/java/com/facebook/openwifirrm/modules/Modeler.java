/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ModelerParams;
import com.facebook.openwifirrm.ucentral.UCentralApConfiguration;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer.KafkaRecord;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceCapabilities;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.ServiceEvent;
import com.facebook.openwifirrm.ucentral.gw.models.StatisticsRecords;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Modeler module.
 */
public class Modeler implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(Modeler.class);

	/** The module parameters. */
	private final ModelerParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The uCentral client instance. */
	private final UCentralClient client;

	/** Kafka input data types. */
	public enum InputDataType { STATE, WIFISCAN }

	/** Kafka input data wrapper. */
	private class InputData {
		/** Data type. */
		public final InputDataType type;

		/** Records. */
		public final List<KafkaRecord> records;

		/** Constructor. */
		public InputData(InputDataType type, List<KafkaRecord> records) {
			this.type = type;
			this.records = records;
		}
	}

	/** The blocking data queue. */
	private final BlockingQueue<InputData> dataQueue =
		new LinkedBlockingQueue<>();

	/** Data model representation. */
	public static class DataModel {
		// TODO: This is only a placeholder implementation.
		// At minimum, we may want to aggregate recent wifi scan responses and
		// keep a rolling average for stats.

		/** List of latest wifi scan results per device. */
		public Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			new ConcurrentHashMap<>();

		/** List of latest state per device. */
		public Map<String, State> latestState = new ConcurrentHashMap<>();

		/** List of radio info per device. */
		public Map<String, JsonArray> latestDeviceStatus = new ConcurrentHashMap<>();

		/** List of capabilities per device. */
		public Map<String, JsonObject> latestDeviceCapabilities =
			new ConcurrentHashMap<>();
	}

	/** The data model. */
	public DataModel dataModel = new DataModel();

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/** Constructor. */
	public Modeler(
		ModelerParams params,
		DeviceDataManager deviceDataManager,
		UCentralKafkaConsumer consumer,
		UCentralClient client,
		DataCollector dataCollector,
		ConfigManager configManager
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
		this.client = client;

		// Register data hooks
		dataCollector.addDataListener(
			getClass().getSimpleName(),
			new DataCollector.DataListener() {
				@Override
				public void processDeviceCapabilities(
					String serialNumber, DeviceCapabilities capabilities
				) {
					updateDeviceCapabilities(serialNumber, capabilities);
				}
			}
		);

		// Register config hooks
		configManager.addConfigListener(
			getClass().getSimpleName(),
			new ConfigManager.ConfigListener() {
				@Override
				public void receiveDeviceConfig(
					String serialNumber, UCentralApConfiguration config
				) {
					updateDeviceConfig(serialNumber, config);
				}

				@Override
				public boolean processDeviceConfig(
					String serialNumber, UCentralApConfiguration config
				) {
					return false;
				}
			}
		);

		// Register Kafka listener
		if (consumer != null) {
			// We only push data to a blocking queue to be processed by this
			// thread later, instead of the Kafka consumer thread
			consumer.addKafkaListener(
				getClass().getSimpleName(),
				new UCentralKafkaConsumer.KafkaListener() {
					@Override
					public void handleStateRecords(List<KafkaRecord> records) {
						dataQueue.offer(
							new InputData(InputDataType.STATE, records)
						);
					}

					@Override
					public void handleWifiScanRecords(
						List<KafkaRecord> records
					) {
						dataQueue.offer(
							new InputData(InputDataType.WIFISCAN, records)
						);
					}

					@Override
					public void handleServiceEventRecords(List<ServiceEvent> serviceEventRecords) {
						// ignored
					}
				}
			);
		}
	}

	@Override
	public void run() {
		logger.info("Fetching initial data...");
		fetchInitialData();

		// Poll for data until interrupted
		logger.info("Modeler awaiting data...");
		while (!Thread.currentThread().isInterrupted()) {
			try {
				InputData inputData = dataQueue.take();

				// Drop records here if RRM is disabled for a device
				int recordCount = inputData.records.size();
				if (inputData.records.removeIf(
					record -> !isRRMEnabled(record.serialNumber)
				)) {
					logger.debug(
						"Dropping {} Kafka record(s) for non-RRM-enabled devices",
						recordCount - inputData.records.size()
					);
				}

				processData(inputData);
			} catch (InterruptedException e) {
				logger.error("Interrupted!", e);
				break;
			}
		}
		logger.error("Thread terminated!");
	}

	/** Fetch initial data (called only once). */
	private void fetchInitialData() {
		while (!client.isInitialized()) {
			logger.trace("Waiting for ucentral client");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		// TODO: backfill data from database?

		// Fetch state from uCentralGw
		List<DeviceWithStatus> devices = client.getDevices();
		if (devices == null) {
			logger.error("Failed to fetch devices!");
			return;
		}
		logger.debug("Received device list of size = {}", devices.size());
		for (DeviceWithStatus device : devices) {
			// Check if enabled
			if (!isRRMEnabled(device.serialNumber)) {
				logger.debug(
					"Skipping data for non-RRM-enabled device {}",
					device.serialNumber
				);
				continue;
			}

			StatisticsRecords records =
				client.getLatestStats(device.serialNumber, 1);
			if (records == null || records.data.size() != 1) {
				continue;
			}
			JsonObject state = records.data.get(0).data;
			if (state != null) {
				try {
					State stateModel = gson.fromJson(state, State.class);
					dataModel.latestState.put(device.serialNumber, stateModel);
					logger.debug(
						"Device {}: added initial state from uCentralGw",
						device.serialNumber
					);
				} catch (JsonSyntaxException e) {
					logger.error(
						String.format(
							"Device %s: failed to deserialize state: %s",
							device.serialNumber,
							state
						),
						e
					);
				}
			}
		}
	}

	/** Process input data. */
	private void processData(InputData data) {
		// for logging only
		Set<String> stateUpdates = new TreeSet<>();
		Set<String> wifiScanUpdates = new TreeSet<>();

		switch (data.type) {
		case STATE:
			for (KafkaRecord record : data.records) {
				JsonObject state = record.payload.getAsJsonObject("state");
				if (state != null) {
					try {
						State stateModel = gson.fromJson(state, State.class);
						dataModel.latestState.put(record.serialNumber, stateModel);
						stateUpdates.add(record.serialNumber);
					} catch (JsonSyntaxException e) {
						logger.error(
							String.format(
								"Device %s: failed to deserialize state: %s",
								record.serialNumber,
								state
							),
							e
						);
					}
				}
			}
			break;
		case WIFISCAN:
			for (KafkaRecord record : data.records) {
				List<List<WifiScanEntry>> wifiScanList =
					dataModel.latestWifiScans.computeIfAbsent(
						record.serialNumber,
						k -> new LinkedList<>()
					);

				// Parse and validate this record
				List<WifiScanEntry> scanEntries =
					UCentralUtils.parseWifiScanEntries(record.payload);
				if (scanEntries == null) {
					continue;
				}

				// Add to list (and truncate to max size)
				while (wifiScanList.size() >= params.wifiScanBufferSize) {
					wifiScanList.remove(0);
				}
				wifiScanList.add(scanEntries);
				wifiScanUpdates.add(record.serialNumber);
			}
			break;
		}

		if (!stateUpdates.isEmpty()) {
			logger.debug(
				"Received state updates for {} device(s): [{}]",
				stateUpdates.size(),
				String.join(", ", stateUpdates)
			);
		}
		if (!wifiScanUpdates.isEmpty()) {
			logger.debug(
				"Received wifi scan results for {} device(s): [{}]",
				wifiScanUpdates.size(),
				String.join(", ", wifiScanUpdates)
			);
		}
	}

	/**
	 * NOTE: if a BSSID does not have a non-obsolete entry, it will be returned
	 * (i.e., it will not be a key in the returned map).
	 */
	public Map<String, WifiScanEntry> getAggregatedWifiScans(long obsoletion_period) {
		Map<String, WifiScanEntry> aggregatedWifiScans = new HashMap<>();
		for (String bssid : dataModel.latestWifiScans.keySet()) {
			// flatten the wifiscan entries and sort in reverse chronological order
			List<List<WifiScanEntry>> scans = dataModel.latestWifiScans.get(bssid);
			List<WifiScanEntry> mostRecentToOldest = scans.stream().flatMap(list -> list.stream())
					.sorted((entry1, entry2) -> {
						return -Long.signum(entry1.tsf - entry2.tsf);
					}).collect(Collectors.toUnmodifiableList());

			/*
			 * For a given BSSID, discard the obsolete entries. Then,consider the most
			 * recent entry. Take its ht_oper and vht_oper. Discard earlier entries with a
			 * different ht_oper or a different vht_oper. For earlier entries with the same
			 * ht_oper and vht_oper, take the average signal value.
			 */
			long now = Instant.now().getEpochSecond();
			String newest_ht_oper = null;
			String newest_vht_oper = null;
			double averagedSignal = 0.0;
			int count = 0;
			for (WifiScanEntry entry : mostRecentToOldest) {
				if (now - entry.tsf >= obsoletion_period) {
					// discard obsolete entries
					break;
				}
				if (newest_ht_oper == null || newest_vht_oper == null) {
					// start with the most recent entry
					newest_ht_oper = entry.ht_oper;
					newest_vht_oper = entry.vht_oper;
					aggregatedWifiScans.put(bssid, entry);
					averagedSignal = entry.signal;
					count++;
					continue;
				}
				if (!entry.ht_oper.equals(newest_ht_oper) || !entry.vht_oper.equals(newest_vht_oper)) {
					// discard older entries with different ht_oper or different vht_oper
					continue;
				}
				// average signal value from older entries with the same ht_oper and vht_oper
				averagedSignal = (count / (count + 1)) * averagedSignal + (entry.signal / (count + 1));
			}
			aggregatedWifiScans.get(bssid).signal = (int) Math.round(averagedSignal);
		}
		return aggregatedWifiScans;
	}

	/**
	 * Update device capabilities into DataModel whenever there are new changes.
	 */
	private void updateDeviceCapabilities(
		String serialNumber, DeviceCapabilities capabilities
	) {
		dataModel.latestDeviceCapabilities.put(
			serialNumber, capabilities.capabilities.getAsJsonObject("wifi")
		);
	}

	/**
	 * Update device config into DataModel whenever there are new changes.
	 */
	private void updateDeviceConfig(
		String serialNumber, UCentralApConfiguration config
	) {
		// Get old vs new radios info and store the new radios info
		JsonArray newRadioList = config.getRadioConfigList();
		Set<String> newRadioBandsSet = config.getRadioBandsSet(newRadioList);
		JsonArray oldRadioList = dataModel.latestDeviceStatus
			.put(serialNumber, newRadioList);
		Set<String> oldRadioBandsSet = config.getRadioBandsSet(oldRadioList);

		// Print info only when there are any updates
		if (!oldRadioBandsSet.equals(newRadioBandsSet)) {
			logger.info(
				"Device {}: the new radios list is: {} (was {}).",
				serialNumber,
				newRadioBandsSet.toString(),
				oldRadioBandsSet.toString()
			);
		}
	}

	/** Return whether the given device has RRM enabled. */
	private boolean isRRMEnabled(String serialNumber) {
		DeviceConfig deviceConfig =
			deviceDataManager.getDeviceConfig(serialNumber);
		if (deviceConfig == null) {
			return false;
		}
		return deviceConfig.enableRRM;
	}

	/** Return the current data model (direct reference). */
	public DataModel getDataModel() {
		return dataModel;
	}

	/** Return the current data model (deep clone via gson). */
	public DataModel getDataModelCopy() {
		return gson.fromJson(gson.toJson(dataModel), DataModel.class);
	}

	/** Revalidate the data model to remove any non-RRM-enabled devices. */
	public void revalidate() {
		if (
			dataModel.latestWifiScans.entrySet()
				.removeIf(e -> !isRRMEnabled(e.getKey()))
		) {
			logger.debug("Removed some wifi scan entries from data model");
		}
		if (
			dataModel.latestState.entrySet()
				.removeIf(e -> !isRRMEnabled(e.getKey()))
		) {
			logger.debug("Removed some state entries from data model");
		}
		if (
			dataModel.latestDeviceStatus.entrySet()
				.removeIf(e -> !isRRMEnabled(e.getKey()))
		) {
			logger.debug("Removed some status entries from data model");
		}
		if (
			dataModel.latestDeviceCapabilities.entrySet()
				.removeIf(e -> !isRRMEnabled(e.getKey()))
		) {
			logger.debug("Removed some capabilities entries from data model");
		}
	}
}
