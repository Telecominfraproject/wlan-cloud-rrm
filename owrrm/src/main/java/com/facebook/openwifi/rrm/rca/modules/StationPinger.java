/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.rca.modules;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.cloudsdk.UCentralClient;
import com.facebook.openwifi.cloudsdk.UCentralUtils;
import com.facebook.openwifi.cloudsdk.kafka.UCentralKafkaConsumer;
import com.facebook.openwifi.cloudsdk.kafka.UCentralKafkaConsumer.KafkaRecord;
import com.facebook.openwifi.cloudsdk.models.ap.State;
import com.facebook.openwifi.cloudsdk.models.gw.ServiceEvent;
import com.facebook.openwifi.rrm.Utils;
import com.facebook.openwifi.rrm.rca.RCAConfig.StationPingerParams;
import com.facebook.openwifi.rrm.rca.RCAUtils;
import com.facebook.openwifi.rrm.rca.RCAUtils.PingResult;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

/**
 * Ping service to measure latency/jitter between Wi-Fi APs and clients
 * (stations).
 * <p>
 * This class subscribes to the Kafka "state" topic to retrieve the list of APs
 * with connected clients, then issues ping commands for each (AP, STA) pair at
 * a given frequency. All actions are submitted to an executor during Kafka
 * callbacks.
 */
public class StationPinger {
	private static final Logger logger =
		LoggerFactory.getLogger(StationPinger.class);

	/** Drop records older than this interval (in ms). */
	private static final long STATE_STALE_THRESHOLD_MS = 300000; // 5 min

	/** The module parameters. */
	private final StationPingerParams params;

	/** The uCentral client. */
	private final UCentralClient client;

	/** The executor service instance. */
	private final ExecutorService executor;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/**
	 * Map from device (serial number) to the latest map of STAs
	 * (i.e. client MAC address to Client structure).
	 */
	private Map<String, Map<String, State.Interface.Client>> deviceToClients =
		new ConcurrentHashMap<>();

	/**
	 * Map of last ping timestamps, keyed on
	 * {@link #getDeviceKey(String, String)}.
	 */
	private Map<String, Long> lastPingTsMap = new ConcurrentHashMap<>();

	/** Constructor. */
	public StationPinger(
		StationPingerParams params,
		UCentralClient client,
		UCentralKafkaConsumer consumer
	) {
		this.params = params;
		this.client = client;
		this.executor =
			Executors.newFixedThreadPool(
				params.executorThreadCount,
				new Utils.NamedThreadFactory(
					"RCA_" + this.getClass().getSimpleName()
				)
			);

		if (params.pingIntervalSec < 1) {
			logger.info("StationPinger is disabled");
			return; // quit before registering listeners
		}

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
					) { /* ignored */ }

					@Override
					public void handleServiceEventRecords(
						List<ServiceEvent> serviceEventRecords
					) { /* ignored */ }
				}
			);
		}
	}

	/** Process the list of received State records. */
	private void handleKafkaStateRecords(List<KafkaRecord> records) {
		long now = System.currentTimeMillis();
		for (KafkaRecord record : records) {
			// Drop old records
			if (now - record.timestampMs > STATE_STALE_THRESHOLD_MS) {
				logger.debug(
					"Dropping old state record for {} at time {}",
					record.serialNumber,
					record.timestampMs
				);
				continue;
			}

			// Deserialize State
			JsonObject state = record.payload.getAsJsonObject("state");
			if (state == null) {
				continue;
			}
			try {
				State stateModel = gson.fromJson(state, State.class);
				Map<String, State.Interface.Client> clientMap =
					UCentralUtils.getWifiClientInfo(stateModel);
				if (
					deviceToClients.put(record.serialNumber, clientMap) == null
				) {
					// Enqueue this device
					final String serialNumber = record.serialNumber;
					executor.submit(() -> pingDevices(serialNumber));
				}
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

	/** Shut down all resources. */
	public void shutdown() {
		executor.shutdownNow();
	}

	/**
	 * Issue ping commands to all clients of a given AP.
	 *
	 * Note that this is intentionally NOT parallelized to avoid collisions
	 * while transmitting to/from multiple clients of the same AP.
	 */
	private void pingDevices(String serialNumber) {
		Map<String, State.Interface.Client> clientMap =
			deviceToClients.get(serialNumber);
		if (clientMap == null) {
			return; // shouldn't happen
		}

		logger.trace(
			"{}: Pinging all clients ({} total)...",
			serialNumber,
			clientMap.size()
		);
		final long PING_INTERVAL_NS =
			Math.max(params.pingIntervalSec, 1) * 1_000_000_000L;
		for (
			Map.Entry<String, State.Interface.Client> entry : clientMap
				.entrySet()
		) {
			String mac = entry.getKey();
			String host = getClientAddress(entry.getValue());
			if (host == null) {
				logger.debug(
					"{}: client {} has no pingable address",
					serialNumber,
					mac
				);
				continue;
			}

			// Check backoff timer
			long now = System.nanoTime();
			String deviceKey = getDeviceKey(serialNumber, mac);
			Long lastPingTs = lastPingTsMap.putIfAbsent(deviceKey, now);
			if (lastPingTs != null && now - lastPingTs < PING_INTERVAL_NS) {
				logger.trace(
					"{}: Skipping ping for {} (last pinged {}s ago)",
					serialNumber,
					mac,
					(now - lastPingTs) / 1_000_000_000L
				);
				continue;
			}
			lastPingTsMap.put(deviceKey, now);

			// Issue ping command
			logger.debug(
				"{}: Pinging client {} ({})",
				serialNumber,
				mac,
				host
			);
			PingResult result = RCAUtils
				.pingFromDevice(client, serialNumber, host, params.pingCount);
			if (result == null) {
				logger.debug(
					"Ping failed from {} to {} ({})",
					serialNumber,
					mac,
					host
				);
				continue;
			}
			// TODO handle results
			logger.info(
				"Ping result from {} to {} ({}): {}",
				serialNumber,
				mac,
				host,
				result.toString()
			);
		}

		// Remove map entries after we process them
		deviceToClients.remove(serialNumber);
	}

	/** Return an address to ping for the given client. */
	private String getClientAddress(State.Interface.Client client) {
		if (client.ipv4_addresses.length > 0) {
			return client.ipv4_addresses[0];
		} else if (client.ipv6_addresses.length > 0) {
			return client.ipv6_addresses[0];
		} else {
			return null;
		}
	}

	/** Return a key to use in {@link #lastPingTsMap}. */
	private String getDeviceKey(String serialNumber, String sta) {
		// Use (AP, STA) pair as the key to handle STAs moving between APs
		// TODO - do we care about radio/band/channel changes too?
		return serialNumber + '\0' + sta;
	}
}
