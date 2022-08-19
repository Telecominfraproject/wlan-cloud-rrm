/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.*;

import com.facebook.openwifirrm.DeviceConfig;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.models.State;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class RandomTxPowerInitializerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	/** Serial numbers */
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
	void testProvidedTxPower() throws Exception {
		for (int txPower : Arrays.asList(-1, 16, 27)) {
			TPC optimizer = new RandomTxPowerInitializer(
				createModel(), TEST_ZONE, createDeviceDataManager(), txPower
			);
			Map<String, Map<String, Integer>> txPowerMap =
				optimizer.computeTxPowerMap();

			for (String band : UCentralConstants.BANDS) {
				assertEquals(txPower, txPowerMap.get(DEVICE_A).get(band));
				assertEquals(txPower, txPowerMap.get(DEVICE_B).get(band));
			}
		}
	}

	@Test
	@Order(2)
	void testRandomTxPower() throws Exception {
		TPC optimizer = new RandomTxPowerInitializer(
			createModel(), TEST_ZONE, createDeviceDataManager()
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();
		Set<Integer> txPowerSet = new TreeSet<>();
		for (String serialNumber : txPowerMap.keySet()) {
			txPowerSet.addAll(txPowerMap.get(serialNumber).values());
		}
		// One unique tx power for all devices and bands
		assertEquals(1, txPowerSet.size());
		for (int txPower : txPowerSet) {
			assertTrue(txPower >= TPC.MIN_TX_POWER && txPower <= TPC.MAX_TX_POWER);
		}
	}
}
