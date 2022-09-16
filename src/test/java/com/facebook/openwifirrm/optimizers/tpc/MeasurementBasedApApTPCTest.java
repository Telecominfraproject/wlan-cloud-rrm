/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonArray;

@TestMethodOrder(OrderAnnotation.class)
public class MeasurementBasedApApTPCTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";
	private static final int MAX_TX_POWER = TPC.MAX_TX_POWER;
	/** Default channel width (MHz). */
	private static final int DEFAULT_CHANNEL_WIDTH = 20;

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
			TestUtils.createTopology(TEST_ZONE, DEVICE_A, DEVICE_B, DEVICE_C)
		);
		final DeviceConfig apCfgA = new DeviceConfig();
		final DeviceConfig apCfgB = new DeviceConfig();
		final DeviceConfig apCfgC = new DeviceConfig();
		apCfgA.boundary = 500;
		apCfgA.location = List.of(408, 317);
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
	 * Creates a data model with 3 devices. All are at max_tx_power, which
	 * represents the first step in greedy TPC.
	 *
	 * @return a data model
	 */
	private static DataModel createModel() {
		DataModel model = new DataModel();

		State stateA = TestUtils.createState(
			1,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_A,
			36,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_A
		);
		State stateB = TestUtils.createState(
			1,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_B,
			36,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_B
		);
		State stateC = TestUtils.createState(
			1,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_C,
			36,
			DEFAULT_CHANNEL_WIDTH,
			MAX_TX_POWER,
			BSSID_C
		);

		model.latestState.put(DEVICE_A, stateA);
		model.latestState.put(DEVICE_B, stateB);
		model.latestState.put(DEVICE_C, stateC);

		model.latestDeviceStatusRadios.put(
			DEVICE_A,
			TestUtils
				.createDeviceStatusDualBand(1, MAX_TX_POWER, 36, MAX_TX_POWER)
		);
		model.latestDeviceStatusRadios.put(
			DEVICE_B,
			TestUtils
				.createDeviceStatusDualBand(1, MAX_TX_POWER, 36, MAX_TX_POWER)
		);
		model.latestDeviceStatusRadios.put(
			DEVICE_C,
			TestUtils
				.createDeviceStatusDualBand(1, MAX_TX_POWER, 36, MAX_TX_POWER)
		);

		return model;
	}

	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansA(
		int channel
	) {
		Map<String, Integer> rssiFromA = Map.ofEntries(
			Map.entry(BSSID_Z, -91),
			Map.entry(BSSID_B, -81),
			Map.entry(BSSID_C, -61)
		);
		List<WifiScanEntry> wifiScanA =
			TestUtils.createWifiScanListWithBssid(rssiFromA, channel);

		Map<String, Integer> rssiFromB = Map.ofEntries(
			Map.entry(BSSID_Z, -92),
			Map.entry(BSSID_A, -72),
			Map.entry(BSSID_C, -62)
		);
		List<WifiScanEntry> wifiScanB =
			TestUtils.createWifiScanListWithBssid(rssiFromB, channel);

		Map<String, Integer> rssiFromC = Map.ofEntries(
			Map.entry(BSSID_Z, -93),
			Map.entry(BSSID_A, -73),
			Map.entry(BSSID_B, -83)
		);
		List<WifiScanEntry> wifiScanC =
			TestUtils.createWifiScanListWithBssid(rssiFromC, channel);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		latestWifiScans.put(DEVICE_C, List.of(wifiScanC));

		return latestWifiScans;
	}

	/** Sets up the tx powers as in the example in Po-Han's design doc */
	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansB(
		int channel
	) {
		Map<String, Integer> rssiFromA = Map.ofEntries(
			Map.entry(BSSID_B, -65),
			Map.entry(BSSID_C, -23)
		);
		List<WifiScanEntry> wifiScanA =
			TestUtils.createWifiScanListWithBssid(rssiFromA, channel);

		Map<String, Integer> rssiFromB = Map.ofEntries(
			Map.entry(BSSID_A, -52),
			Map.entry(BSSID_C, -60)
		);
		List<WifiScanEntry> wifiScanB =
			TestUtils.createWifiScanListWithBssid(rssiFromB, channel);

		Map<String, Integer> rssiFromC = Map.ofEntries(
			Map.entry(BSSID_A, -20),
			Map.entry(BSSID_B, -63)
		);
		List<WifiScanEntry> wifiScanC =
			TestUtils.createWifiScanListWithBssid(rssiFromC, channel);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		latestWifiScans.put(DEVICE_C, List.of(wifiScanC));

		return latestWifiScans;
	}

	/**
	 * Sets up the tx powers to test non-zero values of nthSmallestRssi.
	 * Similar to {@link #createLatestWifiScansB(int)} except the higher RSSIs
	 * are now lower, so that what used to be the 0th smallest RSSI is now the
	 * 1st smallest RSSI (so if nthSmallestRssi = 1 is used with this setup,
	 * the end result should be the same as if nthSmallestRssi = 0 were used
	 * with {@link #createLatestWifiScansB(int)}.
	 *
	 * @param channel channel number
	 * @return latest wifiscan map
	 */
	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansC(
		int channel
	) {
		Map<String, Integer> rssiFromA = Map.ofEntries(
			Map.entry(BSSID_B, -65),
			Map.entry(BSSID_C, -61)
		);
		List<WifiScanEntry> wifiScanA =
			TestUtils.createWifiScanListWithBssid(rssiFromA, channel);

		Map<String, Integer> rssiFromB = Map.ofEntries(
			Map.entry(BSSID_A, -52),
			Map.entry(BSSID_C, -60)
		);
		List<WifiScanEntry> wifiScanB =
			TestUtils.createWifiScanListWithBssid(rssiFromB, channel);

		Map<String, Integer> rssiFromC = Map.ofEntries(
			Map.entry(BSSID_A, -53),
			Map.entry(BSSID_B, -66)
		);
		List<WifiScanEntry> wifiScanC =
			TestUtils.createWifiScanListWithBssid(rssiFromC, channel);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		latestWifiScans.put(DEVICE_C, List.of(wifiScanC));

		return latestWifiScans;
	}

	/**
	 * Returns latest wifiscan map for a {@code DataModel} without wifiscan
	 * entries from AP C to the other APs.
	 *
	 * @param channel channel number
	 * @return latest wifiscan map for a {@code DataModel}
	 */
	private static Map<String, List<List<WifiScanEntry>>> createLatestWifiScansWithMissingEntries(
		int channel
	) {
		Map<String, Integer> rssiFromA = Map.ofEntries(Map.entry(BSSID_B, -38));
		List<WifiScanEntry> wifiScanA = TestUtils.createWifiScanListWithBssid(
			rssiFromA,
			channel
		);

		Map<String, Integer> rssiFromB = Map.ofEntries(Map.entry(BSSID_A, -39));
		List<WifiScanEntry> wifiScanB = TestUtils.createWifiScanListWithBssid(
			rssiFromB,
			channel
		);

		Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			new HashMap<>();
		latestWifiScans.put(DEVICE_A, List.of(wifiScanA));
		latestWifiScans.put(DEVICE_B, List.of(wifiScanB));
		return latestWifiScans;
	}

	@Test
	@Order(1)
	void testGetManagedBSSIDs() throws Exception {
		DataModel dataModel = createModel();
		Set<String> managedBSSIDs =
			MeasurementBasedApApTPC.getManagedBSSIDs(dataModel);
		assertEquals(3, managedBSSIDs.size());
		assertTrue(managedBSSIDs.contains(BSSID_A));
		assertTrue(managedBSSIDs.contains(BSSID_B));
		assertTrue(managedBSSIDs.contains(BSSID_C));
	}

	@Test
	@Order(2)
	void testGetCurrentTxPower() throws Exception {
		final int expectedTxPower = 29;

		DataModel model = new DataModel();
		model.latestDeviceStatusRadios.put(
			DEVICE_A,
			TestUtils.createDeviceStatusDualBand(1, 5, 36, expectedTxPower)
		);

		JsonArray radioStatuses =
			model.latestDeviceStatusRadios.get(DEVICE_A).getAsJsonArray();
		int txPower = MeasurementBasedApApTPC
			.getCurrentTxPower(radioStatuses, UCentralConstants.BAND_5G)
			.get();
		assertEquals(expectedTxPower, txPower);
	}

	@Test
	@Order(3)
	void testBuildRssiMap() throws Exception {
		// This example includes three APs, and one AP that is unmanaged
		Set<String> bssidSet = Set.of(BSSID_A, BSSID_B, BSSID_C);
		Map<String, List<List<WifiScanEntry>>> latestWifiScans =
			createLatestWifiScansA(36);

		Map<String, List<Integer>> rssiMap = MeasurementBasedApApTPC
			.buildRssiMap(bssidSet, latestWifiScans, UCentralConstants.BAND_5G);

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
	void testComputeTxPower() throws Exception {
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
			nthSmallestRssi,
			TPC.DEFAULT_TX_POWER_CHOICES
		);
		assertEquals(16, newTxPower);

		rssiValues = List.of(-62);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			TPC.DEFAULT_TX_POWER_CHOICES
		);
		assertEquals(12, newTxPower);

		rssiValues = List.of(-52, -20);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			TPC.DEFAULT_TX_POWER_CHOICES
		);
		assertEquals(2, newTxPower);

		// Check edge cases
		rssiValues = List.of(-30);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			TPC.DEFAULT_TX_POWER_CHOICES
		);
		assertEquals(0, newTxPower);

		rssiValues = List.of();
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			0,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			TPC.DEFAULT_TX_POWER_CHOICES
		);
		assertEquals(30, newTxPower);
	}

	/**
	 * Tests the tx power map calculations in the given band. Taken from
	 * algorithm design doc from @pohanhf
	 *
	 * @param band band (e.g., "2G")
	 */
	private static void testComputeTxPowerMapSimpleInOneBand(String band) {
		int channel = UCentralUtils.LOWER_CHANNEL_LIMIT.get(band);
		DataModel dataModel = createModel();
		dataModel.latestWifiScans = createLatestWifiScansB(channel);
		DeviceDataManager deviceDataManager = createDeviceDataManager();

		MeasurementBasedApApTPC optimizer = new MeasurementBasedApApTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			-80,
			0
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(3, txPowerMap.size());
		assertEquals(2, txPowerMap.get(DEVICE_A).get(band));
		assertEquals(15, txPowerMap.get(DEVICE_B).get(band));
		assertEquals(10, txPowerMap.get(DEVICE_C).get(band));
	}

	/** Tests non-zero nthSmallestRssi (specifically, nthSmallestRssi = 1) */
	private static void testComputeTxPowerMapNonzeroNthSmallestRssi(
		String band
	) {
		int channel = UCentralUtils.LOWER_CHANNEL_LIMIT.get(band);
		DataModel dataModel = createModel();
		dataModel.latestWifiScans = createLatestWifiScansC(channel);
		DeviceDataManager deviceDataManager = createDeviceDataManager();

		MeasurementBasedApApTPC optimizer = new MeasurementBasedApApTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			-80,
			1
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(3, txPowerMap.size());
		assertEquals(2, txPowerMap.get(DEVICE_A).get(band));
		assertEquals(15, txPowerMap.get(DEVICE_B).get(band));
		assertEquals(10, txPowerMap.get(DEVICE_C).get(band));
	}

	/**
	 * Tests the tx power map calculations without a wifiscan entry from one AP.
	 *
	 * @param band band (e.g., "2G")
	 */
	private static void testComputeTxPowerMapMissingDataInOneBand(String band) {
		int channel = UCentralUtils.LOWER_CHANNEL_LIMIT.get(band);
		DataModel dataModel = createModel();
		dataModel.latestWifiScans =
			createLatestWifiScansWithMissingEntries(channel);
		DeviceDataManager deviceDataManager = createDeviceDataManager();

		MeasurementBasedApApTPC optimizer = new MeasurementBasedApApTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			-80,
			0
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(3, txPowerMap.size());
		assertEquals(0, txPowerMap.get(DEVICE_A).get(band));
		assertEquals(0, txPowerMap.get(DEVICE_B).get(band));
		// Since no other APs see a signal from DEVICE_C, we set the tx_power to max
		assertEquals(30, txPowerMap.get(DEVICE_C).get(band));
	}

	@Test
	@Order(5)
	void testComputeTxPowerMapOnOneBand() throws Exception {
		for (String band : UCentralConstants.BANDS) {
			testComputeTxPowerMapSimpleInOneBand(band);
			testComputeTxPowerMapNonzeroNthSmallestRssi(band);
			testComputeTxPowerMapMissingDataInOneBand(band);
		}
	}

	@Test
	@Order(6)
	void testComputeTxPowerMapMultiBand() {
		// test both bands simultaneously with different setups on each band
		DataModel dataModel = createModel();
		dataModel.latestState.remove(DEVICE_B);
		dataModel.latestState.put(
			DEVICE_B,
			TestUtils.createState(
				1,
				DEFAULT_CHANNEL_WIDTH,
				MAX_TX_POWER,
				BSSID_B
			)
		);
		// make device C not operate in the 5G band instead of dual band
		dataModel.latestDeviceStatusRadios.put(
			DEVICE_C,
			TestUtils.createDeviceStatus(
				UCentralConstants.BAND_2G,
				1,
				MAX_TX_POWER
			)
		);
		DeviceDataManager deviceDataManager = createDeviceDataManager();
		// 2G setup
		final int channel2G =
			UCentralUtils.LOWER_CHANNEL_LIMIT.get(UCentralConstants.BAND_2G);
		dataModel.latestWifiScans = createLatestWifiScansB(channel2G);
		// 5G setup
		final int channel5G =
			UCentralUtils.LOWER_CHANNEL_LIMIT.get(UCentralConstants.BAND_5G);
		Map<String, List<List<WifiScanEntry>>> toMerge =
			createLatestWifiScansWithMissingEntries(channel5G);
		for (
			Map.Entry<String, List<List<WifiScanEntry>>> mapEntry : toMerge
				.entrySet()
		) {
			String serialNumber = mapEntry.getKey();
			List<WifiScanEntry> entriesToMerge = mapEntry.getValue().get(0);
			dataModel.latestWifiScans
				.computeIfAbsent(
					serialNumber,
					k -> new ArrayList<List<WifiScanEntry>>()
				)
				.get(0)
				.addAll(entriesToMerge);
		}

		MeasurementBasedApApTPC optimizer = new MeasurementBasedApApTPC(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			-80,
			0
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// test 2G band
		assertEquals(3, txPowerMap.size());
		assertEquals(
			2,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			15,
			txPowerMap.get(DEVICE_B).get(UCentralConstants.BAND_2G)
		);
		assertEquals(
			10,
			txPowerMap.get(DEVICE_C).get(UCentralConstants.BAND_2G)
		);

		// test 5G band
		assertEquals(
			0,
			txPowerMap.get(DEVICE_A).get(UCentralConstants.BAND_5G)
		);
		// deivce B does not have 5G radio
		assertFalse(
			txPowerMap.get(DEVICE_B).containsKey(UCentralConstants.BAND_5G)
		);
		// device C is not in the 5G band
		assertFalse(
			txPowerMap.get(DEVICE_C).containsKey(UCentralConstants.BAND_5G)
		);
	}

	@Test
	@Order(7)
	void testComputeWithTxPowerChoices() {
		// Test examples here are similar with testComputeTxPower, but the tx
		// power choices are changed
		final String serialNumber = "testSerial";
		final int currentTxPower = 30;
		final int coverageThreshold = -80;
		final int nthSmallestRssi = 0;
		List<Integer> rssiValues = List.of(-66);

		int newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			IntStream
				.rangeClosed(20, 30)
				.boxed()
				.collect(Collectors.toList())
		);
		assertEquals(20, newTxPower);

		rssiValues = List.of(-62);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			Arrays.asList(8, 9, 10, 11)
		);
		assertEquals(11, newTxPower);

		rssiValues = List.of(-52, -20);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			IntStream
				.rangeClosed(2, 20)
				.boxed()
				.collect(Collectors.toList())
		);
		assertEquals(2, newTxPower);

		// Check edge cases
		rssiValues = List.of(-30);
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			currentTxPower,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			IntStream
				.rangeClosed(5, 20)
				.boxed()
				.collect(Collectors.toList())
		);
		assertEquals(5, newTxPower);

		rssiValues = List.of();
		newTxPower = MeasurementBasedApApTPC.computeTxPower(
			serialNumber,
			0,
			rssiValues,
			coverageThreshold,
			nthSmallestRssi,
			IntStream
				.rangeClosed(2, 30)
				.boxed()
				.collect(Collectors.toList())
		);
		assertEquals(30, newTxPower);
	}

}
