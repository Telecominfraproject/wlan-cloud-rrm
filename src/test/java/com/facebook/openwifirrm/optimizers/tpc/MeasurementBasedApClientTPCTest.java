/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;

@TestMethodOrder(OrderAnnotation.class)
public class MeasurementBasedApClientTPCTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	@Test
	@Order(1)
	void testComputeCorrectTxPower() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";
		final String deviceE = "eeeeeeeeeeee";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE, deviceA, deviceB, deviceC, deviceD, deviceE
			)
		);

		DataModel dataModel = new DataModel();
		dataModel.latestState.put(
			deviceA,
			TestUtils.createState(36, 20, 20, null, new int[] {})
		);
		dataModel.latestState.put(
			deviceB,
			TestUtils.createState(36, 20, 20, "", new int[] {-65})
		);
		dataModel.latestState.put(
			deviceC,
			TestUtils.createState(36,40, 21, null, new int[] {-65, -73, -58})
		);
		dataModel.latestState.put(
			deviceD,
			TestUtils.createState(36, 20, 22, null, new int[] {-80})
		);
		dataModel.latestState.put(
			deviceE,
			TestUtils.createState(36, 20, 23, null, new int[] {-45})
		);

		TPC optimizer = new MeasurementBasedApClientTPC(dataModel, TEST_ZONE, deviceDataManager);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// Device A: no clients
		assertEquals(10, txPowerMap.get(deviceA).get(UCentralConstants.BAND_5G));

		// Device B: 1 client with RSSI -65
		assertEquals(14, txPowerMap.get(deviceB).get(UCentralConstants.BAND_5G));

		// Device C: 3 clients with min. RSSI -73
		assertEquals(26, txPowerMap.get(deviceC).get(UCentralConstants.BAND_5G));

		// Device D: 1 client with RSSI -80 => set to max txPower for MCS 7
		assertEquals(28, txPowerMap.get(deviceD).get(UCentralConstants.BAND_5G));

		// Device E: 1 client with RSSI -45 => set to min txPower
		assertEquals(TPC.MIN_TX_POWER, txPowerMap.get(deviceE).get(UCentralConstants.BAND_5G));
	}

	@Test
	@Order(2)
	void testVariousBands() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE, deviceA, deviceB, deviceC, deviceD
			)
		);

		DataModel dataModel = new DataModel();
		// 2G only
		dataModel.latestState.put(
			deviceA,
			TestUtils.createState(1, 20, 20, null, new int[] {})
		);
		// 5G only
		dataModel.latestState.put(
			deviceB,
			TestUtils.createState(36, 20, 20, null, new int[] {})
		);
		// 2G and 5G
		dataModel.latestState.put(
			deviceC,
			TestUtils.createState(1, 20, 20, null, new int[] {}, 36, 20, 20, null, new int[] {})
		);
		// No valid bands in 2G or 5G
		dataModel.latestState.put(
			deviceD,
			TestUtils.createState(25, 20, 20, null, new int[] {})
		);

		TPC optimizer = new MeasurementBasedApClientTPC(dataModel, TEST_ZONE, deviceDataManager);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		Set<String> expectedBands = new TreeSet<>();
		expectedBands.add(UCentralConstants.BAND_2G);
		assertEquals(expectedBands, txPowerMap.get(deviceA).keySet());
		expectedBands.add(UCentralConstants.BAND_5G);
		assertEquals(expectedBands, txPowerMap.get(deviceC).keySet());
		expectedBands.remove(UCentralConstants.BAND_2G);
		assertEquals(expectedBands, txPowerMap.get(deviceB).keySet());
		expectedBands.remove(UCentralConstants.BAND_5G);
		assertEquals(expectedBands, txPowerMap.get(deviceD).keySet());
	}
}
