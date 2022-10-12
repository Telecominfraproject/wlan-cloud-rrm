/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.cloudsdk.UCentralClient;
import com.facebook.openwifi.cloudsdk.kafka.KafkaRunner;
import com.facebook.openwifi.cloudsdk.kafka.UCentralKafkaConsumer;
import com.facebook.openwifi.cloudsdk.kafka.UCentralKafkaProducer;
import com.facebook.openwifi.cloudsdk.models.gw.SystemInfoResults;
import com.facebook.openwifi.rrm.modules.ApiServer;
import com.facebook.openwifi.rrm.modules.ConfigManager;
import com.facebook.openwifi.rrm.modules.DataCollector;
import com.facebook.openwifi.rrm.modules.Modeler;
import com.facebook.openwifi.rrm.modules.ProvMonitor;
import com.facebook.openwifi.rrm.modules.RRMScheduler;
import com.facebook.openwifi.rrm.mysql.DatabaseManager;

/**
 * RRM service runner.
 */
public class RRM {
	private static final Logger logger = LoggerFactory.getLogger(RRM.class);

	/** The executor service instance. */
	private final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Wrap a {@code Runnable} as a {@code Callable<Object>}.
	 *
	 * This is similar to {@link Executors#callable(Runnable)} but will log any
	 * exceptions thrown and then call {@link System#exit(int)}, and will also
	 * name the threads.
	 */
	private static Callable<Object> wrapRunnable(Runnable task) {
		return () -> {
			try {
				Thread.currentThread()
					.setName("RRM_" + task.getClass().getSimpleName());
				task.run();
			} catch (Exception e) {
				logger.error("Exception raised in task!", e);
				System.exit(1);
			}
			return null;
		};
	}

	/** Start the RRM service. */
	public boolean start(
		RRMConfig config,
		DeviceDataManager deviceDataManager,
		UCentralClient client,
		UCentralKafkaConsumer consumer,
		UCentralKafkaProducer producer,
		DatabaseManager dbManager
	) {
		// If using public endpoints, log into uCentral now
		if (config.uCentralConfig.usePublicEndpoints) {
			// uCentral login
			if (!client.login()) {
				logger.error("uCentral login failed! Terminating...");
				return false;
			}
			// Check that uCentralGw is actually alive
			SystemInfoResults systemInfo = client.getSystemInfo();
			if (systemInfo == null) {
				logger.error(
					"Failed to fetch uCentralGw system info. Terminating..."
				);
				return false;
			}
			logger.info("uCentralGw version: {}", systemInfo.version);
		}

		// Instantiate modules
		RRMScheduler scheduler = new RRMScheduler(
			config.moduleConfig.schedulerParams,
			deviceDataManager
		);
		ConfigManager configManager = new ConfigManager(
			config.moduleConfig.configManagerParams,
			deviceDataManager,
			client
		);
		DataCollector dataCollector = new DataCollector(
			config.moduleConfig.dataCollectorParams,
			deviceDataManager,
			client,
			consumer,
			configManager,
			dbManager
		);
		Modeler modeler = new Modeler(
			config.moduleConfig.modelerParams,
			deviceDataManager,
			consumer,
			client,
			dataCollector,
			configManager
		);
		ApiServer apiServer = new ApiServer(
			config.moduleConfig.apiServerParams,
			config.serviceConfig,
			deviceDataManager,
			configManager,
			modeler,
			client,
			scheduler
		);
		ProvMonitor provMonitor =
			config.moduleConfig.provMonitorParams.useVenues
				? new ProvMonitor(
					config.moduleConfig.provMonitorParams,
					deviceDataManager,
					modeler,
					client,
					scheduler
				) : null;
		KafkaRunner kafkaRunner = (consumer == null && producer == null)
			? null : new KafkaRunner(consumer, producer);

		// Add shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			logger.debug("Running shutdown hook...");
			if (kafkaRunner != null) {
				kafkaRunner.shutdown();
			}
			apiServer.shutdown();
			dataCollector.shutdown();
			executor.shutdownNow();
			scheduler.shutdown();
		}));

		// Start scheduler (runs separately from executor)
		scheduler.start(configManager, modeler);

		// Submit jobs
		List<Callable<Object>> services = Arrays
			.asList(
				configManager,
				dataCollector,
				modeler,
				apiServer,
				provMonitor,
				kafkaRunner
			)
			.stream()
			.filter(o -> o != null)
			.map(RRM::wrapRunnable)
			.collect(Collectors.toList());
		try {
			executor.invokeAll(services);
		} catch (InterruptedException e) {
			logger.info("Execution interrupted!", e);
			return true;
		}

		// All jobs crashed?
		return false;
	}
}
