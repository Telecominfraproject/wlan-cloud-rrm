/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import com.facebook.openwifirrm.ucentral.UCentralConstants;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class DeviceDataManagerTest {
	@Test
	@Order(1)
	void testTopology() throws Exception {
		final String zoneA = "test-zone-A";
		final String zoneB = "test-zone-B";
		final String zoneUnknown = "test-zone-unknown";
		final String deviceA1 = "aaaaaaaaaa01";
		final String deviceA2 = "aaaaaaaaaa02";
		final String deviceB1 = "bbbbbbbbbb01";
		final String deviceUnknown = "000000abcdef";

		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Empty topology
		assertFalse(deviceDataManager.isDeviceInTopology(deviceA1));
		assertNull(deviceDataManager.getDeviceZone(deviceA1));
		assertFalse(deviceDataManager.isZoneInTopology(zoneA));

		// Create topology with zones [A, B]
		DeviceTopology topology = new DeviceTopology();
		topology.put(zoneA, new TreeSet<>(Arrays.asList(deviceA1, deviceA2)));
		topology.put(zoneB, new TreeSet<>(Arrays.asList(deviceB1)));
		deviceDataManager.setTopology(topology);

		// Test device/zone getters
		assertTrue(deviceDataManager.isDeviceInTopology(deviceA1));
		assertEquals(zoneA, deviceDataManager.getDeviceZone(deviceA1));
		assertTrue(deviceDataManager.isDeviceInTopology(deviceA2));
		assertEquals(zoneA, deviceDataManager.getDeviceZone(deviceA2));
		assertTrue(deviceDataManager.isDeviceInTopology(deviceB1));
		assertEquals(zoneB, deviceDataManager.getDeviceZone(deviceB1));
		assertFalse(deviceDataManager.isDeviceInTopology(deviceUnknown));
		assertNull(deviceDataManager.getDeviceZone(deviceUnknown));
		assertTrue(deviceDataManager.isZoneInTopology(zoneA));
		assertTrue(deviceDataManager.isZoneInTopology(zoneB));
		assertFalse(deviceDataManager.isZoneInTopology(zoneUnknown));
		assertEquals(Arrays.asList(zoneA, zoneB), deviceDataManager.getZones());

		// Minimal JSON sanity check
		assertFalse(deviceDataManager.getTopologyJson().isEmpty());
	}

	@Test
	@Order(2)
	void testTopologyErrorHandling() throws Exception {
		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Null/empty argument handling
		assertFalse(deviceDataManager.isDeviceInTopology(null));
		assertFalse(deviceDataManager.isDeviceInTopology(""));
		assertNull(deviceDataManager.getDeviceZone(null));
		assertNull(deviceDataManager.getDeviceZone(""));
		assertFalse(deviceDataManager.isZoneInTopology(null));
		assertFalse(deviceDataManager.isZoneInTopology(""));
	}

	@Test
	@Order(3)
	void testTopologyExceptions() throws Exception {
		final String zone = "test-zone";
		final String deviceA = "aaaaaaaaaaaa";

		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Null topology
		assertThrows(
			NullPointerException.class,
			() -> {
				deviceDataManager.setTopology(null);
			}
		);

		// Empty zone name
		final DeviceTopology topologyEmptyZone = new DeviceTopology();
		topologyEmptyZone.put("", new TreeSet<>(Arrays.asList(deviceA)));
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setTopology(topologyEmptyZone);
			}
		);

		// Empty serial number
		final DeviceTopology topologyEmptySerial = new DeviceTopology();
		topologyEmptySerial.put(zone, new TreeSet<>(Arrays.asList("")));
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setTopology(topologyEmptySerial);
			}
		);

		// Same device in multiple zones
		final DeviceTopology topologyDupSerial = new DeviceTopology();
		final String zone2 = zone + "-copy";
		topologyDupSerial.put(zone, new TreeSet<>(Arrays.asList(deviceA)));
		topologyDupSerial.put(zone2, new TreeSet<>(Arrays.asList(deviceA)));
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setTopology(topologyDupSerial);
			}
		);
	}

	@Test
	@Order(101)
	void testDeviceConfig() throws Exception {
		final String zoneA = "test-zone-A";
		final String zoneB = "test-zone-B";
		final String deviceA = "aaaaaaaaaa01";
		final String deviceB = "bbbbbbbbbb01";
		final String deviceUnknown = "000000abcdef";

		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Create topology with zones [A, B]
		DeviceTopology topology = new DeviceTopology();
		topology.put(zoneA, new TreeSet<>(Arrays.asList(deviceA)));
		topology.put(zoneB, new TreeSet<>(Arrays.asList(deviceB)));
		deviceDataManager.setTopology(topology);

		// Update network config
		final DeviceConfig networkCfg = new DeviceConfig();
		networkCfg.enableRRM = false;
		deviceDataManager.setDeviceNetworkConfig(networkCfg);

		// Update zone config
		final DeviceConfig zoneCfgA = new DeviceConfig();
		zoneCfgA.enableRRM = true;
		deviceDataManager.setDeviceZoneConfig(zoneA, zoneCfgA);

		// Update device config
		final DeviceConfig apCfgA = new DeviceConfig();
		final DeviceConfig apCfgB = new DeviceConfig();
		apCfgA.allowedChannels = new HashMap<>();
		apCfgA.allowedChannels
			.put(UCentralConstants.BAND_2G, Arrays.asList(6, 7));
		apCfgB.allowedChannels = new HashMap<>();
		apCfgB.allowedChannels
			.put(UCentralConstants.BAND_2G, Arrays.asList(1, 2, 3));
		// - use setter
		deviceDataManager.setDeviceApConfig(deviceA, apCfgA);
		// - use update function
		deviceDataManager.updateDeviceApConfig(apConfig -> {
			apConfig.put(deviceB, apCfgB);
			apConfig.put(deviceUnknown, new DeviceConfig());
		});

		// Check current layered device config
		DeviceConfig actualApCfgA = deviceDataManager.getDeviceConfig(deviceA);
		DeviceConfig actualApCfgB = deviceDataManager.getDeviceConfig(deviceB);
		assertNull(deviceDataManager.getDeviceConfig(deviceUnknown));
		assertNotNull(actualApCfgA);
		assertNotNull(actualApCfgB);
		assertTrue(actualApCfgA.enableRRM);
		assertFalse(actualApCfgB.enableRRM);
		assertEquals(
			2,
			actualApCfgA.allowedChannels.get(UCentralConstants.BAND_2G).size()
		);
		assertEquals(
			3,
			actualApCfgB.allowedChannels.get(UCentralConstants.BAND_2G).size()
		);
		DeviceConfig actualZoneCfgA = deviceDataManager.getZoneConfig(zoneA);
		assertNotNull(actualZoneCfgA);
		assertTrue(actualZoneCfgA.enableRRM);

		// Minimal JSON sanity check
		assertFalse(deviceDataManager.getDeviceLayeredConfigJson().isEmpty());

		// Setting null config at a single layer is allowed
		deviceDataManager.setDeviceNetworkConfig(null);
		deviceDataManager.setDeviceZoneConfig(zoneA, null);
		deviceDataManager.setDeviceApConfig(deviceA, null);

		// Setting whole layered config works (even with null fields)
		DeviceLayeredConfig nullLayeredCfg = new DeviceLayeredConfig();
		nullLayeredCfg.networkConfig = null;
		nullLayeredCfg.zoneConfig = null;
		nullLayeredCfg.apConfig = null;
		deviceDataManager.setDeviceLayeredConfig(nullLayeredCfg);
		deviceDataManager.setDeviceLayeredConfig(new DeviceLayeredConfig());
	}

	@Test
	@Order(102)
	void testDeviceConfigErrorHandling() throws Exception {
		final String zoneUnknown = "test-zone-unknown";
		final String deviceUnknown = "000000abcdef";

		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Unknown devices/zones (getters)
		assertNull(deviceDataManager.getDeviceConfig(deviceUnknown));
		assertNull(deviceDataManager.getAllDeviceConfigs(null));
		assertNull(deviceDataManager.getAllDeviceConfigs(zoneUnknown));
	}

	@Test
	@Order(103)
	void testDeviceConfigExceptions() throws Exception {
		final String zoneUnknown = "test-zone-unknown";
		final String deviceUnknown = "000000abcdef";

		DeviceDataManager deviceDataManager = new DeviceDataManager();

		// Null config
		assertThrows(
			NullPointerException.class,
			() -> {
				deviceDataManager.setDeviceLayeredConfig(null);
			}
		);

		// Null/empty arguments
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.getDeviceConfig(null);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.getDeviceConfig(null, zoneUnknown);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.getDeviceConfig(deviceUnknown, null);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceZoneConfig(null, null);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceZoneConfig("", null);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceApConfig(null, null);
			}
		);
		Assertions.assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceApConfig("", null);
			}
		);

		// Unknown devices/zones (setters)
		final DeviceConfig cfg = new DeviceConfig();
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceZoneConfig(zoneUnknown, cfg);
			}
		);
		assertThrows(
			IllegalArgumentException.class,
			() -> {
				deviceDataManager.setDeviceApConfig(deviceUnknown, cfg);
			}
		);
	}
}
