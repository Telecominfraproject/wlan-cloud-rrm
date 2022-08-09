/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules.operationelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class VHTOperationElementTest {

	@Test
	void testGetVhtOper() {
		String vhtOper = "ACQAAAA=";
		VHTOperationElement vhtOperObj = new VHTOperationElement(vhtOper);
		boolean expectedChannelWidthIndicator = false; // 20 MHz channel width
		byte expectedChannel1 = 36;
		byte expectedChannel2 = 0;
		byte[] expectedVhtMcsForNss = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		VHTOperationElement expectedVhtOperObj = new VHTOperationElement(expectedChannelWidthIndicator, expectedChannel1, expectedChannel2, expectedVhtMcsForNss);
		assertEquals(expectedVhtOperObj, vhtOperObj);

		vhtOper = "AToAUAE=";
		vhtOperObj = new VHTOperationElement(vhtOper);
		expectedChannelWidthIndicator = true; // 80 MHz channel width
		expectedChannel1 = 58;
		// same channel2
		expectedVhtMcsForNss = new byte[] { 1, 1, 0, 0, 0, 0, 0, 1 };
		expectedVhtOperObj = new VHTOperationElement(expectedChannelWidthIndicator, expectedChannel1, expectedChannel2,
				expectedVhtMcsForNss);
		assertEquals(expectedVhtOperObj, vhtOperObj);

		vhtOper = "ASoyUAE=";
		vhtOperObj = new VHTOperationElement(vhtOper);
		// same channel width indicator (160 MHz channel width)
		expectedChannel1 = 42;
		expectedChannel2 = 50;
		// same vhtMcsForNss
		expectedVhtOperObj = new VHTOperationElement(expectedChannelWidthIndicator, expectedChannel1, expectedChannel2,
				expectedVhtMcsForNss);
		assertEquals(expectedVhtOperObj, vhtOperObj);
	}
}
