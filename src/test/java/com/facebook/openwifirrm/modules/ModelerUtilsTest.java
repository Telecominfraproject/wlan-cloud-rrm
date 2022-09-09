/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facebook.openwifirrm.aggregators.MeanAggregator;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.modules.ModelerUtils.AggregationType;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;

public class ModelerUtilsTest {
	@Test
	void testErrorCase() throws Exception {
		// Out of range
		double[][][] rxPower = ModelerUtils.generateRxPower(
			500,
			4,
			new ArrayList<>(Arrays.asList(408.0, 507.0, 64.0, 457.0)),
			new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
			new ArrayList<>(Arrays.asList(20.0, 20.0, 20.0, 20.0))
		);
		assertNull(rxPower);
	}

	@Test
	void testInvalidMetric() throws Exception {
		double[][][] rxPower = ModelerUtils.generateRxPower(
			500,
			4,
			new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
			new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
			new ArrayList<>(Arrays.asList(20.0, 20.0, 20.0, 20.0))
		);
		assertEquals(-108.529, rxPower[0][0][0], 0.001);
		double[][] heatMap = ModelerUtils.generateHeatMap(
			500,
			4,
			rxPower
		);
		assertEquals(-87.494, heatMap[0][0], 0.001);
		double[][] sinr = ModelerUtils.generateSinr(
			500,
			4,
			rxPower
		);
		assertEquals(5.995, sinr[0][0], 0.001);
		double metric = ModelerUtils.calculateTPCMetrics(
			500,
			heatMap,
			sinr
		);
		assertEquals(Double.POSITIVE_INFINITY, metric, 0.001);
	}

	@Test
	void testValidMetric() throws Exception {
		double[][][] rxPower = ModelerUtils.generateRxPower(
			500,
			4,
			new ArrayList<>(Arrays.asList(408.0, 453.0, 64.0, 457.0)),
			new ArrayList<>(Arrays.asList(317.0, 49.0, 140.0, 274.0)),
			new ArrayList<>(Arrays.asList(30.0, 30.0, 30.0, 30.0))
		);
		assertEquals(-98.529, rxPower[0][0][0], 0.001);
		double[][] heatMap = ModelerUtils.generateHeatMap(
			500,
			4,
			rxPower
		);
		assertEquals(-77.495, heatMap[0][0], 0.001);
		double[][] sinr = ModelerUtils.generateSinr(
			500,
			4,
			rxPower
		);
		assertEquals(12.990, sinr[0][0], 0.001);
		double metric = ModelerUtils.calculateTPCMetrics(
			500,
			heatMap,
			sinr
		);
		assertEquals(0.861, metric, 0.001);
	}

	@Test
	void testPreDot11nAggregatedWifiScanEntry() {
		final long obsoletionPeriodMs = 900000;

		final String apA = "aaaaaaaaaaaa";
		final String bssidA = "aa:aa:aa:aa:aa:aa";
		final String apB = "bbbbbbbbbbbb";
		final String bssidB = "bb:bb:bb:bb:bb:bb";
		final String apC = "cccccccccccc";
		final String bssidC = "cc:cc:cc:cc:cc:cc";
		DataModel dataModel = new DataModel();

		long refTimeMs = TestUtils.DEFAULT_WIFISCANENTRY_TIME.toEpochMilli();

		// if there are no scan entries, there should be no aggregates
		dataModel.latestWifiScans.put(apB, new LinkedList<>());
		dataModel.latestWifiScans.put(apC, new LinkedList<>());
		dataModel.latestWifiScans.get(apC).add(new ArrayList<>());
		assertTrue(
			ModelerUtils.getAggregatedWifiScans(
				dataModel,
				obsoletionPeriodMs,
				new MeanAggregator(),
				refTimeMs
			).isEmpty()
		);

		/*
		 * When only apB conducts a scan, and receives one response from apA, that
		 * response should be the "aggregate response" from apA to apB, and apA and apC
		 * should have no aggregates for any BSSID.
		 */
		WifiScanEntry entryAToB1 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB1.signal = -60;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB1));
		Map<String, Map<String, WifiScanEntry>> aggregateMap =
			ModelerUtils.getAggregatedWifiScans(
				dataModel,
				obsoletionPeriodMs,
				new MeanAggregator(),
				refTimeMs
			);
		assertFalse(aggregateMap.containsKey(apA));
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(entryAToB1, aggregateMap.get(apB).get(bssidA));
		assertFalse(aggregateMap.get(apB).containsKey(bssidB));
		assertFalse(aggregateMap.get(apB).containsKey(bssidC));

		// add another scan with one entry from apA to apB and check the aggregation
		WifiScanEntry entryAToB2 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB2.signal = -62;
		entryAToB2.unixTimeMs += 60000; // 1 min later
		refTimeMs = entryAToB2.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB2));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		WifiScanEntry expectedAggregatedEntryAToB =
			new WifiScanEntry(entryAToB2);
		expectedAggregatedEntryAToB.signal = -61; // average of -60 and -62
		assertFalse(aggregateMap.containsKey(apA));
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
		assertFalse(aggregateMap.get(apB).containsKey(bssidB));
		assertFalse(aggregateMap.get(apB).containsKey(bssidC));

		// test the obsoletion period boundaries
		// test the inclusive non-obsolete boundary
		WifiScanEntry entryAToB3 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB3.signal = -64;
		entryAToB3.unixTimeMs += obsoletionPeriodMs;
		refTimeMs = entryAToB3.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB3));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB3);
		expectedAggregatedEntryAToB.signal = -62; // average of -60, -62, and -64;
		assertFalse(aggregateMap.containsKey(apA));
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
		assertFalse(aggregateMap.get(apB).containsKey(bssidB));
		assertFalse(aggregateMap.get(apB).containsKey(bssidC));
		// test moving the boundary by 1 ms and excluding the earliest entry
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs - 1,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB.signal = -63; // average of -62 and -64
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
		// test an obsoletion period of 0 ms
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			0,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB.signal = -64; // latest rssid
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
		/*
		 * Test that the obsoletion period starts counting backwards from the
		 * time of the most recent entry for each (ap, bssid) tuple. Also test
		 * that if there is no recent entry, the latest entry is returned.
		 */
		WifiScanEntry entryCToB1 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidC);
		entryCToB1.signal = -70;
		entryCToB1.unixTimeMs += 2 * obsoletionPeriodMs;
		refTimeMs = entryCToB1.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryCToB1));
		// now only the entryCToB1 should show up
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			0,
			new MeanAggregator(),
			refTimeMs
		);
		WifiScanEntry expectedAggregatedEntryCToB =
			new WifiScanEntry(entryCToB1);
		assertEquals(
			expectedAggregatedEntryCToB,
			aggregateMap.get(apB).get(bssidC)
		);
		assertEquals(entryAToB3, aggregateMap.get(apB).get(bssidA));

		// test multiple entries in one scan and scans from multiple APs
		WifiScanEntry entryAToB4 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB4.signal = -80;
		entryAToB4.unixTimeMs += 3 * obsoletionPeriodMs;
		WifiScanEntry entryCToB2 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidC);
		entryCToB2.signal = -80;
		entryCToB2.unixTimeMs += 3 * obsoletionPeriodMs;
		WifiScanEntry entryBToA1 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidB);
		entryBToA1.signal = -60;
		entryBToA1.unixTimeMs += 3 * obsoletionPeriodMs;
		WifiScanEntry entryCToA1 =
			TestUtils.createWifiScanEntryWithBssid(1, bssidC);
		entryCToA1.signal = -60;
		entryCToA1.unixTimeMs += 3 * obsoletionPeriodMs;
		refTimeMs = entryCToA1.unixTimeMs;
		dataModel.latestWifiScans.get(apB)
			.add(Arrays.asList(entryCToB2, entryAToB4));
		dataModel.latestWifiScans.put(apA, new LinkedList<>());
		dataModel.latestWifiScans.get(apA)
			.add(Arrays.asList(entryBToA1, entryCToA1));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryCToB = new WifiScanEntry(entryCToB2);
		expectedAggregatedEntryCToB.signal = -75; // average of -70 and-80
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB4);
		WifiScanEntry expectedAggregatedEntryCToA =
			new WifiScanEntry(entryCToA1);
		WifiScanEntry expectedAggregatedEntryBToA =
			new WifiScanEntry(entryBToA1);
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(
			expectedAggregatedEntryCToB,
			aggregateMap.get(apB).get(bssidC)
		);
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
		assertFalse(aggregateMap.get(apB).containsKey(bssidB));
		assertEquals(
			expectedAggregatedEntryCToA,
			aggregateMap.get(apA).get(bssidC)
		);
		assertEquals(
			expectedAggregatedEntryBToA,
			aggregateMap.get(apA).get(bssidB)
		);
		assertFalse(aggregateMap.get(apA).containsKey(bssidA));

		// test that entries are not aggregated when channel information does not match
		WifiScanEntry entryBToA2 =
			TestUtils.createWifiScanEntryWithBssid(2, bssidB); // different channel
		entryBToA2.signal = -62;
		entryBToA2.unixTimeMs += 3 * obsoletionPeriodMs + 1; // 1 sec after the most recent B->A response
		refTimeMs = entryBToA2.unixTimeMs;
		dataModel.latestWifiScans.get(apA).add(Arrays.asList(entryBToA2));
		expectedAggregatedEntryBToA = new WifiScanEntry(entryBToA2);
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		assertEquals(
			expectedAggregatedEntryBToA,
			aggregateMap.get(apA).get(bssidB)
		);

		// test out of order wifiscans
		WifiScanEntry entryBToA3 =
			TestUtils.createWifiScanEntryWithBssid(2, bssidB); // different channel
		entryBToA3.signal = -64;
		entryBToA3.unixTimeMs += 3 * obsoletionPeriodMs - 1;
		dataModel.latestWifiScans.get(apA).add(Arrays.asList(entryBToA3));
		expectedAggregatedEntryBToA = new WifiScanEntry(entryBToA2); // use the most recent entry
		expectedAggregatedEntryBToA.signal = -63; // average of -62 and -64 (skipping -60, different channel)
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		assertEquals(
			expectedAggregatedEntryBToA,
			aggregateMap.get(apA).get(bssidB)
		);
	}

	@Test
	void testPostDot11nAggregatedWifiScanEntry() {
		final long obsoletionPeriodMs = 900000;

		final String bssidA = "aa:aa:aa:aa:aa:aa";
		final String apB = "bbbbbbbbbbbb";
		DataModel dataModel = new DataModel();

		long refTimeMs = TestUtils.DEFAULT_WIFISCANENTRY_TIME.toEpochMilli();

		// First, test that entries for different channels do not aggregate (this could
		// have been tested in testPreDot11nAggregatedWifiScanEntry)
		byte primaryChannel = 1; // first entry on channel 1
		String htOper = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		WifiScanEntry entryAToB1 = TestUtils
			.createWifiScanEntryWithWidth(bssidA, primaryChannel, htOper, null);
		entryAToB1.signal = -60;
		dataModel.latestWifiScans.put(apB, new LinkedList<>());
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB1));
		Map<String, Map<String, WifiScanEntry>> aggregateMap =
			ModelerUtils.getAggregatedWifiScans(
				dataModel,
				obsoletionPeriodMs,
				new MeanAggregator(),
				refTimeMs
			);
		WifiScanEntry expectedAggregatedEntryAToB =
			new WifiScanEntry(entryAToB1);
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		primaryChannel = 6; // second entry on channel 6, should only aggregate this one
		htOper = "BgAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		WifiScanEntry entryAToB2 = TestUtils
			.createWifiScanEntryWithWidth(bssidA, primaryChannel, htOper, null);
		entryAToB2.signal = -62;
		entryAToB2.unixTimeMs += 60000; // 1 min after previous entry
		refTimeMs = entryAToB2.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB2));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB2);
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		primaryChannel = 1; // third entry on channel 1 again, should aggregate first and third entry
		htOper = "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		WifiScanEntry entryAToB3 = TestUtils
			.createWifiScanEntryWithWidth(bssidA, primaryChannel, htOper, null);
		entryAToB3.signal = -70;
		entryAToB3.unixTimeMs += 120000; // 1 min after previous entry
		refTimeMs = entryAToB3.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB3));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB3);
		expectedAggregatedEntryAToB.signal = -65; // average of -60 and -70 (would be -64 if the -62 entry was included)
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		// Test that entries with HT operation elements which differ in the relevant
		// fields (channel numbers and widths) are not aggregated together.
		primaryChannel = 1;
		htOper = "AQUAAAAAAAAAAAAAAAAAAAAAAAAAAA=="; // use secondary channel and wider channel
		WifiScanEntry entryAToB4 = TestUtils
			.createWifiScanEntryWithWidth(bssidA, primaryChannel, htOper, null);
		entryAToB4.signal = -72;
		entryAToB4.unixTimeMs += 180000; // 1 min after previous entry
		refTimeMs = entryAToB4.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB4));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB4);
		expectedAggregatedEntryAToB.signal = -72;
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		// Test that entries with HT operation elements with differ only in irrelevant
		// fields are aggregated together
		htOper = "AQUAAAAAgAAAAAAAAAAAAAAAAAAAAA=="; // use different Basic HT-MCS Set field
		WifiScanEntry entryAToB5 = TestUtils
			.createWifiScanEntryWithWidth(bssidA, primaryChannel, htOper, null);
		entryAToB5.signal = -74;
		entryAToB5.unixTimeMs += 240000; // 1 min after previous entry
		refTimeMs = entryAToB5.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB5));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB5);
		expectedAggregatedEntryAToB.signal = -73; // average of -72 and -74
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		/*
		 * Test that entries with VHT operation elements which differ in the relevant
		 * fields (channel numbers and widths) are not aggregated together. Use channel
		 * 42 (80 MHz wide), with the primary channel being 36 (contained "within" the
		 * wider channel 42).
		 */
		primaryChannel = 36;
		// use secondary channel offset field of 1 and sta channel width field of 1
		htOper = "JAUAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		String vhtOper = "ASoAAAA=";
		WifiScanEntry entryAToB6 = TestUtils.createWifiScanEntryWithWidth(
			bssidA,
			primaryChannel,
			htOper,
			vhtOper
		);
		entryAToB6.signal = -74;
		entryAToB6.unixTimeMs += 300000; // 1 min after previous entry
		refTimeMs = entryAToB6.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB6));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB6);
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		/*
		 * Switch to channel 50 (160 MHz wide) which still "contains" channel 36. All
		 * other fields stay the same. In reality, the entry's channel field may change,
		 * but here it remains the same, just to test vhtOper.
		 */
		vhtOper = "ASoyAAA=";
		WifiScanEntry entryAToB7 = TestUtils.createWifiScanEntryWithWidth(
			bssidA,
			primaryChannel,
			htOper,
			vhtOper
		);
		entryAToB7.signal = -76;
		entryAToB7.unixTimeMs += 360000; // 1 min after previous entry
		refTimeMs = entryAToB7.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB7));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB7);
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);

		// Test that entries with VHT operation elements with differ only in irrelevant
		// fields are aggregated together
		vhtOper = "ASoygAA="; // use different Basic VHT-MCS Set and NSS Set field
		WifiScanEntry entryAToB8 = TestUtils.createWifiScanEntryWithWidth(
			bssidA,
			primaryChannel,
			htOper,
			vhtOper
		);
		entryAToB8.signal = -78;
		entryAToB8.unixTimeMs += 420000; // 1 min after previous entry
		refTimeMs = entryAToB8.unixTimeMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB8));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			refTimeMs
		);
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB8);
		expectedAggregatedEntryAToB.signal = -77; // average of -78 and -76
		assertEquals(
			expectedAggregatedEntryAToB,
			aggregateMap.get(apB).get(bssidA)
		);
	}

	@Test
	void testEmptyAggregatedStates() throws IllegalArgumentException,
		IllegalAccessException, NoSuchFieldException, SecurityException {
		final long obsoletionPeriodMs = 900000;

		final String serialNumberA = "aa:aa:aa:aa:aa:aa";
		final String serialNumberB = "bb:bb:bb:bb:bb:bb";
		final String serialNumberC = "cc:cc:cc:cc:cc:cc";

		long refTimeMs = TestUtils.DEFAULT_WIFISCANENTRY_TIME.toEpochMilli();

		DataModel dataModel = new DataModel();

		// Test empty State list.
		dataModel.latestStates.put(serialNumberA, new ArrayList<>());
		dataModel.latestStates.put(serialNumberB, new ArrayList<>());
		dataModel.latestStates.put(serialNumberC, new ArrayList<>());

		assertTrue(
			ModelerUtils.getAggregatedStates(
				dataModel,
				obsoletionPeriodMs,
				refTimeMs,
				new ArrayList<>(
					Arrays.asList("channel", "channel_width", "tx_power")
				),
				new ArrayList<>(Arrays.asList("rssi"))
			).isEmpty()
		);
	}

	@Test
	void testValidAggregatedStates()
		throws JsonMappingException, JsonProcessingException,
		IllegalArgumentException, IllegalAccessException, NoSuchFieldException,
		SecurityException {
		final long obsoletionPeriodMs = 60000000;
		final String serialNumberA = "aaaaaaaaaaaa";
		final String bssidA1 = "aa:aa:aa:aa:aa:a1";
		final String bssidA2 = "aa:aa:aa:aa:aa:a2";
		final String serialNumberB = "bbbbbbbbbbbb";
		final String bssidB = "bb:bb:bb:bb:bb:bb";
		final String serialNumberC = "cccccccccccc";
		final String bssidC = "cc:cc:cc:cc:cc:cc";

		long refTimeMs = TestUtils.DEFAULT_LOCAL_TIME;

		DataModel dataModel = new DataModel();

		List<String> aggregatedKeys = new ArrayList<>(
			Arrays.asList("channel", "channel_width", "tx_power")
		);
		List<String> aggregatedFields = new ArrayList<>(Arrays.asList("rssi"));

		// This serie of StateA is used to test a valid input states.
		State time1ToStateA = TestUtils.createState(
			1,
			80,
			1000,
			bssidA1,
			new String[] { "stationA1_1", "stationA1_2" },
			new int[] { -84, -67 },
			2,
			40,
			2000,
			bssidA2,
			new String[] { "stationA2_1" },
			new int[] { -80 },
			TestUtils.DEFAULT_LOCAL_TIME
		);

		State time2ToStateA = TestUtils.createState(
			1,
			80,
			1000,
			bssidA1,
			new String[] { "stationA1_2", "stationA1_3" },
			new int[] { 27, 100 },
			2,
			40,
			2000,
			bssidA2,
			new String[] { "stationA2_1" },
			new int[] { 180 },
			TestUtils.DEFAULT_LOCAL_TIME - 800
		);

		//As State time3ToStateA is obsolescent, it should not be aggregated.
		State time3ToStateA = TestUtils.createState(
			1,
			80,
			1000,
			bssidA1,
			new String[] { "stationA1_1", "stationA1_2", "stationA1_4" },
			new int[] { 24, 27, 1000 },
			2,
			40,
			2000,
			bssidA2,
			new String[] { "stationA2_1", "staionA2_2" },
			new int[] { 180, 180 },
			// Set the localtime exactly obsolescent
			TestUtils.DEFAULT_LOCAL_TIME - obsoletionPeriodMs - 1
		);

		dataModel.latestStates.put(
			serialNumberA,
			new ArrayList<>(
				Arrays.asList(
					time1ToStateA,
					time2ToStateA,
					time3ToStateA
				)
			)
		);
		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedMap =
			ModelerUtils.getAggregatedStates(
				dataModel,
				obsoletionPeriodMs,
				refTimeMs,
				aggregatedKeys,
				aggregatedFields
			);
		assertFalse(aggregatedMap.containsKey(serialNumberB));
		assertFalse(aggregatedMap.containsKey(serialNumberC));
		assertTrue(
			aggregatedMap.get(serialNumberA).get(bssidA1).containsKey("stationA1_1")
		);
		assertTrue(
			aggregatedMap.get(serialNumberA).get(bssidA1).containsKey("stationA1_2")
		);
		assertTrue(
			aggregatedMap.get(serialNumberA).get(bssidA2).containsKey("stationA2_1")
		);
		assertTrue(
			aggregatedMap.get(serialNumberA).get(bssidA1).containsKey("stationA1_3")
		);

		//obsolescent States should not be aggregated.
		assertFalse(
			aggregatedMap.get(serialNumberA).get(bssidA1).containsKey("stationA1_4")
		);
		assertFalse(
			aggregatedMap.get(serialNumberA).get(bssidA2).containsKey("stationA2_2")
		);

		ObjectMapper mapper = new ObjectMapper();

		JsonNode expectedAggregatedKey1 = mapper.readTree(
			TestUtils.createRadioObject(
				new String[] { "channel", "channel_width", "tx_power" },
				new int[] { 1, 80, 1000 }
			).toString()
		);

		JsonNode expectedAggregatedKey2 = mapper.readTree(
			TestUtils.createRadioObject(
				new String[] { "channel", "channel_width", "tx_power" },
				new int[] { 2, 40, 2000 }
			).toString()
		);

		assertEquals(
			aggregatedMap.get(serialNumberA).get(bssidA1).get("stationA1_1").get(expectedAggregatedKey1).get("rssi").getList(), Arrays.asList(-84.0)
		);
		assertEquals(
			aggregatedMap.get(serialNumberA).get(bssidA1).get("stationA1_2").get(expectedAggregatedKey1).get("rssi").getList(), Arrays.asList(-67.0, 27.0)
		);
		assertEquals(
			aggregatedMap.get(serialNumberA).get(bssidA2).get("stationA2_1").get(expectedAggregatedKey2).get("rssi").getList(), Arrays.asList(-80.0, 180.0)
		);

		// Test on same station operating on different channel.
		State channel1ToStateA = TestUtils.createState(
			2,
			40,
			2000,
			bssidA1,
			new String[] { "stationA1_1", "stationA1_2" },
			new int[] { -1, -2 },
			TestUtils.DEFAULT_LOCAL_TIME - 100
		);

		dataModel.latestStates
			.get(serialNumberA)
			.add(channel1ToStateA);

		// Test more clients operate on the same channel (stationB and stationA)
		State time1ToStateB = TestUtils.createState(
			1,
			80,
			1000,
			bssidB,
			new String[] { "stationB1" },
			new int[] { -30 },
			TestUtils.DEFAULT_LOCAL_TIME
		);
		dataModel.latestStates
			.computeIfAbsent(serialNumberB, k -> new ArrayList<>())
			.add(time1ToStateB);

		State time1ToStateC = TestUtils.createState(
			2,
			40,
			2000,
			bssidC,
			new String[] { "stationC1" },
			new int[] { -100 },
			TestUtils.DEFAULT_LOCAL_TIME
		);
		dataModel.latestStates
			.computeIfAbsent(serialNumberC, k -> new ArrayList<>())
			.add(time1ToStateC);

		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedMap2 =
			ModelerUtils.getAggregatedStates(
				dataModel,
				obsoletionPeriodMs,
				refTimeMs,
				aggregatedKeys,
				aggregatedFields
			);

		assertEquals(
			aggregatedMap2.get(serialNumberA).get(bssidA1).get("stationA1_1").get(expectedAggregatedKey2).get("rssi").getList(), Arrays.asList(-1.0)
		);
		assertEquals(
			aggregatedMap2.get(serialNumberA).get(bssidA1).get("stationA1_2").get(expectedAggregatedKey2).get("rssi").getList(), Arrays.asList(-2.0)
		);
		assertEquals(
			aggregatedMap2.get(serialNumberB).get(bssidB).get("stationB1").get(expectedAggregatedKey1).get("rssi").getList(), Arrays.asList(-30.0)
		);
		assertEquals(
			aggregatedMap2.get(serialNumberC).get(bssidC).get("stationC1").get(expectedAggregatedKey2).get("rssi").getList(), Arrays.asList(-100.0)
		);

	}

	@Test
	void testAggregatedMapToJson()
		throws JsonMappingException, JsonProcessingException {
		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedMap =
			new HashMap<>();
		JsonNode aggKey1 = new ObjectMapper().readTree(
			TestUtils.createRadioObject(
				new String[] { "a", "b" },
				new int[] { 1, 2 }
			).toString()
		);

		JsonNode aggKey2 = new ObjectMapper().readTree(
			TestUtils.createRadioObject(
				new String[] { "a", "b" },
				new int[] { 2, 1 }
			).toString()
		);
		aggregatedMap.computeIfAbsent("serial number A", k -> new HashMap<>())
			.computeIfAbsent("bssidA1", k -> new HashMap<>())
			.computeIfAbsent("stationA1_1", k -> new HashMap<>())
			.computeIfAbsent(aggKey1, k -> new HashMap<>())
			.computeIfAbsent(
				"rssi",
				k -> new MeanAggregator(new ArrayList<>(Arrays.asList(1.2, 2.1)))
			);

		aggregatedMap.computeIfAbsent("serial number A", k -> new HashMap<>())
			.computeIfAbsent("bssidA1", k -> new HashMap<>())
			.computeIfAbsent("stationA1_1", k -> new HashMap<>())
			.computeIfAbsent(aggKey2, k -> new HashMap<>())
			.computeIfAbsent(
				"rssi",
				k -> new MeanAggregator(new ArrayList<>(Arrays.asList(1.2, 2.1)))
			);

		aggregatedMap.computeIfAbsent("serial number A", k -> new HashMap<>())
			.computeIfAbsent("bssidA1", k -> new HashMap<>())
			.computeIfAbsent("stationA1_2", k -> new HashMap<>())
			.computeIfAbsent(aggKey2, k -> new HashMap<>())
			.computeIfAbsent(
				"rssi",
				k -> new MeanAggregator(
					new ArrayList<>(Arrays.asList(100.0, 200.0))
				)
			);

		assertEquals(
			"{\"serial number A\":" +
				"{\"bssidA1\":" +
				"{\"stationA1_1\":" +
				"{\"{\\\"a\\\":1,\\\"b\\\":2}\":{\"rssi\":\"1.65\"}," +
				"\"{\\\"a\\\":2,\\\"b\\\":1}\":{\"rssi\":\"1.65\"}}," +
				"\"stationA1_2\":" +
				"{\"{\\\"a\\\":2,\\\"b\\\":1}\":{\"rssi\":\"150.0\"}}}}}",
			ModelerUtils
				.aggregatedStatesToJson(aggregatedMap, AggregationType.MEAN)
		);
		assertEquals(
			"{\"serial number A\":" +
				"{\"bssidA1\":" +
				"{\"stationA1_1\":" +
				"{\"{\\\"a\\\":1,\\\"b\\\":2}\":" +
				"{\"rssi\":[\"1.2\",\"2.1\"]}," +
				"\"{\\\"a\\\":2,\\\"b\\\":1}\":" +
				"{\"rssi\":[\"1.2\",\"2.1\"]}}," +
				"\"stationA1_2\":" +
				"{\"{\\\"a\\\":2,\\\"b\\\":1}\":" +
				"{\"rssi\":[\"100.0\",\"200.0\"]}}}}}",
			ModelerUtils
				.aggregatedStatesToJson(aggregatedMap, AggregationType.LIST)
		);
	}

}
