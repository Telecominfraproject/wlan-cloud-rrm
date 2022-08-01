/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonObject;

@TestMethodOrder(OrderAnnotation.class)
public class MeasurementBasedApClientTPCTest {

	/** Create a device state object containing the given parameters. */
	private State createState(
		String serialNumber, int curTxPower, int bandwidth, int... clientRssi
	) {
		State state = new State();
		state.radios = new JsonObject[] { new JsonObject() };
		state.radios[0].addProperty("channel", 36);
		state.radios[0].addProperty(
			"channel_width", Integer.toString(bandwidth)
		);
		state.radios[0].addProperty("tx_power", curTxPower);
		state.interfaces = new State.Interface[] { state.new Interface() };
		state.interfaces[0].ssids = new State.Interface.SSID[] {
			state.interfaces[0].new SSID()
		};
		state.interfaces[0].ssids[0].ssid = "test-ssid-" + serialNumber;
		state.interfaces[0].ssids[0].associations =
			new State.Interface.SSID.Association[clientRssi.length];
		for (int i = 0; i < clientRssi.length; i++) {
			State.Interface.SSID.Association client =
				state.interfaces[0].ssids[0].new Association();
			client.bssid = client.station = "test-client-" + i;
			client.rssi = clientRssi[i];
			state.interfaces[0].ssids[0].associations[i] = client;
		}
		return state;
	}

	@Test
	@Order(1)
	void test1() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";
		final String deviceE = "eeeeeeeeeeee";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
						TestUtils.TEST_ZONE, deviceA, deviceB, deviceC, deviceD, deviceE
			)
		);

		DataModel dataModel = new DataModel();
		dataModel.latestState.put(
			deviceA,
			createState(deviceA, 20 /*txPower*/, 20 /*bandwidth*/)
		);
		dataModel.latestState.put(
			deviceB,
			createState(deviceB, 20 /*txPower*/, 20 /*bandwidth*/, -65)
		);
		dataModel.latestState.put(
			deviceC,
			createState(deviceC, 21 /*txPower*/, 40 /*bandwidth*/, -65, -73, -58)
		);
		dataModel.latestState.put(
			deviceD,
			createState(deviceD, 22 /*txPower*/, 20 /*bandwidth*/, -80)
		);
		dataModel.latestState.put(
			deviceE,
			createState(deviceE, 23 /*txPower*/, 20 /*bandwidth*/, -45)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(dataModel, TestUtils.TEST_ZONE, deviceDataManager);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// Device A: no clients
		assertEquals(10, txPowerMap.get(deviceA).get("5G"));

		// Device B: 1 client with RSSI -65
		assertEquals(14, txPowerMap.get(deviceB).get("5G"));

		// Device C: 3 clients with min. RSSI -73
		assertEquals(26, txPowerMap.get(deviceC).get("5G"));

		// Device D: 1 client with RSSI -80 => set to max txPower for MCS 7
		assertEquals(28, txPowerMap.get(deviceD).get("5G"));

		// Device E: 1 client with RSSI -45 => set to min txPower
		assertEquals(TPC.MIN_TX_POWER, txPowerMap.get(deviceE).get("5G"));
	}
}
