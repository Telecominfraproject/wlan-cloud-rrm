/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;

@TestMethodOrder(OrderAnnotation.class)
public class LocationBasedOptimalTPCTest {

	@Test
	@Order(1)
	void testPermutations() throws Exception {
		// n = 0 -> empty list
		List<List<Integer>> combinations1 = LocationBasedOptimalTPC
			.getPermutationsWithRepetitions(new ArrayList<>(Arrays.asList(29, 30)), 0);
		List<List<Integer>> expectedAnswer1 = new ArrayList<>();
		assertEquals(expectedAnswer1, combinations1);

		// n != 0 -> n^p combinations where p = the size of choices
		List<List<Integer>> combinations2 = LocationBasedOptimalTPC
			.getPermutationsWithRepetitions(new ArrayList<>(Arrays.asList(29, 30)), 2);
		List<List<Integer>> expectedAnswer2 = new ArrayList<>();
		expectedAnswer2.add(new ArrayList<>(Arrays.asList(29, 29)));
		expectedAnswer2.add(new ArrayList<>(Arrays.asList(29, 30)));
		expectedAnswer2.add(new ArrayList<>(Arrays.asList(30, 29)));
		expectedAnswer2.add(new ArrayList<>(Arrays.asList(30, 30)));
		assertEquals(expectedAnswer2, combinations2);
	}

	@Test
	@Order(2)
	void testRunLocationBasedOptimalTPC() throws Exception {
		List<Integer> txPowerList = LocationBasedOptimalTPC.runLocationBasedOptimalTPC(
			500,
			4,
			new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
			new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
			new ArrayList<>(Arrays.asList(29, 30))
		);
		assertEquals(new ArrayList<>(Arrays.asList(30, 30, 30, 29)), txPowerList);

		List<Integer> txPowerList2 = LocationBasedOptimalTPC.runLocationBasedOptimalTPC(
			500,
			4,
			new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
			new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
			new ArrayList<>(Arrays.asList(0, 1))
		);
		assertEquals(new ArrayList<>(Arrays.asList(30, 30, 30, 30)), txPowerList2);
	}

	@Test
	@Order(3)
	void testLocationBasedOptimalTPCSuccessCase() throws Exception {
		final String band = "5G";
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA = new DeviceConfig();
		final DeviceConfig apCfgB = new DeviceConfig();
		final DeviceConfig apCfgC = new DeviceConfig();
		apCfgA.boundary = 500;
		apCfgA.location = new ArrayList<>(Arrays.asList(408, 317));
		apCfgA.allowedTxPowers = new HashMap<>();
		apCfgA.allowedTxPowers.put(band, Arrays.asList(29, 30));
		apCfgB.boundary = 500;
		apCfgB.location = new ArrayList<>(Arrays.asList(453, 49));
		apCfgC.boundary = 500;
		apCfgC.location = new ArrayList<>(Arrays.asList(64, 140));
		deviceDataManager.setDeviceApConfig(deviceA, apCfgA);
		deviceDataManager.setDeviceApConfig(deviceB, apCfgB);
		deviceDataManager.setDeviceApConfig(deviceC, apCfgC);

		DataModel dataModel = new DataModel();
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(36, 20, dummyBssid)
		);
		Map<String, Map<String, Integer>> expected = new HashMap<>();
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, 30);
		expected.put(deviceA, radioMapA);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, 29);
		expected.put(deviceB, radioMapB);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, 30);
		expected.put(deviceC, radioMapC);

		LocationBasedOptimalTPC optimizer = new LocationBasedOptimalTPC(
				dataModel, TestUtils.TEST_ZONE, deviceDataManager
		);

		assertEquals(expected, optimizer.computeTxPowerMap()
		);

		// deviceB is removed due to negative location data.
		// The other two still participate the algorithm.
		DeviceDataManager deviceDataManager2 = new DeviceDataManager();
		deviceDataManager2.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA2 = new DeviceConfig();
		final DeviceConfig apCfgB2 = new DeviceConfig();
		final DeviceConfig apCfgC2 = new DeviceConfig();
		apCfgA2.boundary = 100;
		apCfgA2.location = new ArrayList<>(Arrays.asList(10, 10));
		apCfgB2.boundary = 50;
		apCfgB2.location = new ArrayList<>(Arrays.asList(-30, 10));
		apCfgC2.boundary = 100;
		apCfgC2.location = new ArrayList<>(Arrays.asList(90, 10));
		deviceDataManager2.setDeviceApConfig(deviceA, apCfgA2);
		deviceDataManager2.setDeviceApConfig(deviceB, apCfgB2);
		deviceDataManager2.setDeviceApConfig(deviceC, apCfgC2);

		DataModel dataModel2 = new DataModel();
		dataModel2.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceA, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel2.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceB, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel2.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceC, TestUtils.createState(36, 20, dummyBssid)
		);
		Map<String, Map<String, Integer>> expected2 = new HashMap<>();
		Map<String, Integer> radioMapA2 = new HashMap<>();
		radioMapA2.put(band, 30);
		expected2.put(deviceA, radioMapA2);
		Map<String, Integer> radioMapC2 = new HashMap<>();
		radioMapC2.put(band, 0);
		expected2.put(deviceC, radioMapC2);

		LocationBasedOptimalTPC optimizer5 = new LocationBasedOptimalTPC(
				dataModel2, TestUtils.TEST_ZONE, deviceDataManager2
		);

		assertEquals(expected2, optimizer5.computeTxPowerMap()
		);
	}
	@Test
	@Order(4)
	void testLocationBasedOptimalTPCFailedCase() throws Exception {
		final String band = "5G";
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";

		// No invalid APs, missing location data!
		DeviceDataManager deviceDataManager1 = new DeviceDataManager();
		deviceDataManager1.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA1 = new DeviceConfig();
		final DeviceConfig apCfgB1 = new DeviceConfig();
		final DeviceConfig apCfgC1 = new DeviceConfig();
		deviceDataManager1.setDeviceApConfig(deviceA, apCfgA1);
		deviceDataManager1.setDeviceApConfig(deviceB, apCfgB1);
		deviceDataManager1.setDeviceApConfig(deviceC, apCfgC1);

		DataModel dataModel1 = new DataModel();
		Map<String, Map<String, Integer>> expected1 = new HashMap<>();

		LocationBasedOptimalTPC optimizer1 = new LocationBasedOptimalTPC(
				dataModel1, TestUtils.TEST_ZONE, deviceDataManager1
		);

		assertEquals(expected1, optimizer1.computeTxPowerMap()
		);

		// Invalid boundary since it is smaller than the given location!
		DeviceDataManager deviceDataManager2 = new DeviceDataManager();
		deviceDataManager2.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA2 = new DeviceConfig();
		final DeviceConfig apCfgB2 = new DeviceConfig();
		final DeviceConfig apCfgC2 = new DeviceConfig();
		apCfgA2.boundary = 100;
		apCfgA2.location = new ArrayList<>(Arrays.asList(10, 10));
		apCfgB2.boundary = 50;
		apCfgB2.location = new ArrayList<>(Arrays.asList(30, 10));
		apCfgC2.boundary = 100;
		apCfgC2.location = new ArrayList<>(Arrays.asList(110, 10));
		deviceDataManager2.setDeviceApConfig(deviceA, apCfgA2);
		deviceDataManager2.setDeviceApConfig(deviceB, apCfgB2);
		deviceDataManager2.setDeviceApConfig(deviceC, apCfgC2);

		DataModel dataModel2 = new DataModel();
		dataModel2.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceA, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel2.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceB, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel2.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel2.latestState.put(
			deviceC, TestUtils.createState(36, 20, dummyBssid)
		);
		Map<String, Map<String, Integer>> expected2 = new HashMap<>();

		LocationBasedOptimalTPC optimizer2 = new LocationBasedOptimalTPC(
				dataModel2, TestUtils.TEST_ZONE, deviceDataManager2
		);

		assertEquals(expected2, optimizer2.computeTxPowerMap()
		);

		// Invalid txPower choices! The intersection is an empty set.
		DeviceDataManager deviceDataManager3 = new DeviceDataManager();
		deviceDataManager3.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA3 = new DeviceConfig();
		final DeviceConfig apCfgB3 = new DeviceConfig();
		final DeviceConfig apCfgC3 = new DeviceConfig();
		apCfgA3.boundary = 100;
		apCfgA3.location = new ArrayList<>(Arrays.asList(10, 10));
		apCfgA3.allowedTxPowers = new HashMap<>();
		apCfgA3.allowedTxPowers.put(band, Arrays.asList(1, 2));
		apCfgB3.boundary = 100;
		apCfgB3.location = new ArrayList<>(Arrays.asList(30, 10));
		apCfgB3.allowedTxPowers = new HashMap<>();
		apCfgB3.allowedTxPowers.put(band, Arrays.asList(3, 4));
		apCfgC3.boundary = 100;
		apCfgC3.location = new ArrayList<>(Arrays.asList(50, 10));
		deviceDataManager3.setDeviceApConfig(deviceA, apCfgA3);
		deviceDataManager3.setDeviceApConfig(deviceB, apCfgB3);
		deviceDataManager3.setDeviceApConfig(deviceC, apCfgC3);

		DataModel dataModel3 = new DataModel();
		dataModel3.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel3.latestState.put(
			deviceA, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel3.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel3.latestState.put(
			deviceB, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel3.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel3.latestState.put(
			deviceC, TestUtils.createState(36, 20, dummyBssid)
		);
		Map<String, Map<String, Integer>> expected3 = new HashMap<>();

		LocationBasedOptimalTPC optimizer3 = new LocationBasedOptimalTPC(
				dataModel3, TestUtils.TEST_ZONE, deviceDataManager3
		);

		assertEquals(expected3, optimizer3.computeTxPowerMap()
		);

		// Invalid operation! Complexity issue!
		DeviceDataManager deviceDataManager4 = new DeviceDataManager();
		deviceDataManager4.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA4 = new DeviceConfig();
		final DeviceConfig apCfgB4 = new DeviceConfig();
		final DeviceConfig apCfgC4 = new DeviceConfig();
		apCfgA4.boundary = 100;
		apCfgA4.location = new ArrayList<>(Arrays.asList(10, 10));
		apCfgB4.boundary = 100;
		apCfgB4.location = new ArrayList<>(Arrays.asList(30, 10));
		apCfgC4.boundary = 100;
		apCfgC4.location = new ArrayList<>(Arrays.asList(50, 10));
		deviceDataManager4.setDeviceApConfig(deviceA, apCfgA4);
		deviceDataManager4.setDeviceApConfig(deviceB, apCfgB4);
		deviceDataManager4.setDeviceApConfig(deviceC, apCfgC4);

		DataModel dataModel4 = new DataModel();
		dataModel4.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel4.latestState.put(
			deviceA, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel4.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel4.latestState.put(
			deviceB, TestUtils.createState(36, 20, dummyBssid)
		);
		dataModel4.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel4.latestState.put(
			deviceC, TestUtils.createState(36, 20, dummyBssid)
		);
		Map<String, Map<String, Integer>> expected4 = new HashMap<>();

		LocationBasedOptimalTPC optimizer4 = new LocationBasedOptimalTPC(
				dataModel4, TestUtils.TEST_ZONE, deviceDataManager4
		);

		assertEquals(expected4, optimizer4.computeTxPowerMap()
		);
	}
}
