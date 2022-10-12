/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.tpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
				TEST_ZONE,
				deviceA,
				deviceB,
				deviceC,
				deviceD,
				deviceE
			)
		);

		DataModel dataModel = new DataModel();
		dataModel.latestStates.put(
			deviceA,
			Arrays.asList(
				TestUtils.createState(36, 20, 20, null, new int[] {})
			)
		);
		dataModel.latestStates.put(
			deviceB,
			Arrays.asList(
				TestUtils.createState(36, 20, 20, "", new int[] { -65 })
			)
		);
		dataModel.latestStates.put(
			deviceC,
			Arrays.asList(
				TestUtils.createState(
					36,
					40,
					21,
					null,
					new int[] { -65, -73, -58 }
				)
			)
		);
		dataModel.latestStates.put(
			deviceD,
			Arrays.asList(
				TestUtils.createState(36, 20, 22, null, new int[] { -80 })
			)
		);
		dataModel.latestStates.put(
			deviceE,
			Arrays.asList(
				TestUtils.createState(36, 20, 23, null, new int[] { -45 })
			)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// Device A: no clients
		assertEquals(
			0,
			txPowerMap.get(deviceA).get(UCentralConstants.BAND_5G)
		);

		// Device B: 1 client with RSSI -65
		assertEquals(
			14,
			txPowerMap.get(deviceB).get(UCentralConstants.BAND_5G)
		);

		// Device C: 3 clients with min. RSSI -73
		assertEquals(
			26,
			txPowerMap.get(deviceC).get(UCentralConstants.BAND_5G)
		);

		// Device D: 1 client with RSSI -80 => set to max txPower for MCS 7
		assertEquals(
			28,
			txPowerMap.get(deviceD).get(UCentralConstants.BAND_5G)
		);

		// Device E: 1 client with RSSI -45 => set to min txPower
		assertEquals(
			TPC.MIN_TX_POWER,
			txPowerMap.get(deviceE).get(UCentralConstants.BAND_5G)
		);
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
				TEST_ZONE,
				deviceA,
				deviceB,
				deviceC,
				deviceD
			)
		);

		DataModel dataModel = new DataModel();
		// 2G only
		dataModel.latestStates.put(
			deviceA,
			Arrays.asList(
				TestUtils.createState(1, 20, 20, null, new int[] {})
			)
		);
		// 5G only
		dataModel.latestStates.put(
			deviceB,
			Arrays.asList(
				TestUtils.createState(36, 20, 20, null, new int[] {})
			)
		);
		// 2G and 5G
		dataModel.latestStates.put(
			deviceC,
			Arrays.asList(
				TestUtils.createState(
					1,
					20,
					20,
					null,
					36,
					20,
					20,
					null
				)
			)
		);
		// No valid bands in 2G or 5G
		dataModel.latestStates.put(
			deviceD,
			Arrays.asList(
				TestUtils.createState(25, 20, 20, null, new int[] {})
			)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager
		);
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

	@Test
	@Order(3)
	void testTxPowerChoices() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE,
				deviceA,
				deviceB,
				deviceC
			)
		);
		DeviceLayeredConfig deviceLayeredConfig = new DeviceLayeredConfig();
		DeviceConfig deviceConfigA = new DeviceConfig();
		deviceConfigA.allowedTxPowers = new TreeMap<>();
		deviceConfigA.allowedTxPowers
			.put(UCentralConstants.BAND_5G, Arrays.asList(20, 21, 22, 23, 24));
		deviceLayeredConfig.apConfig.put(deviceA, deviceConfigA);
		DeviceConfig deviceConfigB = new DeviceConfig();
		deviceConfigB.allowedTxPowers = new TreeMap<>();
		deviceConfigB.allowedTxPowers.put(
			UCentralConstants.BAND_5G,
			Arrays.asList(20, 21, 22, 23, 24, 25)
		);
		deviceConfigB.userTxPowers = new TreeMap<>();
		deviceConfigB.userTxPowers.put(UCentralConstants.BAND_5G, 7);
		deviceLayeredConfig.apConfig.put(deviceB, deviceConfigB);
		deviceDataManager.setDeviceLayeredConfig(deviceLayeredConfig);

		DataModel dataModel = new DataModel();
		dataModel.latestStates.put(
			deviceA,
			Arrays.asList(
				TestUtils.createState(36, 20, 20, null, new int[] {})
			)
		);
		dataModel.latestStates.put(
			deviceB,
			Arrays.asList(
				TestUtils.createState(36, 20, 20, "", new int[] { -65 })
			)
		);
		dataModel.latestStates.put(
			deviceC,
			Arrays.asList(
				TestUtils.createState(
					36,
					40,
					21,
					null,
					new int[] { -65, -73, -58 }
				)
			)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// Device A: no clients, so the min possible tx power is used
		assertEquals(
			20,
			txPowerMap.get(deviceA).get(UCentralConstants.BAND_5G)
		);

		// Device B: User tx power is 7
		assertEquals(
			7,
			txPowerMap.get(deviceB).get(UCentralConstants.BAND_5G)
		);

		// Device C: 3 clients with min. RSSI -73
		assertEquals(
			26,
			txPowerMap.get(deviceC).get(UCentralConstants.BAND_5G)
		);
	}
}
