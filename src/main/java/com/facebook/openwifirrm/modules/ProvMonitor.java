/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ProvMonitorParams;
import com.facebook.openwifirrm.optimizers.ChannelOptimizer;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.TPC;
import com.facebook.openwifirrm.optimizers.UnmanagedApAwareChannelOptimizer;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTag;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTagList;
import com.facebook.openwifirrm.ucentral.prov.models.SerialNumberList;
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

	/** Unknown (i.e. empty/unset) venue name. */
	public static final String UNKNOWN_VENUE = "%OWPROV_UNKNOWN_VENUE%";

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
		while (!client.isProvInitialized()) {
			logger.trace("Waiting for uCentral client");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		// Fetch data from owprov
		// TODO: this may change later - for now, we only fetch inventory and
		// venues, using venue name (not UUID) as our "zone" in topology, and
		// ignoring "entity" completely
		InventoryTagList inventory = client.getProvInventory();
		SerialNumberList inventoryForRRM = client.getProvInventoryForRRM();
		VenueList venueList = client.getProvVenues();
		//EntityList entityList = client.getProvEntities();
		if (inventory == null || inventoryForRRM == null) {
			logger.error("Failed to fetch inventory from owprov");
			return;
		}
		if (venueList == null) {
			logger.error("Failed to fetch venues from owprov");
			return;
		}

		// Sync data
		syncDataToProv(inventory, inventoryForRRM, venueList);

		// TODO run periodic optimizations
		//runOptimizations(deviceDataManager, configManager, modeler);
	}

	/** Sync RRM topology and device configs with owprov data. */
	protected void syncDataToProv(
		InventoryTagList inventory,
		SerialNumberList inventoryForRRM,
		VenueList venueList
	) {
		// Sync topology
		// NOTE: this will wipe configs for any device that moved venues, etc.
		Map<String, String> venueIdToName = new HashMap<>();
		for (Venue venue : venueList.venues) {
			venueIdToName.put(venue.id, venue.name);
		}
		DeviceTopology topo = new DeviceTopology();
		for (InventoryTag tag : inventory.taglist) {
			String venue = !tag.venue.isEmpty()
				? venueIdToName.getOrDefault(tag.venue, tag.venue)
				: UNKNOWN_VENUE;
			Set<String> zone =
				topo.computeIfAbsent(venue, k -> new TreeSet<>());
			zone.add(tag.serialNumber);
		}
		deviceDataManager.setTopology(topo);
		logger.info(
			"Synced topology with owprov: {} zone(s), {} total device(s)",
			topo.size(),
			topo.values().stream().mapToInt(x -> x.size()).sum()
		);

		// Sync device configs
		// NOTE: this only sets the device layer, NOT the zone(venue) layer
		deviceDataManager.updateDeviceApConfig(configMap -> {
			// Pass 1: disable RRM on all devices
			for (InventoryTag tag : inventory.taglist) {
				DeviceConfig cfg = configMap.computeIfAbsent(
					tag.serialNumber, k -> new DeviceConfig()
				);
				cfg.enableRRM = cfg.enableConfig = cfg.enableWifiScan = false;
			}
			// Pass 2: re-enable RRM on specific devices
			for (String serialNumber : inventoryForRRM.serialNumbers) {
				DeviceConfig cfg = configMap.computeIfAbsent(
					serialNumber, k -> new DeviceConfig()
				);
				cfg.enableRRM = cfg.enableConfig = cfg.enableWifiScan = true;
			}
		});
		logger.info(
			"Synced device configs with owprov: RRM enabled on {}/{} device(s)",
			inventoryForRRM.serialNumbers.size(),
			inventory.taglist.size()
		);
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
