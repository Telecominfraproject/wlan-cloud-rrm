/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.tpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifi.cloudsdk.UCentralConstants;
import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.DeviceLayeredConfig;
import com.facebook.openwifi.rrm.modules.Modeler.DataModel;
import com.facebook.openwifi.rrm.optimizers.TestUtils;

@TestMethodOrder(OrderAnnotation.class)
public class RandomTxPowerInitializerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	// Serial numbers
	private static final String DEVICE_A = "aaaaaaaaaaaa";
	private static final String DEVICE_B = "bbbbbbbbbbbb";

	// Default channel width
	private static final int DEFAULT_CHANNEL_WIDTH = 20;

	// Default tx power
	private static final int DEFAULT_TX_POWER = 20;

	// bssids
	private static final String BSSID_A = "aa:aa:aa:aa:aa:aa";
	private static final String BSSID_B = "bb:bb:bb:bb:bb:bb";

	/**
	 * Creates a manager with 2 devices.
	 */
	private static DeviceDataManager createDeviceDataManager() {
		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, DEVICE_A, DEVICE_B)
		);
		return deviceDataManager;
	}

	/**
	 * Creates a data model with 2 devices.
	 */
	private static DataModel createModel() {
		DataModel dataModel = new DataModel();
		dataModel.latestStates.put(
			DEVICE_A,
			Arrays.asList(
				TestUtils.createState(
					36,
					DEFAULT_CHANNEL_WIDTH,
					DEFAULT_TX_POWER,
					BSSID_A,
					2,
					DEFAULT_CHANNEL_WIDTH,
					DEFAULT_TX_POWER,
					BSSID_A
				)
			)
		);
		dataModel.latestDeviceCapabilitiesPhy.put(
			DEVICE_A,
			TestUtils.createDeviceCapabilityPhy(
				new String[] {
					UCentralConstants.BAND_5G,
					UCentralConstants.BAND_2G }
			)
		);
		dataModel.latestStates.put(
			DEVICE_B,
			Arrays.asList(
				TestUtils.createState(
					2,
					DEFAULT_CHANNEL_WIDTH,
					DEFAULT_TX_POWER,
					BSSID_B
				)
			)
		);
		dataModel.latestDeviceCapabilitiesPhy.put(
			DEVICE_B,
			TestUtils.createDeviceCapabilityPhy(UCentralConstants.BAND_2G)
		);
		return dataModel;
	}

	@Test
	@Order(1)
	void testSeededSameTxPower() throws Exception {
		TPC optimizer = new RandomTxPowerInitializer(
			createModel(),
			TEST_ZONE,
			createDeviceDataManager(),
			false,
			new Random(0)
		);

		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		int expectedTxPower = 2;
		assertEquals(
			expectedTxPower,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			expectedTxPower,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_5G)
		);
		assertEquals(
			expectedTxPower,
			txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_2G)
		);
		assertNull(txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_5G));
	}

	@Test
	@Order(2)
	void testSeededDifferentTxPowers() throws Exception {
		DeviceDataManager deviceDataManager = createDeviceDataManager();
		DeviceLayeredConfig deviceLayeredConfig = new DeviceLayeredConfig();
		DeviceConfig deviceConfigA = new DeviceConfig();
		deviceConfigA.allowedTxPowers = new TreeMap<>();
		deviceConfigA.allowedTxPowers
			.put(UCentralConstants.BAND_2G, Arrays.asList(11, 12, 13, 14, 15));
		deviceConfigA.userTxPowers = new TreeMap<>();
		deviceConfigA.userTxPowers.put(UCentralConstants.BAND_2G, 6);
		deviceConfigA.allowedTxPowers
			.put(UCentralConstants.BAND_5G, Arrays.asList(11, 12, 13, 14, 15));
		deviceLayeredConfig.apConfig.put(DEVICE_A, deviceConfigA);
		DeviceConfig deviceConfigB = new DeviceConfig();
		deviceConfigB.userTxPowers = new TreeMap<>();
		deviceConfigB.userTxPowers.put(UCentralConstants.BAND_2G, 7);
		deviceLayeredConfig.apConfig.put(DEVICE_B, deviceConfigB);
		deviceDataManager.setDeviceLayeredConfig(deviceLayeredConfig);
		TPC optimizer = new RandomTxPowerInitializer(
			createModel(),
			TEST_ZONE,
			deviceDataManager,
			true,
			new Random(0)
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(
			6,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			15,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_5G)
		);
		assertEquals(
			7,
			txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_2G)
		);
		assertNull(txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_5G));
	}
}
