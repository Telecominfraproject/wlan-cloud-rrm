/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ProvMonitorParams;
import com.facebook.openwifirrm.optimizers.ChannelOptimizer;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.TPC;
import com.facebook.openwifirrm.optimizers.UnmanagedApAwareChannelOptimizer;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.prov.models.Venue;
import com.facebook.openwifirrm.ucentral.prov.models.VenueList;

 /**
 * owprov monitor module.
 * <p>
 * Periodically updates our view of topology using owprov venue information.
 * Also handles periodic optimization, based on owprov configuration.
 */
public class ProvMonitor implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ProvMonitor.class);

	/** The module parameters. */
	private final ProvMonitorParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The Modeler module instance. */
	private final Modeler modeler;

	/** The ConfigManager module instance. */
	private final ConfigManager configManager;

	/** The uCentral client. */
	private final UCentralClient client;

	/** Constructor. */
	public ProvMonitor(
		ProvMonitorParams params,
		ConfigManager configManager,
		DeviceDataManager deviceDataManager,
		Modeler modeler,
		UCentralClient client
	) {
		this.params = params;
		this.configManager = configManager;
		this.deviceDataManager = deviceDataManager;
		this.modeler = modeler;
		this.client = client;
	}

	@Override
	public void run() {
		logger.info("Starting ProvMonitor");

		// Run application logic in a periodic loop
		while (!Thread.currentThread().isInterrupted()) {
			try {
				runImpl();
				Thread.sleep(params.syncIntervalMs);
			} catch (InterruptedException e) {
				logger.error("Interrupted!", e);
				break;
			}
		}
		logger.error("Thread terminated!");
	}

	/** Run single iteration of application logic. */
	private void runImpl() {
		if (!client.isInitialized()) {
			logger.trace("Waiting for uCentral client");
			return;
		}

		// Fetch venues
		VenueList venueList = client.getVenues();
		if (venueList == null) {
			logger.error("Venue list request failed");
			return;
		}

		// Sync topology to venue list
		DeviceTopology topo = buildTopology(venueList);
		deviceDataManager.setTopology(topo);

		// TODO run periodic optimizations
		//runOptimizations(deviceDataManager, configManager, modeler);
	}

	/** Build new topology from VenueList */
	protected DeviceTopology buildTopology(VenueList venueList) {
		// TODO: Look at entity hierarchy to understand what has RRM enabled
		DeviceTopology topo = new DeviceTopology();
		for (Venue venue : venueList.venues) {
			String zone = venue.id;
			Set<String> devices = new TreeSet<>(venue.devices);
			topo.put(zone, devices);
		}
		return topo;
	}

	/** Running tx power and channel optimizations for all RRM-enabled venues */
	protected void runOptimizations(
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Modeler modeler
	) {
		DeviceTopology topo = deviceDataManager.getTopologyCopy();

		for (Map.Entry<String, Set<String>> e : topo.entrySet()) {
			String zone = e.getKey();
			logger.info(
				"Running periodic optimizations\n Zone: {}\n Devices: {}",
				zone,
				e.getValue()
			);
			ChannelOptimizer channelOptimizer = new UnmanagedApAwareChannelOptimizer(
				modeler.getDataModelCopy(), zone, deviceDataManager
			);
			TPC txOptimizer = new MeasurementBasedApApTPC(
				modeler.getDataModelCopy(), zone, deviceDataManager
			);

			Map<String, Map<String, Integer>> channelMap =
				channelOptimizer.computeChannelMap();
			Map<String, Map<String, Integer>> txPowerMap =
				txOptimizer.computeTxPowerMap();

			channelOptimizer.applyConfig(
				deviceDataManager, configManager, channelMap
			);
			txOptimizer.applyConfig(
				deviceDataManager, configManager, txPowerMap
			);
		}
	}
}
