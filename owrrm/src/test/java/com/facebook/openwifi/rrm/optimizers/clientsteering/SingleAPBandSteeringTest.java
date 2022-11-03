/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.facebook.openwifi.cloudsdk.UCentralConstants;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.modules.Modeler;
import com.facebook.openwifi.rrm.optimizers.TestUtils;
import com.facebook.openwifi.rrm.optimizers.clientsteering.ClientSteeringOptimizer.CLIENT_STEERING_ACTIONS;

public class SingleAPBandSteeringTest {
	// TODO test 6G also (should be treated same as 5G)

	/** Test zone name. */
	private static final String TEST_ZONE = "test-zone";

	// AP serial numbers
	private static final String apA = "aaaaaaaaaaaa";
	private static final String apB = "bbbbbbbbbbbb";
	private static final String apC = "cccccccccccc";

	// arrays are mutable, but these are private and this is just a test class
	// arrays are more convenient for constructing states

	/** bssids for radios on AP A */
	private static final String[] bssidsA =
		new String[] { "aa:aa:aa:aa:aa:a1", "aa:aa:aa:aa:aa:a2" };
	/** bssids for radios on AP B */
	private static final String[] bssidsB =
		new String[] { "bb:bb:bb:bb:bb:b1", "bb:bb:bb:bb:bb:b2" };
	/** bssids for radios on AP C */
	private static final String[] bssidsC =
		new String[] { "cc:cc:cc:cc:cc:c1", "cc:cc:cc:cc:cc:c2" };

	/** Array: each element is an array of client MACS for a radio on AP A */
	private static final String[][] clientsA =
		new String[][] { new String[] { "1a:aa:aa:aa:aa:aa" },
			new String[] { "2a:aa:aa:aa:aa:aa" } };
	/** Array: each element is an array of client MACS for a radio on AP B */
	private static final String[][] clientsB =
		new String[][] { new String[] { "1b:bb:bb:bb:bb:bb" },
			new String[] { "2b:bb:bb:bb:bb:bb" } };
	/** Array: each element is an array of client MACS for a radio on AP B */
	private static final String[][] clientsC =
		new String[][] { new String[] { "1c:cc:cc:cc:cc:cc" },
			new String[] { "2c:cc:cc:cc:cc:cc" } };

	//	// useful for iterating over the APs
	//	private static final List<String> aps = Collections.unmodifiableList(Arrays.asList(apA, apB, apC));
	//	private static final List<String[]> bssids = Collections.unmodifiableList(Arrays.asList(bssidsA, bssidsB, bssidsC));
	//	private static final List<String[][]> clients = Collections.unmodifiableList(Arrays.asList(clientsA, clientsB, clientsC));

	/** Default channel width */
	private static final int DEFAULT_CHANNEL_WIDTH = 20;

	/** Default tx power */
	private static final int DEFAULT_TX_POWER = 20;

	/** Adds matching State and DeviceCapabilityPhy objects to the data model */
	private void addStateAndCapability(
		Modeler.DataModel dataModel,
		String apSerialNumber,
		String[] bssids,
		String[][] clients,
		int[][] clientRssis
	) {
		dataModel.latestStates.put(
			apSerialNumber,
			Arrays.asList(
				TestUtils.createState(
					new int[] { 1, 36 },
					new int[] { DEFAULT_CHANNEL_WIDTH, DEFAULT_CHANNEL_WIDTH },
					new int[] { DEFAULT_TX_POWER, DEFAULT_TX_POWER },
					bssids,
					clients,
					clientRssis,
					TestUtils.DEFAULT_LOCAL_TIME
				)
			)
		);
		dataModel.latestDeviceCapabilitiesPhy.put(
			apSerialNumber,
			TestUtils.createDeviceCapabilityPhy(
				new String[] {
					UCentralConstants.BAND_2G,
					UCentralConstants.BAND_5G }
			)
		);
	}

	/**
	 * Creates a data model such that:
	 * Client on (AP A, radio 2G) -> should be deauthenticated
	 * Client on (AP A, radio 5G) -> should be steered down
	 * Client on (AP B, radio 2G) -> no action
	 * Client on (AP B, radio 5G) -> no action
	 * Client on (AP C, radio 2G) -> should be steered up
	 * Client on (AP C, radio 5G) -> no action
	 *
	 * @return the data model
	 */
	private Modeler.DataModel createModel() {
		Modeler.DataModel dataModel = new Modeler.DataModel();
		// AP A
		int[] clientRssis2G =
			new int[] { SingleAPBandSteering.DEFAULT_MIN_RSSI_2G - 1 }; // deauthenticate
		int[] clientRssis5G =
			new int[] { SingleAPBandSteering.DEFAULT_MIN_RSSI_NON_2G - 1 }; // steer down
		int[][] clientRssis = new int[][] { clientRssis2G, clientRssis5G };
		addStateAndCapability(
			dataModel,
			apA,
			bssidsA,
			clientsA,
			clientRssis
		);

		// AP B
		clientRssis2G = new int[] { SingleAPBandSteering.DEFAULT_MIN_RSSI_2G }; // do nothing
		clientRssis5G =
			new int[] { SingleAPBandSteering.DEFAULT_MIN_RSSI_NON_2G }; // do nothing
		clientRssis = new int[][] { clientRssis2G, clientRssis5G };
		addStateAndCapability(
			dataModel,
			apB,
			bssidsB,
			clientsB,
			clientRssis
		);

		// AP C
		clientRssis2G =
			new int[] { SingleAPBandSteering.DEFAULT_MAX_RSSI_2G + 1 }; // steer up
		clientRssis5G =
			new int[] { SingleAPBandSteering.DEFAULT_MIN_RSSI_NON_2G }; // do nothing
		clientRssis = new int[][] { clientRssis2G, clientRssis5G };
		addStateAndCapability(
			dataModel,
			apC,
			bssidsC,
			clientsC,
			clientRssis
		);

		return dataModel;
	}

	@Test
	void testComputeApClientActionMap() {
		DeviceDataManager deviceDataManager = new DeviceDataManager();
		deviceDataManager
			.setTopology(TestUtils.createTopology(TEST_ZONE, apA, apB, apC));
		Modeler.DataModel dataModel = createModel();
		// create expected results
		// see javadoc of createModel for more details
		Map<String, Map<String, String>> exp = new HashMap<>();
		Map<String, String> apAMap = new HashMap<>();
		apAMap
			.put(clientsA[0][0], CLIENT_STEERING_ACTIONS.DEAUTHENTICATE.name());
		apAMap.put(clientsA[1][0], CLIENT_STEERING_ACTIONS.STEER_DOWN.name());
		exp.put(apA, apAMap);
		// no action for AP B
		Map<String, String> apCMap = new HashMap<>();
		apCMap.put(clientsC[0][0], CLIENT_STEERING_ACTIONS.STEER_UP.name());
		exp.put(apC, apCMap);
		SingleAPBandSteering optimizer = SingleAPBandSteering.makeWithArgs(
			dataModel,
			TEST_ZONE,
			deviceDataManager,
			new HashMap<>(0) // args (use default)
		);
		Map<String, Map<String, String>> apClientActionMap =
			optimizer.computeApClientActionMap();
		assertEquals(exp, apClientActionMap);
	}
}
