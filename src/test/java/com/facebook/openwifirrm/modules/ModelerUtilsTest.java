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

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.DeviceDataManager;
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
		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(TestUtils.createTopology("test-zone", apA, apB));
		DataModel dataModel = new DataModel();

		// if there are no scans (no list or empty list), there should be no aggregates
		dataModel.latestWifiScans.put(bssidB, new LinkedList<>());
		assertTrue(ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator()).isEmpty());

		// for an AP, if there is one scan, the aggregate should just be that scan
		// for the other device, there should be no aggregate
		WifiScanEntry entryB1 = TestUtils.createWifiScanEntryWithBssid(1, bssidB);
		entryB1.signal = -60;
		dataModel.latestWifiScans.get(bssidB).add(Arrays.asList(entryB1));
		WifiScanEntry aggregatedEntryB = ModelerUtils
				.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator()).get(bssidB);
		assertFalse(dataModel.latestWifiScans.containsKey(bssidA));
		assertEquals(entryB1, aggregatedEntryB);

		// add another entry for device B and check the aggregation
		WifiScanEntry entryB2 = TestUtils.createWifiScanEntryWithBssid(1, bssidB);
		entryB2.signal = -62;
		entryB2.unixTimeMs += 60000; // 1 min later
		dataModel.latestWifiScans.get(bssidB).add(Arrays.asList(entryB2));
		aggregatedEntryB = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator())
				.get(bssidB);
		WifiScanEntry expectedAggregatedEntry = new WifiScanEntry(entryB2);
		expectedAggregatedEntry.signal = -61; // average of -60 and -62
		assertFalse(dataModel.latestWifiScans.containsKey(bssidA));
		assertEquals(expectedAggregatedEntry, aggregatedEntryB);

		// test the obsoletion period boundaries
		WifiScanEntry entryB3 = TestUtils.createWifiScanEntryWithBssid(1, bssidB);
		entryB3.signal = -64;
		entryB3.unixTimeMs += obsoletionPeriodMs;
		dataModel.latestWifiScans.get(bssidB).add(Arrays.asList(entryB3));
		aggregatedEntryB = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs, new MeanAggregator())
				.get(bssidB);
		expectedAggregatedEntry = new WifiScanEntry(entryB3);
		expectedAggregatedEntry.signal = -62; // average of -60, -62, and -64 3;
		assertEquals(expectedAggregatedEntry, aggregatedEntryB);
		aggregatedEntryB = ModelerUtils.getAggregatedWifiScans(dataModel, obsoletionPeriodMs - 1, new MeanAggregator())
				.get(bssidB);
		expectedAggregatedEntry.signal = -63; // average of -62 and -64
		assertEquals(expectedAggregatedEntry, aggregatedEntryB);
		aggregatedEntryB = ModelerUtils.getAggregatedWifiScans(dataModel, 0, new MeanAggregator()).get(bssidB);
		expectedAggregatedEntry.signal = -64; // latest rssid
		assertEquals(expectedAggregatedEntry, aggregatedEntryB);
	}

//	@Test
//	void testMultipleEntriesAndAggregationLogic() {
//		// TODO test 802.11n onwards (with non-null ht_oper and vht_oper)
// 		// TODO test matching method for ht/vht opers
//		// TODO test with multiple entries from the same scan
//	}
}
