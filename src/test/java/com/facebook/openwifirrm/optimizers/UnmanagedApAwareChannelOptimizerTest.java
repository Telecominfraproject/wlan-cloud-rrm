/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.Constants;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;

@TestMethodOrder(OrderAnnotation.class)
public class UnmanagedApAwareChannelOptimizerTest {
	@Test
	@Order(1)
	void test5G() throws Exception {
		final String band = Constants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String bssidA = "aa:aa:aa:aa:aa:aa";
		final String bssidB = "bb:bb:bb:bb:bb:bb";
		final String bssidC = "cc:cc:cc:cc:cc:cc";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (48)
		int aExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, bssidA)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(
					Arrays.asList(36, 36, 40, 44, 149, 165, 165, 165, 165, 165)
				)
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> Assign to only free channel (165)
		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		int bExpectedChannel = channelsB.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 40)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(40, channelWidth, bssidB)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to the channel with the least weight (48)
		// since A is on 48, the weight of channel 48 is lower than the other channels
		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		LinkedList<String> bssidsC = new LinkedList<>(
			Arrays.asList(
				"dd:dd:dd:dd:dd:dd", "ee:ee:ee:ee:ee:ee", "ff:ff:ff:ff:ff:ff",
				bssidA, "gg:gg:gg:gg:gg:gg", "hh:hh:hh:hh:hh:hh",
				"ii:ii:ii:ii:ii:ii", "jj:jj:jj:jj:jj:jj", "kk:kk:kk:kk:kk:kk"
			)
		);
		int cExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, bssidC)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC, bssidsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new UnmanagedApAwareChannelOptimizer(
			dataModel, TestUtils.TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(2)
	void test2G() throws Exception {
		final String band = Constants.BAND_2G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String bssidA = "aa:aa:aa:aa:aa:aa";
		final String bssidB = "bb:bb:bb:bb:bb:bb";
		final String bssidC = "cc:cc:cc:cc:cc:cc";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TestUtils.TEST_ZONE, deviceA, deviceB, deviceC)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (1)
		int aExpectedChannel = 1;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, bssidA)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(6, 7, 8, 9, 10, 11))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> No free channels, assign to least occupied (11)
		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		int bExpectedChannel = channelsB.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 6)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(6, channelWidth, bssidB)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> Assigned to only free prioritized channel (1)
		int cExpectedChannel = 1;
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 6)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(6, channelWidth, bssidC)
		);
		dataModel.latestWifiScans.put(
			deviceC,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(6, 7, 10, 11))
			)
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new UnmanagedApAwareChannelOptimizer(
			dataModel, TestUtils.TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}
}
