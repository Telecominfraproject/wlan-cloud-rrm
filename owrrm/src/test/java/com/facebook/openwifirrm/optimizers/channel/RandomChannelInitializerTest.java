/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;

@TestMethodOrder(OrderAnnotation.class)
public class RandomChannelInitializerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	@Test
	@Order(1)
	void test1() throws Exception {
		final String band = UCentralConstants.BAND_2G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceABssid = "aa:aa:aa:aa:aa:aa";
		final String deviceBBssid = "bb:bb:bb:bb:bb:bb";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB)
		);

		// A and B will be assigned to the same channel
		DataModel dataModel = new DataModel();
		dataModel.latestStates.put(
			deviceA,
			Arrays.asList(
				TestUtils.createState(6, channelWidth, deviceABssid)
			)
		);
		dataModel.latestStates.put(
			deviceB,
			Arrays.asList(
				TestUtils.createState(11, channelWidth, deviceBBssid)
			)
		);
		dataModel.latestDeviceStatusRadios.put(
			deviceA,
			TestUtils.createDeviceStatus(band, 7)
		);
		dataModel.latestDeviceStatusRadios.put(
			deviceB,
			TestUtils.createDeviceStatus(band, 8)
		);

		ChannelOptimizer optimizer = new RandomChannelInitializer(
			dataModel,
			TEST_ZONE,
			deviceDataManager
		);
		Map<String, Map<String, Integer>> channelMap =
			optimizer.computeChannelMap();

		assertEquals(channelMap.get(deviceA), channelMap.get(deviceB));
	}

	@Test
	@Order(2)
	void testSettingDifferentChannelPerAp() throws Exception {
		final String band = UCentralConstants.BAND_2G;
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceABssid = "aa:aa:aa:aa:aa:aa";
		final String deviceBBssid = "bb:bb:bb:bb:bb:bb";
		final int channelWidth = 20;

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB)
		);

		// A and B will be assigned to the same channel
		DataModel dataModel = new DataModel();
		dataModel.latestStates.put(
			deviceA,
			Arrays.asList(
				TestUtils.createState(6, channelWidth, deviceABssid)
			)
		);
		dataModel.latestStates.put(
			deviceB,
			Arrays.asList(
				TestUtils.createState(11, channelWidth, deviceBBssid)
			)
		);
		dataModel.latestDeviceStatusRadios.put(
			deviceA,
			TestUtils.createDeviceStatus(band, 7)
		);
		dataModel.latestDeviceStatusRadios.put(
			deviceB,
			TestUtils.createDeviceStatus(band, 8)
		);

		ChannelOptimizer optimizer = new RandomChannelInitializer(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			true,
			new Random(10)
		);
		Map<String, Map<String, Integer>> channelMap =
			optimizer.computeChannelMap();

		Map<String, Integer> deviceAChannel = channelMap.get(deviceA);
		Map<String, Integer> deviceBChannel = channelMap.get(deviceB);

		assertNotEquals(deviceAChannel, deviceBChannel);
	}
}
