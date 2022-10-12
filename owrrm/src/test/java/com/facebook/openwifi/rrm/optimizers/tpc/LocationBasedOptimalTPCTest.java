/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.tpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifi.cloudsdk.UCentralConstants;
import com.facebook.openwifi.cloudsdk.UCentralUtils;
import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.modules.Modeler.DataModel;
import com.facebook.openwifi.rrm.optimizers.TestUtils;

@TestMethodOrder(OrderAnnotation.class)
public class LocationBasedOptimalTPCTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";
	/** Default channel width (MHz). */
	private static final int DEFAULT_CHANNEL_WIDTH = 20;
	/** Default tx power. */
	private static final int DEFAULT_TX_POWER = 20;
	/** Default 2G channel. */
	private static final int DEFAULT_CHANNEL_2G =
		UCentralUtils.getLowerChannelLimit(UCentralConstants.BAND_2G);
	/** Default 5G channel. */
	private static final int DEFAULT_CHANNEL_5G =
		UCentralUtils.getLowerChannelLimit(UCentralConstants.BAND_5G);

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
		List<Integer> txPowerList =
			LocationBasedOptimalTPC.runLocationBasedOptimalTPC(
				500,
				4,
				new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
				new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
				new ArrayList<>(Arrays.asList(29, 30))
			);
		assertEquals(
			new ArrayList<>(Arrays.asList(30, 30, 30, 29)),
			txPowerList
		);

		List<Integer> txPowerList2 =
			LocationBasedOptimalTPC.runLocationBasedOptimalTPC(
				500,
				4,
				new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
				new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
				new ArrayList<>(Arrays.asList(0, 1))
			);
		assertEquals(
			new ArrayList<>(Arrays.asList(1, 1, 1, 1)),
			txPowerList2
		);
	}

	@Test
	@Order(3)
	void testLocationBasedOptimalTPCSuccessCase() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA = new DeviceConfig();
		final DeviceConfig apCfgB = new DeviceConfig();
		final DeviceConfig apCfgC = new DeviceConfig();
		apCfgA.boundary = 500;
		apCfgA.location = new ArrayList<>(Arrays.asList(408, 317));
		apCfgA.allowedTxPowers = new HashMap<>();
		UCentralConstants.BANDS.stream()
			.forEach(
				band -> apCfgA.allowedTxPowers.put(band, Arrays.asList(29, 30))
			);
		apCfgB.boundary = 500;
		apCfgB.location = new ArrayList<>(Arrays.asList(453, 49));
		apCfgC.boundary = 500;
		apCfgC.location = new ArrayList<>(Arrays.asList(64, 140));
		deviceDataManager.setDeviceApConfig(deviceA, apCfgA);
		deviceDataManager.setDeviceApConfig(deviceB, apCfgB);
		deviceDataManager.setDeviceApConfig(deviceC, apCfgC);

		DataModel dataModel = new DataModel();
		for (String device : Arrays.asList(deviceA, deviceB, deviceC)) {
			dataModel.latestDeviceStatusRadios.put(
				device,
				TestUtils.createDeviceStatus(UCentralConstants.BANDS)
			);
			dataModel.latestStates.put(
				device,
				Arrays.asList(
					TestUtils.createState(
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid,
						DEFAULT_CHANNEL_5G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid
					)
				)
			);
		}

		Map<String, Map<String, Integer>> expected = new HashMap<>();
		for (String band : UCentralConstants.BANDS) {
			expected.computeIfAbsent(deviceA, k -> new TreeMap<>())
				.put(
					band,
					30
				);
			expected.computeIfAbsent(deviceB, k -> new TreeMap<>())
				.put(
					band,
					29
				);
			expected.computeIfAbsent(deviceC, k -> new TreeMap<>())
				.put(
					band,
					30
				);
		}

		LocationBasedOptimalTPC optimizer = new LocationBasedOptimalTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager
		);

		assertEquals(expected, optimizer.computeTxPowerMap());

		// deviceB is removed due to negative location data.
		// The other two still participate the algorithm.
		DeviceDataManager deviceDataManager2 = new DeviceDataManager();
		deviceDataManager2.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
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
		for (String device : Arrays.asList(deviceA, deviceB)) {
			dataModel2.latestDeviceStatusRadios.put(
				device,
				TestUtils.createDeviceStatus(UCentralConstants.BANDS)
			);
			dataModel2.latestStates.put(
				device,
				Arrays.asList(
					TestUtils.createState(
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid,
						DEFAULT_CHANNEL_5G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid
					)
				)
			);
		}
		dataModel2.latestDeviceStatusRadios
			.put(
				deviceC,
				TestUtils.createDeviceStatus(
					Arrays.asList(UCentralConstants.BAND_5G)
				)
			);
		dataModel2.latestStates.put(
			deviceC,
			Arrays.asList(
				TestUtils.createState(
					DEFAULT_CHANNEL_5G,
					DEFAULT_CHANNEL_WIDTH,
					DEFAULT_TX_POWER,
					dummyBssid
				)
			)
		);

		Map<String, Map<String, Integer>> expected2 = new HashMap<>();
		for (String band : UCentralConstants.BANDS) {
			expected2.computeIfAbsent(deviceA, k -> new TreeMap<>())
				.put(
					band,
					30
				);
		}
		expected2.computeIfAbsent(deviceC, k -> new TreeMap<>())
			.put(
				UCentralConstants.BAND_5G,
				0
			);

		LocationBasedOptimalTPC optimizer2 = new LocationBasedOptimalTPC(
			dataModel2,
			TEST_ZONE,
			deviceDataManager2
		);

		assertEquals(expected2, optimizer2.computeTxPowerMap());
	}

	@Test
	@Order(4)
	void testLocationBasedOptimalTPCFailedCase() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";

		// No invalid APs, missing location data!
		DeviceDataManager deviceDataManager1 = new DeviceDataManager();
		deviceDataManager1.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
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
			dataModel1,
			TEST_ZONE,
			deviceDataManager1
		);

		assertEquals(expected1, optimizer1.computeTxPowerMap());

		// Invalid boundary since it is smaller than the given location!
		DeviceDataManager deviceDataManager2 = new DeviceDataManager();
		deviceDataManager2.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
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
		for (String device : Arrays.asList(deviceA, deviceB, deviceC)) {
			dataModel2.latestDeviceStatusRadios.put(
				device,
				TestUtils.createDeviceStatus(UCentralConstants.BANDS)
			);
			dataModel2.latestStates.put(
				device,
				Arrays.asList(
					TestUtils.createState(
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid,
						DEFAULT_CHANNEL_5G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid
					)
				)
			);
		}

		Map<String, Map<String, Integer>> expected2 = new HashMap<>();

		LocationBasedOptimalTPC optimizer2 = new LocationBasedOptimalTPC(
			dataModel2,
			TEST_ZONE,
			deviceDataManager2
		);

		assertEquals(expected2, optimizer2.computeTxPowerMap());

		// Invalid txPower choices! The intersection is an empty set.
		DeviceDataManager deviceDataManager3 = new DeviceDataManager();
		deviceDataManager3.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);
		final DeviceConfig apCfgA3 = new DeviceConfig();
		final DeviceConfig apCfgB3 = new DeviceConfig();
		final DeviceConfig apCfgC3 = new DeviceConfig();
		apCfgA3.boundary = 100;
		apCfgA3.location = new ArrayList<>(Arrays.asList(10, 10));
		apCfgA3.allowedTxPowers = new HashMap<>();
		UCentralConstants.BANDS.stream()
			.forEach(
				band -> apCfgA3.allowedTxPowers.put(band, Arrays.asList(1, 2))
			);
		apCfgB3.boundary = 100;
		apCfgB3.location = new ArrayList<>(Arrays.asList(30, 10));
		apCfgB3.allowedTxPowers = new HashMap<>();
		UCentralConstants.BANDS.stream()
			.forEach(
				band -> apCfgB3.allowedTxPowers.put(band, Arrays.asList(3, 4))
			);
		apCfgC3.boundary = 100;
		apCfgC3.location = new ArrayList<>(Arrays.asList(50, 10));
		deviceDataManager3.setDeviceApConfig(deviceA, apCfgA3);
		deviceDataManager3.setDeviceApConfig(deviceB, apCfgB3);
		deviceDataManager3.setDeviceApConfig(deviceC, apCfgC3);

		DataModel dataModel3 = new DataModel();
		for (String device : Arrays.asList(deviceA, deviceB, deviceC)) {
			dataModel3.latestDeviceStatusRadios.put(
				device,
				TestUtils.createDeviceStatus(UCentralConstants.BANDS)
			);
			dataModel3.latestStates.put(
				device,
				Arrays.asList(
					TestUtils.createState(
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid,
						DEFAULT_CHANNEL_5G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid
					)
				)
			);
		}

		Map<String, Map<String, Integer>> expected3 = new HashMap<>();

		LocationBasedOptimalTPC optimizer3 = new LocationBasedOptimalTPC(
			dataModel3,
			TEST_ZONE,
			deviceDataManager3
		);

		assertEquals(expected3, optimizer3.computeTxPowerMap());

		// Invalid operation! Complexity issue!
		DeviceDataManager deviceDataManager4 = new DeviceDataManager();
		deviceDataManager4.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
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
		for (String device : Arrays.asList(deviceA, deviceB, deviceC)) {
			dataModel4.latestDeviceStatusRadios.put(
				device,
				TestUtils.createDeviceStatus(UCentralConstants.BANDS)
			);
			dataModel4.latestStates.put(
				device,
				Arrays.asList(
					TestUtils.createState(
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid,
						DEFAULT_CHANNEL_2G,
						DEFAULT_CHANNEL_WIDTH,
						DEFAULT_TX_POWER,
						dummyBssid
					)
				)
			);
		}

		Map<String, Map<String, Integer>> expected4 = new HashMap<>();

		LocationBasedOptimalTPC optimizer4 = new LocationBasedOptimalTPC(
			dataModel4,
			TEST_ZONE,
			deviceDataManager4
		);

		assertEquals(expected4, optimizer4.computeTxPowerMap());
	}
}
