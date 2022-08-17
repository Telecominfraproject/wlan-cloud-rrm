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

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralConstants;

@TestMethodOrder(OrderAnnotation.class)
public class LeastUsedChannelOptimizerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	@Test
	@Order(1)
	void test5G() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (48)
		int aExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(36, 40, 44, 149))
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
			deviceB, TestUtils.createState(40, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to least occupied (36)
		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		int cExpectedChannel = channelsC.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(2)
	void test2G() throws Exception {
		final String band = UCentralConstants.BAND_2G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (1)
		int aExpectedChannel = 1;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
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
			deviceB, TestUtils.createState(6, channelWidth, dummyBssid)
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
			deviceC, TestUtils.createState(6, channelWidth, dummyBssid)
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

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(3)
	void testWithUserChannels() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";
		final int channelWidth = 20;
		int userChannel = 149;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);
		DeviceConfig apConfig = new DeviceConfig();
		apConfig.userChannels = new HashMap<>();
		apConfig.userChannels.put(UCentralConstants.BAND_5G, userChannel);
		deviceDataManager.setDeviceApConfig(deviceA, apConfig);
		deviceDataManager.setDeviceApConfig(deviceB, apConfig);
		deviceDataManager.setDeviceApConfig(deviceC, apConfig);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A, B, C should just be assigned to the same userChannel
		int aExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(36, 40, 44, 149))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, userChannel);
		expected.put(deviceA, radioMapA);

		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsB.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 40)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(40, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, userChannel);
		expected.put(deviceB, radioMapB);

		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, userChannel);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(4)
	void testWithAllowedChannels() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);
		DeviceConfig apConfig = new DeviceConfig();
		apConfig.allowedChannels = new HashMap<>();
		apConfig.allowedChannels.put(UCentralConstants.BAND_5G, Arrays.asList(48, 165));
		deviceDataManager.setDeviceApConfig(deviceA, apConfig);
		deviceDataManager.setDeviceApConfig(deviceB, apConfig);
		deviceDataManager.setDeviceApConfig(deviceC, apConfig);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel and the current channel is in allowedChannels,
		// so stay on it (48)
		int aExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(36, 40, 44, 149))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> Assign to only free channel and
		// the free channel is in allowedChannels (165)
		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsB.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 40)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(40, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, 165);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to least occupied in allowedChannels (48)
		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, 48);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(5)
	void testBandwidth40() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";
		final String dummyBssid = "ee:ee:ee:ee:ee:ee";
		final int channelWidth = 40;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC, deviceD)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (48)
		int aExpectedChannel = 157;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(36, 40, 44, 149))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> No free channels (because the neighboring channels are occupied)
		// assign to least occupied (157)
		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(Arrays.asList(40, 48, 153, 161));
		channelsB.addAll(Arrays.asList(40, 48, 153, 161));
		int bExpectedChannel = channelsB.removeLast() - 4; // upper extension
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 40)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(40, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to least occupied (36)
		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		int cExpectedChannel = channelsC.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		// D ->  Assign to only free channel for 40 MHz (157)
		LinkedList<Integer> channelsD = new LinkedList<>();
		channelsD.addAll(Arrays.asList(36, 44, 149, 157));
		int dExpectedChannel = channelsD.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceD, TestUtils.createDeviceStatus(band, 40)
		);
		dataModel.latestState.put(
			deviceD, TestUtils.createState(40, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceD, Arrays.asList(TestUtils.createWifiScanList(channelsD))
		);
		Map<String, Integer> radioMapD = new HashMap<>();
		radioMapD.put(band, dExpectedChannel);
		expected.put(deviceD, radioMapD);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(6)
	void testBandwidth80() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";
		final String deviceE = "eeeeeeeeeeee";
		final String dummyBssid = "ff:ff:ff:ff:ff:ff";
		final int channelWidth = 80;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE, deviceA, deviceB, deviceC, deviceD, deviceE
			)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (36)
		int aExpectedChannel = 36;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(149, 157, 165))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> Assign to only free channel (149)
		LinkedList<Integer> channelsB = new LinkedList<>();
		channelsB.addAll(Arrays.asList(40, 48, 149));
		int bExpectedChannel = channelsB.removeLast();
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(36, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB, Arrays.asList(TestUtils.createWifiScanList(channelsB))
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to least occupied (36)
		LinkedList<Integer> channelsC = new LinkedList<>();
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		int cExpectedChannel = channelsC.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC))
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		// D ->  No free channels (because the neighboring channels are occupied)
		// assign to least occupied (149)
		LinkedList<Integer> channelsD = new LinkedList<>();
		channelsD.addAll(Arrays.asList(40, 48, 153, 161));
		channelsD.addAll(Arrays.asList(40, 48, 153, 161));
		int dExpectedChannel = channelsD.removeLast() - 12;
		dataModel.latestDeviceStatus.put(
			deviceD, TestUtils.createDeviceStatus(band, 36)
		);
		dataModel.latestState.put(
			deviceD, TestUtils.createState(36, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceD, Arrays.asList(TestUtils.createWifiScanList(channelsD))
		);
		Map<String, Integer> radioMapD = new HashMap<>();
		radioMapD.put(band, dExpectedChannel);
		expected.put(deviceD, radioMapD);


		// E -> The allowedChannels are not valid since 80 MHz supports 36 and 149
		// The availableChannelsList will fall back to the default available channels
		// No APs on current channel, so stay on it (36)
		DeviceConfig apConfig = new DeviceConfig();
		apConfig.allowedChannels = new HashMap<>();
		apConfig.allowedChannels.put(UCentralConstants.BAND_5G, Arrays.asList(48, 165));
		deviceDataManager.setDeviceApConfig(deviceE, apConfig);
		int eExpectedChannel = 36;
		dataModel.latestDeviceStatus.put(
			deviceE, TestUtils.createDeviceStatus(band, eExpectedChannel)
		);
		dataModel.latestState.put(
			deviceE, TestUtils.createState(eExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceE,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(149, 157, 165))
			)
		);
		Map<String, Integer> radioMapE = new HashMap<>();
		radioMapE.put(band, eExpectedChannel);
		expected.put(deviceE, radioMapE);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

	@Test
	@Order(7)
	void testBandwidthScan() throws Exception {
		final String band = UCentralConstants.BAND_5G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String dummyBssid = "dd:dd:dd:dd:dd:dd";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB, deviceC)
		);

		DataModel dataModel = new DataModel();
		Map<String, Map<String, Integer>> expected = new HashMap<>();

		// A -> No APs on current channel, so stay on it (48)
		int aExpectedChannel = 48;
		dataModel.latestDeviceStatus.put(
			deviceA, TestUtils.createDeviceStatus(band, aExpectedChannel)
		);
		dataModel.latestState.put(
			deviceA, TestUtils.createState(aExpectedChannel, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceA,
			Arrays.asList(
				TestUtils.createWifiScanList(Arrays.asList(36, 157))
			)
		);
		Map<String, Integer> radioMapA = new HashMap<>();
		radioMapA.put(band, aExpectedChannel);
		expected.put(deviceA, radioMapA);

		// B -> Same setting as A, but the scan results are bandwidth aware
		// Assign to only free channel (165)
		int bExpectedChannel = 165;
		dataModel.latestDeviceStatus.put(
			deviceB, TestUtils.createDeviceStatus(band, 48)
		);
		dataModel.latestState.put(
			deviceB, TestUtils.createState(48, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceB,
			Arrays.asList(
				TestUtils.createWifiScanListWithWidth(
					null,
					Arrays.asList(36, 157),
					Arrays.asList(
						"JAUWAAAAAAAAAAAAAAAAAAAAAAAAAA==",
						"nQUAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
					),
					Arrays.asList("ASoAAAA=", "AZsAAAA=")
				)
			)
		);
		Map<String, Integer> radioMapB = new HashMap<>();
		radioMapB.put(band, bExpectedChannel);
		expected.put(deviceB, radioMapB);

		// C -> No free channels, assign to least occupied (36)
		LinkedList<Integer> channelsC1 = new LinkedList<>(); // bandwidth-agnostic
		LinkedList<Integer> channelsC2 = new LinkedList<>(); // bandwidth-aware
		channelsC1.addAll(ChannelOptimizer.AVAILABLE_CHANNELS_BAND.get(band));
		channelsC2.addAll(Arrays.asList(36, 157, 165));
		int cExpectedChannel = channelsC1.removeFirst();
		dataModel.latestDeviceStatus.put(
			deviceC, TestUtils.createDeviceStatus(band, 149)
		);
		dataModel.latestState.put(
			deviceC, TestUtils.createState(149, channelWidth, dummyBssid)
		);
		dataModel.latestWifiScans.put(
			deviceC, Arrays.asList(TestUtils.createWifiScanList(channelsC1))
		);
		dataModel.latestWifiScans.put(
			deviceC,
			Arrays.asList(
				TestUtils.createWifiScanListWithWidth(
					null,
					channelsC2,
					Arrays.asList(
						"JAUWAAAAAAAAAAAAAAAAAAAAAAAAAA==",
						"nQUAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
					),
					Arrays.asList("ASoAAAA=", "AZsAAAA=")
				)
			)
		);
		Map<String, Integer> radioMapC = new HashMap<>();
		radioMapC.put(band, cExpectedChannel);
		expected.put(deviceC, radioMapC);

		ChannelOptimizer optimizer = new LeastUsedChannelOptimizer(
			dataModel, TEST_ZONE, deviceDataManager
		);
		assertEquals(expected, optimizer.computeChannelMap());
	}

}
