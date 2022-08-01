/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.DataCollectorParams;
import com.facebook.openwifirrm.mysql.DatabaseManager;
import com.facebook.openwifirrm.mysql.StateRecord;
import com.facebook.openwifirrm.ucentral.UCentralApConfiguration;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer.KafkaRecord;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.gw.models.CommandInfo;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceCapabilities;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.ServiceEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Data collector module.
 */
public class DataCollector implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(DataCollector.class);

	/** Radio keys from state records to store. */
	private static final String[] RADIO_KEYS = new String[] {
		"channel", "channel_width", "noise", "tx_power"
	};

	/** AP client keys from state records to store. */
	private static final String[] CLIENT_KEYS = new String[] {
		"connected", "inactive", "rssi", "rx_bytes", "rx_packets", "tx_bytes",
		"tx_duration", "tx_failed", "tx_offset", "tx_packets", "tx_retries"
	};
	/** AP client rate keys from state records to store. */
	private static final String[] CLIENT_RATE_KEYS =
		new String[] { "rx_rate", "tx_rate" };

	/** The module parameters. */
	private final DataCollectorParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The uCentral client. */
	private final UCentralClient client;

	/** The database manager. */
	private final DatabaseManager dbManager;

	/** The executor service instance. */
	private final ExecutorService executor;

	/** Runtime per-device data. */
	private class DeviceData {
		/** Last wifi scan time (in monotonic ns). */
		public Long lastWifiScanTimeNs;

		/** Last capabilities request time (in monotonic ns). */
		public Long lastCapabilitiesTimeNs;
	}

	/** Map from device serial number to runtime data. */
	private Map<String, DeviceData> deviceDataMap = new HashMap<>();

	/** Data listener interface. */
	public interface DataListener {
		/** Process a received device capabilities object. */
		void processDeviceCapabilities(
			String serialNumber, DeviceCapabilities capabilities
		);
	}

	/** State listeners. */
	private Map<String, DataListener> dataListeners = new TreeMap<>();

	/** Constructor. */
	public DataCollector(
		DataCollectorParams params,
		DeviceDataManager deviceDataManager,
		UCentralClient client,
		UCentralKafkaConsumer consumer,
		ConfigManager configManager,
		DatabaseManager dbManager
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
		this.client = client;
		this.dbManager = dbManager;
		this.executor =
			Executors.newFixedThreadPool(params.executorThreadCount);

		// Register config hooks
		configManager.addConfigListener(
			getClass().getSimpleName(),
			new ConfigManager.ConfigListener() {
				@Override
				public void receiveDeviceConfig(
					String serialNumber, UCentralApConfiguration config
				) {
					// do nothing
				}

				@Override
				public boolean processDeviceConfig(
					String serialNumber, UCentralApConfiguration config
				) {
					return sanitizeDeviceConfig(serialNumber, config);
				}
			}
		);

		// Register Kafka listener
		if (consumer != null) {
			consumer.addKafkaListener(
				getClass().getSimpleName(),
				new UCentralKafkaConsumer.KafkaListener() {
					@Override
					public void handleStateRecords(List<KafkaRecord> records) {
						handleKafkaStateRecords(records);
					}

					@Override
					public void handleWifiScanRecords(
						List<KafkaRecord> records
					) {
						// ignored here, handled directly from UCentralClient
					}

					@Override
					public void handleServiceEventRecords(List<ServiceEvent> serviceEventRecords) {
						// ignored here, handled directly from UCentralKafkaConsumer
					}
				}
			);
		}
	}

	/** Shut down all resources. */
	public void shutdown() {
		executor.shutdownNow();
	}

	@Override
	public void run() {
		// Run application logic in a periodic loop
		while (!Thread.currentThread().isInterrupted()) {
			try {
				runImpl();
				Thread.sleep(params.updateIntervalMs);
			} catch (InterruptedException e) {
				logger.error("Interrupted!", e);
				break;
			}
		}
		logger.error("Thread terminated!");
	}

	/** Run single iteration of application logic. */
	private void runImpl() {
		while (!client.isInitialized()) {
			logger.trace("Waiting for ucentral client");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		// Fetch device list
		List<DeviceWithStatus> devices = client.getDevices();
		if (devices == null) {
			logger.error("Failed to fetch devices!");
			return;
		}
		int fetchSize = devices.size();
		logger.debug("Received device list of size = {}", fetchSize);
		if (devices.removeIf(
			device -> !deviceDataManager.isDeviceInTopology(device.serialNumber)
		)) {
			int removedSize = fetchSize - devices.size();
			logger.debug(
				"Ignoring {} device(s) not in topology", removedSize
			);
		}
		for (DeviceWithStatus device : devices) {
			deviceDataMap.computeIfAbsent(
				device.serialNumber, k -> new DeviceData()
			);
		}

		// Fetch device capabilities
		fetchDeviceCapabilities(devices);

		// Perform wifi scans
		scheduleWifiScans(devices);
	}

	/**
	 * Check that device config matches expected values.
	 *
	 * If any changes are needed, modify the config and return true.
	 * Otherwise, return false.
	 */
	private boolean sanitizeDeviceConfig(
		String serialNumber, UCentralApConfiguration config
	) {
		boolean modified = false;

		// Check stats interval
		final int STATS_INTERVAL_S = params.deviceStatsIntervalSec;
		if (STATS_INTERVAL_S < 1) {
			return false;  // unmanaged
		}
		int currentStatsInterval = config.getStatisticsInterval();
		if (currentStatsInterval != STATS_INTERVAL_S) {
			logger.info(
				"Device {}: setting stats interval to {} (was {})",
				serialNumber, STATS_INTERVAL_S, currentStatsInterval
			);
			config.setStatisticsInterval(STATS_INTERVAL_S);
			modified = true;
		}

		return modified;
	}

	/** Fetch capabilities for the given list of devices. */
	private void fetchDeviceCapabilities(List<DeviceWithStatus> devices) {
		long now = System.nanoTime();
		final long CAPABILITIES_INTERVAL_NS =
			Math.max(params.capabilitiesIntervalSec, 1) * 1_000_000_000L;

		for (DeviceWithStatus device : devices) {
			// Check last request time
			DeviceData data = deviceDataMap.get(device.serialNumber);
			if (
				data.lastCapabilitiesTimeNs != null &&
				now - data.lastCapabilitiesTimeNs < CAPABILITIES_INTERVAL_NS
			) {
				logger.trace(
					"Skipping capabilities for {} (last requested {}s ago)",
					device.serialNumber,
					(now - data.lastCapabilitiesTimeNs) / 1_000_000_000L
				);
				continue;
			}

			// Issue capabilities request (via executor, will run async eventually)
			// Set "lastCapabilitiesTimeNs" now and again when the request actually runs
			logger.info("Device {}: queued capabilities request", device.serialNumber);
			data.lastCapabilitiesTimeNs = now;
			executor.submit(() -> {
				data.lastCapabilitiesTimeNs = System.nanoTime();
				performDeviceCapabilitiesRequest(device.serialNumber);
			});
		}
	}

	/** Schedule wifi scans for the given list of devices. */
	private void scheduleWifiScans(List<DeviceWithStatus> devices) {
		// Disabled?
		if (params.wifiScanIntervalSec == -1) {
			logger.trace("Automatic wifi scans are disabled.");
			return;
		}

		long now = System.nanoTime();
		final long WIFI_SCAN_INTERVAL_NS =
			Math.max(params.wifiScanIntervalSec, 1) * 1_000_000_000L;
		for (DeviceWithStatus device : devices) {
			// Check if scans are enabled in device config
			DeviceConfig deviceConfig =
				deviceDataManager.getDeviceConfig(device.serialNumber);
			if (deviceConfig == null) {
				logger.trace(
					"Skipping wifi scan for {} (null device config)",
					device.serialNumber
				);
				continue;
			}
			if (!deviceConfig.enableWifiScan) {
				logger.trace(
					"Skipping wifi scan for {} (disabled in device config)",
					device.serialNumber
				);
				continue;
			}

			// Check last request time
			DeviceData data = deviceDataMap.get(device.serialNumber);
			if (
				data.lastWifiScanTimeNs != null &&
				now - data.lastWifiScanTimeNs < WIFI_SCAN_INTERVAL_NS
			) {
				logger.trace(
					"Skipping wifi scan for {} (last scanned {}s ago)",
					device.serialNumber,
					(now - data.lastWifiScanTimeNs) / 1_000_000_000L
				);
				continue;
			}

			// Issue scan command (via executor, will run async eventually)
			// Set "lastWifiScanTime" now and again when the scan actually runs
			logger.info("Device {}: queued wifi scan", device.serialNumber);
			data.lastWifiScanTimeNs = now;
			executor.submit(() -> {
				data.lastWifiScanTimeNs = System.nanoTime();
				performWifiScan(device.serialNumber);
			});
		}
	}

	/**
	 * Request device capabilities and handle results.
	 *
	 * Returns true upon failure and false otherwise.
	 */
	private boolean performDeviceCapabilitiesRequest(String serialNumber) {
		logger.info("Device {}: requesting capabilities...", serialNumber);
		DeviceCapabilities capabilities = client.getCapabilities(serialNumber);
		if (capabilities == null) {
			logger.error("Device {}: capabilities request failed", serialNumber);
			return false;
		}
		logger.debug(
			"Device {}: capabilities response: {}",
			serialNumber,
			new Gson().toJson(capabilities)
		);

		// Process results
		for (DataListener listener : dataListeners.values()) {
			listener.processDeviceCapabilities(serialNumber, capabilities);
		}
		return true;
	}

	/**
	 * Issue a wifi scan command to the given device and handle results.
	 *
	 * Returns true upon failure and false otherwise.
	 */
	private boolean performWifiScan(String serialNumber) {
		logger.info("Device {}: performing wifi scan...", serialNumber);
		CommandInfo wifiScanResult = client.wifiScan(serialNumber, true);
		if (wifiScanResult == null) {
			logger.error("Device {}: wifi scan request failed", serialNumber);
			return false;
		}
		logger.trace(
			"Device {}: wifi scan results: {}",
			serialNumber,
			new Gson().toJson(wifiScanResult)
		);

		// Process results
		if (wifiScanResult.errorCode != 0) {
			logger.error(
				"Device {}: wifi scan returned error code {}",
				serialNumber, wifiScanResult.errorCode
			);
			return false;
		}
		if (wifiScanResult.results.entrySet().isEmpty()) {
			logger.error(
				"Device {}: wifi scan returned empty result set", serialNumber
			);
			return false;
		}
		List<WifiScanEntry> scanEntries =
			UCentralUtils.parseWifiScanEntries(wifiScanResult.results);
		if (scanEntries == null) {
			logger.error(
				"Device {}: wifi scan returned unexpected result", serialNumber
			);
			return false;
		}

		// Print some processed info (not doing anything else useful here)
		Set<Integer> channels = scanEntries
			.stream()
			.map(entry -> entry.channel)
			.collect(Collectors.toCollection(() -> new TreeSet<>()));
		logger.info(
			"Device {}: found {} network(s) on {} channel(s): {}",
			serialNumber,
			scanEntries.size(),
			channels.size(),
			channels
		);

		// Save to database
		insertWifiScanResultsToDatabase(
			serialNumber, wifiScanResult.executed, scanEntries
		);

		return true;
	}

	/** Insert wifi scan results into database. */
	private void insertWifiScanResultsToDatabase(
		String serialNumber, long ts, List<WifiScanEntry> entries
	) {
		if (dbManager == null) {
			return;
		}

		// Insert into database
		try {
			dbManager.addWifiScan(serialNumber, ts, entries);
		} catch (SQLException e) {
			logger.error("Failed to insert wifi scan results into database", e);
			return;
		}
	}

	/** Kafka state records callback. */
	private void handleKafkaStateRecords(List<KafkaRecord> records) {
		logger.debug("Handling {} state record(s)", records.size());
		insertStateRecordsToDatabase(records);
	}

	/** Parse a single state record into individual metrics. */
	protected static void parseStateRecord(
		String serialNumber, JsonObject payload, List<StateRecord> results
	) {
		JsonObject state = payload.getAsJsonObject("state");
		JsonArray interfaces = state.getAsJsonArray("interfaces");
		JsonArray radios = state.getAsJsonArray("radios");
		JsonObject unit = state.getAsJsonObject("unit");
		long localtime = unit.get("localtime").getAsLong();

		// "interfaces"
		// - store all entries from "counters" as
		//   "interface.<interface_name>.<counter_name>"
		// - store all entries from "ssids.<N>.associations.<M>" as
		//   "interface.<interface_name>.bssid.<bssid>.client.<bssid>.<counter_name>"
		for (JsonElement o1 : interfaces) {
			JsonObject iface = o1.getAsJsonObject();
			String ifname = iface.get("name").getAsString();

			JsonObject counters = iface.getAsJsonObject("counters");
			if (counters != null) {
				for (Map.Entry<String, JsonElement> entry : counters.entrySet()) {
					String metric = String.format(
						"interface.%s.%s", ifname, entry.getKey()
					);
					long value = entry.getValue().getAsLong();
					results.add(new StateRecord(
						localtime, metric, value, serialNumber
					));
				}
			}
			JsonArray ssids = iface.getAsJsonArray("ssids");
			if (ssids != null) {
				for (JsonElement o2 : ssids) {
					JsonObject ssid = o2.getAsJsonObject();
					if (!ssid.has("bssid")) {
						continue;
					}
					String bssid = ssid.get("bssid").getAsString();
					JsonArray associations = ssid.getAsJsonArray("associations");
					if (associations != null) {
						for (JsonElement o3 : associations) {
							JsonObject client = o3.getAsJsonObject();
							if (!client.has("bssid")) {
								continue;
							}
							String clientBssid = client.get("bssid").getAsString();
							for (String s : CLIENT_KEYS) {
								if (!client.has(s) || !client.get(s).isJsonPrimitive()) {
									continue;
								}
								String metric = String.format(
									"interface.%s.bssid.%s.client.%s.%s",
									ifname, bssid, clientBssid, s
								);
								long value = client.get(s).getAsLong();
								results.add(new StateRecord(
									localtime, metric, value, serialNumber
								));
							}
							for (String s : CLIENT_RATE_KEYS) {
								if (!client.has(s) || !client.get(s).isJsonObject()) {
									continue;
								}
								String metricBase = String.format(
									"interface.%s.bssid.%s.client.%s.%s",
									ifname, bssid, clientBssid, s
								);
								for (
									Map.Entry<String, JsonElement> entry :
									client.getAsJsonObject(s).entrySet()
								) {
									String metric = String.format(
										"%s.%s", metricBase, entry.getKey()
									);
									long value;
									if (entry.getValue().getAsJsonPrimitive().isBoolean()) {
										value = entry.getValue().getAsBoolean() ? 1 : 0;
									} else {
										value = entry.getValue().getAsLong();
									}
									results.add(new StateRecord(
										localtime, metric, value, serialNumber
									));
								}
							}
						}
					}
				}
			}
		}

		// "radios"
		// - store "channel", "channel_width", "noise", "tx_power" as
		//   "radio.<N>.<counter_name>"
		if (radios != null) {
			for (int i = 0; i < radios.size(); i++) {
				JsonObject o = radios.get(i).getAsJsonObject();
				for (String s : RADIO_KEYS) {
					if (!o.has(s) || !o.get(s).isJsonPrimitive()) {
						continue;
					}
					String metric = String.format("radio.%d.%s", i, s);
					long value = o.get(s).getAsLong();
					results.add(
						new StateRecord(localtime, metric, value, serialNumber)
					);
				}
			}
		}

		// "unit"
		// - store "uptime" as "unit.<counter_name>"
		// - "load.0", "load.1", "load.2" => unclear what is going on
		//   with these values, leaving them out for now
		/*
		JsonArray loadArray = unit.getAsJsonArray("load");
		for (int i = 0; i < loadArray.size(); i++) {
			String metric = String.format("unit.load.%d", i);
			long load = loadArray.get(i).getAsLong();
			results.add(new StateRecord(localtime, metric, load, serialNumber));
		}
		*/
		long uptime = unit.get("uptime").getAsLong();
		results.add(
			new StateRecord(localtime, "unit.uptime", uptime, serialNumber)
		);
	}

	/** Parse state records into individual metrics. */
	private static List<StateRecord> parseStateRecords(List<KafkaRecord> records) {
		List<StateRecord> results = new ArrayList<>();
		for (KafkaRecord record : records) {
			try {
				parseStateRecord(record.serialNumber, record.payload, results);
			} catch (Exception e) {
				String errMsg = String.format(
					"Failed to parse state record: %s",
					record.payload.toString()
				);
				logger.error(errMsg, e);
				continue;
			}
		}
		return results;
	}

	/** Parse state records into individual metrics and insert into database. */
	private void insertStateRecordsToDatabase(List<KafkaRecord> records) {
		if (dbManager == null) {
			return;
		}

		List<StateRecord> dbRecords = parseStateRecords(records);
		try {
			dbManager.addStateRecords(dbRecords);
		} catch (SQLException e) {
			logger.error("Failed to insert state records into database", e);
			return;
		}
	}

	/**
	 * Add/overwrite a data listener with an arbitrary identifier.
	 *
	 * The "id" string determines the order in which listeners are called.
	 */
	public void addDataListener(String id, DataListener listener) {
		logger.debug("Adding data listener: {}", id);
		dataListeners.put(id, listener);
	}

	/**
	 * Remove a data listener with the given identifier, returning true if
	 * anything was actually removed.
	 */
	public boolean removeDataListener(String id) {
		logger.debug("Removing data listener: {}", id);
		return (dataListeners.remove(id) != null);
	}
}
