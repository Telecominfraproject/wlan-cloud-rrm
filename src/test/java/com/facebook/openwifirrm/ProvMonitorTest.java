/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.facebook.openwifirrm.modules.ConfigManager;
import com.facebook.openwifirrm.modules.DataCollector;
import com.facebook.openwifirrm.modules.Modeler;
import com.facebook.openwifirrm.mysql.DatabaseManager;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer;
import com.facebook.openwifirrm.ucentral.prov.models.Venue;
import com.facebook.openwifirrm.ucentral.prov.models.VenueList;

public class ProvMonitorTest {
	/** Test device data manager. */
	private DeviceDataManager deviceDataManager;

	/** Test provisioning monitor. */
	private ProvMonitor provMonitor;

	@BeforeEach
	void setup(TestInfo testInfo) {
		this.deviceDataManager = new DeviceDataManager();

		// Create config
		RRMConfig rrmConfig = new RRMConfig();

		// Create clients (null for now)
		UCentralClient client = null;
		UCentralKafkaConsumer consumer = null;
		DatabaseManager dbManager = null;

		// Instantiate dependent instances
		ConfigManager configManager = new ConfigManager(
			rrmConfig.moduleConfig.configManagerParams,
			deviceDataManager,
			client
		);
		DataCollector dataCollector = new DataCollector(
			rrmConfig.moduleConfig.dataCollectorParams,
			deviceDataManager,
			client,
			consumer,
			configManager,
			dbManager
		);
		Modeler modeler = new Modeler(
			rrmConfig.moduleConfig.modelerParams,
			deviceDataManager,
			consumer,
			client,
			dataCollector,
			configManager
		);

		// Instantiate ProvMonitor
		this.provMonitor = new ProvMonitor(
			configManager,
			deviceDataManager,
			modeler,
			null,
			1
		);
	}

	@Test
	@Order(1)
	void test_buildTopology() throws Exception {
		// First test case - empty VenueList
		VenueList venueList = new VenueList();
		venueList.venues = List.of();
		DeviceTopology topo = provMonitor.buildTopology(venueList);
		assertTrue(topo.isEmpty());

		// Second test case - filled VenueList
		Venue venue1 = new Venue();
		venue1.id = "id1";
		venue1.entity = "entity1";
		venue1.entity = "zone1";
		venue1.devices = List.of("device1", "device2");

		Venue venue2 = new Venue();
		venue2.id = "id2";
		venue2.entity = "entity2";
		venue2.entity = "zone2";
		venue2.devices = List.of("device3");

		venueList.venues = List.of(venue1, venue2);
		topo = provMonitor.buildTopology(venueList);
		assertEquals(2, topo.size());
		assertEquals(2, topo.get("id1").size());
	}
}
