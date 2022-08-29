/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.models.State;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(OrderAnnotation.class)
public class RandomTxPowerInitializerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	// Serial numbers
	private static final String DEVICE_A = "aaaaaaaaaaaa";
	private static final String DEVICE_B = "bbbbbbbbbbbb";

	/** Create an empty device state object. */
	private static State createState() {
		return new State();
	}

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
		dataModel.latestState.put(DEVICE_A, createState());
		dataModel.latestState.put(DEVICE_B, createState());
		return dataModel;
	}

	@Test
	@Order(1)
	void testSeededTxPower() throws Exception {
		TPC optimizer = new RandomTxPowerInitializer(
			createModel(),
			TEST_ZONE,
			createDeviceDataManager(),
			new Random(0)
		);

		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(
			20,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			11,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_5G)
		);
		assertEquals(
			2,
			txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			2,
			txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_5G)
		);
	}

	@Test
	@Order(2)
	void testRandomTxPowerInAvailableList() throws Exception {
		TPC optimizer = new RandomTxPowerInitializer(
			createModel(),
			TEST_ZONE,
			createDeviceDataManager(),
			new Random(0)
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();
		Set<Integer> txPowerSet = new TreeSet<>();
		for (String serialNumber : txPowerMap.keySet()) {
			txPowerSet.addAll(txPowerMap.get(serialNumber).values());
		}

		// different values per AP
		assertTrue(txPowerSet.size() > 1);
		for (int txPower : txPowerSet) {
			assertTrue(
				txPower >= TPC.MIN_TX_POWER && txPower <= TPC.MAX_TX_POWER
			);
		}
	}
}
