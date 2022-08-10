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
import java.util.LinkedList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.aggregators.MeanAggregator;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;

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
			500, 4, rxPower
		);
		assertEquals(-87.494, heatMap[0][0], 0.001);
		double[][] sinr = ModelerUtils.generateSinr(
			500, 4, rxPower
		);
		assertEquals(5.995, sinr[0][0], 0.001);
		double metric = ModelerUtils.calculateTPCMetrics(
			500, heatMap, sinr
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
			500, 4, rxPower
		);
		assertEquals(-77.495, heatMap[0][0], 0.001);
		double[][] sinr = ModelerUtils.generateSinr(
			500, 4, rxPower
		);
		assertEquals(12.990, sinr[0][0], 0.001);
		double metric = ModelerUtils.calculateTPCMetrics(
			500, heatMap, sinr
		);
		assertEquals(0.861, metric, 0.001);
	}

	@Test
	void testBasicPreDot11nAggregatedWifiScanEntry() {
		final long obsoletionPeriodMs = 900000;

		final String apA = "aaaaaaaaaaaa";
		final String bssidA = "aa:aa:aa:aa:aa:aa";
		final String apB = "bbbbbbbbbbbb";
		final String bssidB = "bb:bb:bb:bb:bb:bb";
		final String apC = "cccccccccccc";
		final String bssidC = "cc:cc:cc:cc:cc:cc";
		DataModel dataModel = new DataModel();

		// if there are no scan entries, there should be no aggregates
		dataModel.latestWifiScans.put(apB, new LinkedList<>());
		dataModel.latestWifiScans.put(apC, new LinkedList<>());
		dataModel.latestWifiScans.get(apC).add(new ArrayList<>());
		assertTrue(ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator()).isEmpty());

		/*
		 * When only apB conducts a scan, and receives one response from apA, that
		 * response should be the "aggregate response" from apA to apB, and apA and apC
		 * should have no aggregates for any BSSID.
		 */
		WifiScanEntry entryAToB1 = TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB1.signal = -60;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB1));
		Map<String, Map<String, WifiScanEntry>> aggregateMap = ModelerUtils.getAggregatedWifiScans(dataModel,
				obsoletionPeriodMs, new MeanAggregator());
		assertFalse(aggregateMap.containsKey(apA));
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(entryAToB1, aggregateMap.get(apB).get(bssidA));

		// add another scan with one entry from apA to apB and check the aggregation
		WifiScanEntry entryAToB2 = TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB2.signal = -62;
		entryAToB2.unixTimeMs += 60000; // 1 min later
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB2));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator());
		WifiScanEntry expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB2);
		expectedAggregatedEntryAToB.signal = -61; // average of -60 and -62
		assertFalse(aggregateMap.containsKey(apA));
		assertFalse(aggregateMap.containsKey(apC));
		assertEquals(expectedAggregatedEntryAToB, aggregateMap.get(apB).get(bssidA));

		// test the obsoletion period boundaries
		// test the inclusive non-obsolete boundary
		WifiScanEntry entryAToB3 = TestUtils.createWifiScanEntryWithBssid(1, bssidA);
		entryAToB3.signal = -64;
		entryAToB3.unixTimeMs += obsoletionPeriodMs;
		dataModel.latestWifiScans.get(apB).add(Arrays.asList(entryAToB3));
		aggregateMap = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator());
		expectedAggregatedEntryAToB = new WifiScanEntry(entryAToB3);
		expectedAggregatedEntryAToB.signal = -62; // average of -60, -62, and -64 3;
		assertEquals(expectedAggregatedEntryAToB, aggregateMap.get(apB).get(bssidA));
		// test moving the boundary by 1 ms and excluding the earliest entry
		aggregateMap = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs - 1, new MeanAggregator());
		expectedAggregatedEntryAToB.signal = -63; // average of -62 and -64
		assertEquals(expectedAggregatedEntryAToB, aggregateMap.get(apB).get(bssidA));
		// test an obsoletion period of 0 ms
		aggregateMap = ModelerUtils.getAggregatedWifiScans(dataModel, 0, new MeanAggregator());
		expectedAggregatedEntryAToB.signal = -64; // latest rssid
		assertEquals(expectedAggregatedEntryAToB, aggregateMap.get(apB).get(bssidA));
	}

//	@Test
//	void testMultipleEntriesAndAggregationLogic() {
//		// TODO test 802.11n onwards (with non-null ht_oper and vht_oper)
// 		// TODO test matching method for ht/vht opers
//		// TODO test with multiple entries from the same scan
//	}
}
