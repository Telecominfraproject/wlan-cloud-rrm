/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonArray;

@TestMethodOrder(OrderAnnotation.class)
public class MeasurementBasedApApTPCTest {
	private static final int MAX_TX_POWER = 30;
	private static final String BAND = "5G";

	// Serial numbers
	private static final String DEVICE_A = "aaaaaaaaaaaa";
	private static final String DEVICE_B = "bbbbbbbbbbbb";
	private static final String DEVICE_C = "cccccccccccc";

	private static final String BSSID_A = "aa:aa:aa:aa:aa:aa";
	private static final String BSSID_B = "bb:bb:bb:bb:bb:bb";
	private static final String BSSID_C = "cc:cc:cc:cc:cc:cc";
	private static final String BSSID_Z = "zz:zz:zz:zz:zz:zz";

	/**
	 * Creates a manager with 3 devices.
	 */
	private static DeviceDataManager createDeviceDataManager() {
		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
				TestUtils.createTopology(TestUtils.TEST_ZONE, DEVICE_A, DEVICE_B, DEVICE_C)
		);
		final DeviceConfig apCfgA = new DeviceConfig();
		final DeviceConfig apCfgB = new DeviceConfig();
		final DeviceConfig apCfgC = new DeviceConfig();
		apCfgA.boundary = 500;
		apCfgA.location = List.of(408, 317);
		apCfgA.allowedTxPowers = new HashMap<>();
		apCfgA.allowedTxPowers.put(BAND, List.of(29, 30));
		apCfgB.boundary = 500;
		apCfgB.location = List.of(453, 49);
		apCfgC.boundary = 500;
		apCfgC.location = List.of(64, 140);
		deviceDataManager.setDeviceApConfig(DEVICE_A, apCfgA);
		deviceDataManager.setDeviceApConfig(DEVICE_B, apCfgB);
		deviceDataManager.setDeviceApConfig(DEVICE_C, apCfgC);

		return deviceDataManager;
	}

	/**
	 * Creates a data model with 3 devices.
	 * All are at max_tx_power, which represents the first step in greedy TPC.
	 */
	private static DataModel createModel() {
		DataModel model = new DataModel();

		State stateA = TestUtils.createState(1, 20, 5, 36, 20, MAX_TX_POWER, BSSID_A);
		State stateB = TestUtils.createState(1, 20, 5, 36, 20, MAX_TX_POWER, BSSID_B);
		State stateC = TestUtils.createState(1, 20, 5, 36, 20, MAX_TX_POWER, BSSID_C);

		model.latestState.put(DEVICE_A, stateA);
		model.latestState.put(DEVICE_B, stateB);
		model.latestState.put(DEVICE_C, stateC);

		model.latestDeviceStatus.put(DEVICE_A, TestUtils.createDeviceStatusDualBand(1, 5, 36, MAX_TX_POWER));
		model.latestDeviceStatus.put(DEVICE_B, TestUtils.createDeviceStatusDualBand(1, 5, 36, MAX_TX_POWER));
		model.latestDeviceStatus.put(DEVICE_C, TestUtils.createDeviceStatusDualBand(1, 5, 36, MAX_TX_POWER));

		return model;
	}

	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansA() {
		Map<String, Integer> rssiFromA = Map.ofEntries(
			Map.entry(BSSID_Z, -91),
			Map.entry(BSSID_B, -81),
			Map.entry(BSSID_C, -61)
		);
		List<WifiScanEntry> wifiScanA = TestUtils.createWifiScanListWithBssid(rssiFromA);

		Map<String, Integer> rssiFromB = Map.ofEntries(
			Map.entry(BSSID_Z, -92),
			Map.entry(BSSID_A, -72),
			Map.entry(BSSID_C, -62)
		);
		List<WifiScanEntry> wifiScanB = TestUtils.createWifiScanListWithBssid(rssiFromB);

		Map<String, Integer> rssiFromC = Map.ofEntries(
			Map.entry(BSSID_Z, -93),
			Map.entry(BSSID_A, -73),
			Map.entry(BSSID_B, -83)
		);
		List<WifiScanEntry> wifiScanC = TestUtils.createWifiScanListWithBssid(rssiFromC);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans = new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		latestWifiScans.put(DEVICE_C, List.of(wifiScanC));

		return latestWifiScans;
	}

	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansB() {
		Map<String, Integer> rssiFromA = Map.ofEntries(
			Map.entry(BSSID_B, -65),
			Map.entry(BSSID_C, -23)
		);
		List<WifiScanEntry> wifiScanA = TestUtils.createWifiScanListWithBssid(rssiFromA);

		Map<String, Integer> rssiFromB = Map.ofEntries(
			Map.entry(BSSID_A, -52),
			Map.entry(BSSID_C, -60)
		);
		List<WifiScanEntry> wifiScanB = TestUtils.createWifiScanListWithBssid(rssiFromB);

		Map<String, Integer> rssiFromC = Map.ofEntries(
			Map.entry(BSSID_A, -20),
			Map.entry(BSSID_B, -63)
		);
		List<WifiScanEntry> wifiScanC = TestUtils.createWifiScanListWithBssid(rssiFromC);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans = new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		latestWifiScans.put(DEVICE_C, List.of(wifiScanC));

		return latestWifiScans;
	}

	@Test
	@Order(1)
	void test_getManagedBSSIDs() throws Exception {
		DataModel dataModel = createModel();
		Set<String> managedBSSIDs = MeasurementBasedApApTPC.getManagedBSSIDs(dataModel);
		assertEquals(3, managedBSSIDs.size());
		assertTrue(managedBSSIDs.contains(BSSID_A));
		assertTrue(managedBSSIDs.contains(BSSID_B));
		assertTrue(managedBSSIDs.contains(BSSID_C));
	}

	@Test
	@Order(2)
	void test_getCurrentTxPower() throws Exception {
		final int expectedTxPower = 29;

		DataModel model = new DataModel();
		model.latestDeviceStatus.put(DEVICE_A, TestUtils.createDeviceStatusDualBand(1, 5, 36, expectedTxPower));

		JsonArray radioStatuses = model.latestDeviceStatus.get(DEVICE_A).getAsJsonArray();
		int txPower = MeasurementBasedApApTPC.getCurrentTxPower(radioStatuses);
		assertEquals(expectedTxPower, txPower);
	}

	@Test
	@Order(3)
	void test_buildRssiMap() throws Exception {
		// This example includes three APs, and one AP that is unmanaged
		Set<String> bssidSet = Set.of(BSSID_A, BSSID_B, BSSID_C);
		Map<String, List<List<WifiScanEntry>>> latestWifiScans = createLatestWifiScansA();

		Map<String, List<Integer>> rssiMap = MeasurementBasedApApTPC.buildRssiMap(bssidSet, latestWifiScans);

		assertEquals(3, rssiMap.size());
		assertEquals(2, rssiMap.get(BSSID_A).size());
		assertEquals(2, rssiMap.get(BSSID_B).size());
		assertEquals(2, rssiMap.get(BSSID_C).size());

		// Sanity check that values are sorted in ascending order
		assertEquals(-73, rssiMap.get(BSSID_A).get(0));
		assertEquals(-83, rssiMap.get(BSSID_B).get(0));
		assertEquals(-62, rssiMap.get(BSSID_C).get(0));
	}

	@Test
	@Order(4)
	void test_computeTxPower() throws Exception {
		// Test examples here taken from algorithm design doc from @pohanhf
		final String serialNumber = "testSerial";
		final int currentTxPower = 30;
		final int coverageThreshold = -80;
		final int nthSmallestRssi = 0;
		List<Integer> rssiValues = List.of(-66);

		// Test examples here taken from algorithm design doc from @pohanhf
		// These are common happy path examples
		int newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi
		);
		assertEquals(16, newTxPower);

		rssiValues = List.of(-62);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi
		);
		assertEquals(12, newTxPower);

		rssiValues = List.of(-52, -20);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi
		);
		assertEquals(2, newTxPower);

		// Check edge cases
		rssiValues = List.of(-30);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi
		);
		assertEquals(0, newTxPower);

		rssiValues = List.of();
		newTxPower = MeasurementBasedApApTPC.computeTxPower(serialNumber, 0, rssiValues, coverageThreshold, nthSmallestRssi);
		assertEquals(30, newTxPower);
	}

	@Test
	@Order(5)
	void test_computeTxPowerMap() throws Exception {
		// First example here taken from algorithm design doc from @pohanhf
		DataModel dataModel = createModel();
		dataModel.latestWifiScans = createLatestWifiScansB();
		DeviceDataManager deviceDataManager = createDeviceDataManager();
		MeasurementBasedApApTPC optimizer = new MeasurementBasedApApTPC(dataModel, TestUtils.TEST_ZONE,
				deviceDataManager, -80, 0);

		Map<String, Map<String, Integer>> txPowerMap = optimizer.computeTxPowerMap();

		assertEquals(3, txPowerMap.size());
		assertEquals(2, txPowerMap.get(DEVICE_A).get(BAND));
		assertEquals(15, txPowerMap.get(DEVICE_B).get(BAND));
		assertEquals(10, txPowerMap.get(DEVICE_C).get(BAND));

		// Tests an example where wifiscans are missing some data
		Map<String, Integer> rssiFromA = Map.ofEntries(Map.entry(BSSID_B, -38));
		List<WifiScanEntry> wifiScanA = TestUtils.createWifiScanListWithBssid(rssiFromA);

		Map<String, Integer> rssiFromB = Map.ofEntries(Map.entry(BSSID_A, -39));
		List<WifiScanEntry> wifiScanB = TestUtils.createWifiScanListWithBssid(rssiFromB);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans = new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));

		dataModel = createModel();
		dataModel.latestWifiScans = latestWifiScans;

		deviceDataManager = createDeviceDataManager();
		optimizer = new MeasurementBasedApApTPC(dataModel, TestUtils.TEST_ZONE, deviceDataManager);

		txPowerMap = optimizer.computeTxPowerMap();

		assertEquals(3, txPowerMap.size());
		assertEquals(0, txPowerMap.get(DEVICE_A).get(BAND));
		assertEquals(0, txPowerMap.get(DEVICE_B).get(BAND));
		// Since no other APs see a signal from DEVICE_C, we set the tx_power to max
		assertEquals(30, txPowerMap.get(DEVICE_C).get(BAND));
	}
}
