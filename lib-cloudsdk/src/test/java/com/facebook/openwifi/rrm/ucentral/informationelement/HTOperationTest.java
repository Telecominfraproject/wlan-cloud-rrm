/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral.informationelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class HTOperationTest {
	@Test
	void testGetHtOper() {
		String htOper = "AQAEAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		HTOperation htOperObj = new HTOperation(htOper);
		byte expectedPrimaryChannel = 1;
		byte expectedSecondaryChannelOffset = 0;
		boolean expectedStaChannelWidth = false;
		boolean expectedRifsMode = false;
		byte expectedHtProtection = 0;
		boolean expectedNongreenfieldHtStasPresent = true;
		boolean expectedObssNonHtStasPresent = false;
		byte expectedChannelCenterFrequencySegment2 = 0;
		boolean expectedDualBeacon = false;
		boolean expectedDualCtsProtection = false;
		boolean expectedStbcBeacon = false;
		HTOperation expectedHtOperObj = new HTOperation(
			expectedPrimaryChannel,
			expectedSecondaryChannelOffset,
			expectedStaChannelWidth,
			expectedRifsMode,
			expectedHtProtection,
			expectedNongreenfieldHtStasPresent,
			expectedObssNonHtStasPresent,
			expectedChannelCenterFrequencySegment2,
			expectedDualBeacon,
			expectedDualCtsProtection,
			expectedStbcBeacon
		);
		assertEquals(expectedHtOperObj, htOperObj);

		htOper = "JAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		htOperObj = new HTOperation(htOper);
		// all fields except the primary channel and nongreenfield field are the same
		expectedPrimaryChannel = 36;
		expectedNongreenfieldHtStasPresent = false;
		expectedHtOperObj = new HTOperation(
			expectedPrimaryChannel,
			expectedSecondaryChannelOffset,
			expectedStaChannelWidth,
			expectedRifsMode,
			expectedHtProtection,
			expectedNongreenfieldHtStasPresent,
			expectedObssNonHtStasPresent,
			expectedChannelCenterFrequencySegment2,
			expectedDualBeacon,
			expectedDualCtsProtection,
			expectedStbcBeacon
		);
		assertEquals(expectedHtOperObj, htOperObj);
	}
}
