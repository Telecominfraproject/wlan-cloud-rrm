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

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.models.State;

@TestMethodOrder(OrderAnnotation.class)
public class RandomTxPowerInitializerTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	/** Create an empty device state object. */
	private State createState() {
		return new State();
	}

	@Test
	@Order(1)
	void test1() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(TEST_ZONE, deviceA, deviceB)
		);

		DataModel dataModel = new DataModel();
		dataModel.latestState.put(deviceA, createState());
		dataModel.latestState.put(deviceB, createState());

		final int txPower = 16;
		TPC optimizer = new RandomTxPowerInitializer(
			dataModel, TEST_ZONE, deviceDataManager, txPower
		);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		assertEquals(txPower, txPowerMap.get(deviceA).get(UCentralConstants.BAND_5G));
		assertEquals(txPower, txPowerMap.get(deviceB).get(UCentralConstants.BAND_5G));
	}
}
