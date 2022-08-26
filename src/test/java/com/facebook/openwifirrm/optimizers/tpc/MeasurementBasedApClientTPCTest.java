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
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.optimizers.TestUtils;
import com.facebook.openwifirrm.ucentral.UCentralConstants;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.JsonObject;

@TestMethodOrder(OrderAnnotation.class)
public class MeasurementBasedApClientTPCTest {
	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	/** Create a device state object containing the given parameters. */
	private State createState(
		String serialNumber, int curTxPower, int bandwidth, int[] channels, int... clientRssi
	) {
		State state = new State();
		state.radios = new JsonObject[channels.length];
		int curIndex = 0;
		for (int channel : channels) {
			JsonObject radio = new JsonObject();
			radio.addProperty("channel", channel);
			radio.addProperty(
				"channel_width", Integer.toString(bandwidth)
			);
			radio.addProperty("tx_power", curTxPower);
			state.radios[curIndex] = radio;
			curIndex += 1;
		}
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
	void testComputeCorrectTxPower() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";
		final String deviceE = "eeeeeeeeeeee";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE, deviceA, deviceB, deviceC, deviceD, deviceE
			)
		);

		DataModel dataModel = new DataModel();
		dataModel.latestState.put(
			deviceA,
			createState(deviceA, 20 /*txPower*/, 20 /*bandwidth*/, new int[] {36} /*channel*/)
		);
		dataModel.latestState.put(
			deviceB,
			createState(deviceB, 20 /*txPower*/, 20 /*bandwidth*/, new int[] {36}, -65)
		);
		dataModel.latestState.put(
			deviceC,
			createState(deviceC, 21 /*txPower*/, 40 /*bandwidth*/, new int[] {36}, -65, -73, -58)
		);
		dataModel.latestState.put(
			deviceD,
			createState(deviceD, 22 /*txPower*/, 20 /*bandwidth*/, new int[] {36},-80)
		);
		dataModel.latestState.put(
			deviceE,
			createState(deviceE, 23 /*txPower*/, 20 /*bandwidth*/, new int[] {36}, -45)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(dataModel, TEST_ZONE, deviceDataManager);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		// Device A: no clients
		assertEquals(10, txPowerMap.get(deviceA).get(UCentralConstants.BAND_5G));

		// Device B: 1 client with RSSI -65
		assertEquals(14, txPowerMap.get(deviceB).get(UCentralConstants.BAND_5G));

		// Device C: 3 clients with min. RSSI -73
		assertEquals(26, txPowerMap.get(deviceC).get(UCentralConstants.BAND_5G));

		// Device D: 1 client with RSSI -80 => set to max txPower for MCS 7
		assertEquals(28, txPowerMap.get(deviceD).get(UCentralConstants.BAND_5G));

		// Device E: 1 client with RSSI -45 => set to min txPower
		assertEquals(TPC.MIN_TX_POWER, txPowerMap.get(deviceE).get(UCentralConstants.BAND_5G));
	}

	@Test
	@Order(2)
	void testVariousBands() throws Exception {
		final String deviceA = "aaaaaaaaaaaa";
		final String deviceB = "bbbbbbbbbbbb";
		final String deviceC = "cccccccccccc";
		final String deviceD = "dddddddddddd";

		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager.setTopology(
			TestUtils.createTopology(
				TEST_ZONE, deviceA, deviceB, deviceC, deviceD
			)
		);

		DataModel dataModel = new DataModel();
		// 2G only
		dataModel.latestState.put(
			deviceA,
			createState(deviceA, 20 /*txPower*/, 20 /*bandwidth*/, new int[]{2} /*channel*/)
		);
		// 5G only
		dataModel.latestState.put(
			deviceB,
			createState(deviceB, 20 /*txPower*/, 20 /*bandwidth*/, new int[]{36} /*channel*/)
		);
		// 2G and 5G
		dataModel.latestState.put(
			deviceC,
			createState(deviceC, 20 /*txPower*/, 20 /*bandwidth*/, new int[]{2, 36} /*channel*/)
		);
		// No valid bands in 2G or 5G
		dataModel.latestState.put(
			deviceD,
			createState(deviceA, 20 /*txPower*/, 20 /*bandwidth*/, new int[]{25} /*channel*/)
		);

		TPC optimizer = new MeasurementBasedApClientTPC(dataModel, TEST_ZONE, deviceDataManager);
		Map<String, Map<String, Integer>> txPowerMap =
			optimizer.computeTxPowerMap();

		Set<String> expectedBands = new TreeSet<>();
		expectedBands.add(UCentralConstants.BAND_2G);
		assertEquals(expectedBands, txPowerMap.get(deviceA).keySet());
		expectedBands.add(UCentralConstants.BAND_5G);
		assertEquals(expectedBands, txPowerMap.get(deviceC).keySet());
		expectedBands.remove(UCentralConstants.BAND_2G);
		assertEquals(expectedBands, txPowerMap.get(deviceB).keySet());
		expectedBands.remove(UCentralConstants.BAND_5G);
		assertEquals(expectedBands, txPowerMap.get(deviceD).keySet());
	}
}
