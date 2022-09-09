/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.RRMAlgorithm;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ProvMonitorParams;
import com.facebook.openwifirrm.RRMSchedule;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTag;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTagList;
import com.facebook.openwifirrm.ucentral.prov.models.RRMAlgorithmDetails;
import com.facebook.openwifirrm.ucentral.prov.models.RRMDetails;
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
	private static final Logger logger =
		LoggerFactory.getLogger(ProvMonitor.class);

	/** Unknown (i.e. empty/unset) venue name. */
	public static final String UNKNOWN_VENUE = "%OWPROV_UNKNOWN_VENUE%";

	/** The module parameters. */
	private final ProvMonitorParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The Modeler module instance. */
	private final Modeler modeler;

	/** The uCentral client. */
	private final UCentralClient client;

	/** The RRM scheduler. */
	private final RRMScheduler scheduler;

	/** Constructor. */
	public ProvMonitor(
		ProvMonitorParams params,
		DeviceDataManager deviceDataManager,
		Modeler modeler,
		UCentralClient client,
		RRMScheduler scheduler
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
		this.modeler = modeler;
		this.client = client;
		this.scheduler = scheduler;
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
		//EntityList entityList = client.getProvEntities();
		if (inventory == null || inventoryForRRM == null) {
			logger.error("Failed to fetch inventory from owprov");
			return;
		}

		// fetch the RRM details for each AP that has RRM enabled
		Map<String, RRMDetails> rrmDetails = new HashMap<String, RRMDetails>();
		for (String serialNumber : inventoryForRRM.serialNumbers) {
			RRMDetails details =
				client.getProvInventoryRrmDetails(serialNumber);
			if (details == null) {
				logger
					.error("Could not fetch RRM details for {}", serialNumber);
				continue;
			}

			rrmDetails.put(serialNumber, details);
		}

		VenueList venueList = client.getProvVenues();
		if (venueList == null) {
			logger.error("Failed to fetch venues from owprov");
			return;
		}

		// Sync data
		syncDataToProv(inventory, inventoryForRRM, rrmDetails, venueList);
	}

	/** Sync RRM topology and device configs with owprov data. */
	protected void syncDataToProv(
		InventoryTagList inventory,
		SerialNumberList inventoryForRRM,
		Map<String, RRMDetails> rrmDetails,
		VenueList venueList
	) {
		// TODO sync RRM schedules per venue

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

		try {
			deviceDataManager.setTopology(topo);
		} catch (IllegalArgumentException e) {
			logger.error(
				"Invalid topology received from owprov, aborting sync",
				e
			);
			return;
		}

		logger.info(
			"Synced topology with owprov: {} zone(s), {} total device(s)",
			topo.size(),
			topo.values().stream().mapToInt(x -> x.size()).sum()
		);

		// Sync device configs
		// NOTE: this only sets the device layer, NOT the zone(venue) layer
		deviceDataManager.updateDeviceApConfig(
			configMap -> {
				// Pass 1: disable RRM on all devices
				for (InventoryTag tag : inventory.taglist) {
					DeviceConfig cfg = configMap.computeIfAbsent(
						tag.serialNumber,
						k -> new DeviceConfig()
					);
					cfg.enableRRM =
						cfg.enableConfig = cfg.enableWifiScan = false;
				}

				// Pass 2: re-enable RRM on specific devices
				for (String serialNumber : inventoryForRRM.serialNumbers) {
					DeviceConfig cfg = configMap.computeIfAbsent(
						serialNumber,
						k -> new DeviceConfig()
					);
					cfg.enableRRM =
						cfg.enableConfig = cfg.enableWifiScan = true;

					// read the details from the config
					RRMDetails details = rrmDetails.get(serialNumber);
					if (details == null) {
						logger.error(
							"No RRM details available for {} even though it has RRM enabled",
							serialNumber
						);
						continue;
					}

					cfg.schedule = new RRMSchedule();
					if (details.rrm != null) {
						cfg.schedule.cron = details.rrm.schedule;

						if (details.rrm.algorithms != null) {
							cfg.schedule.algorithms =
								new ArrayList<RRMAlgorithm>();
							for (
								RRMAlgorithmDetails algorithmDetails : details.rrm.algorithms
							) {
								cfg.schedule.algorithms.add(
									RRMAlgorithm.parse(
										algorithmDetails.name,
										algorithmDetails.parameters
									)
								);
							}
						}
						logger.info("Set RRM settings for {}", serialNumber);
					} else {
						logger
							.error("RRM setting for {} is null", serialNumber);
					}
				}
			}
		);
		logger.info(
			"Synced device configs with owprov: RRM enabled on {}/{} device(s)",
			inventoryForRRM.serialNumbers.size(),
			inventory.taglist.size()
		);

		// Revalidate data model
		modeler.revalidate();

		// Update scheduler
		// TODO [ZoneBasedRrmScheduling] move this to syncTriggersForZones once
		// that API is available and change it to be named just `syncTriggers`
		scheduler.syncTriggersForDevices();
	}
}
