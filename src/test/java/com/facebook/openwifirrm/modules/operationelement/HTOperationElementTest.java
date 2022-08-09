package com.facebook.openwifirrm.modules.operationelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Test the High Throughput (HT) Operation Element */
public class HTOperationElementTest {
	@Test
	void testGetHtOper() {
		String htOper = "AQAgAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		HTOperationElement htOperObj = new HTOperationElement(htOper);
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
		HTOperationElement expectedHtOperObj = new HTOperationElement(expectedPrimaryChannel,
				expectedSecondaryChannelOffset,
				expectedStaChannelWidth, expectedRifsMode, expectedHtProtection, expectedNongreenfieldHtStasPresent,
				expectedObssNonHtStasPresent, expectedChannelCenterFrequencySegment2, expectedDualBeacon,
				expectedDualCtsProtection, expectedStbcBeacon);
		assertEquals(expectedHtOperObj, htOperObj);

		htOper = "JAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		htOperObj = new HTOperationElement(htOper);
		// all fields except the primary channel and nongreenfield field are the same
		expectedPrimaryChannel = 36;
		expectedNongreenfieldHtStasPresent = false;
		expectedHtOperObj = new HTOperationElement(expectedPrimaryChannel, expectedSecondaryChannelOffset,
				expectedStaChannelWidth, expectedRifsMode, expectedHtProtection, expectedNongreenfieldHtStasPresent,
				expectedObssNonHtStasPresent, expectedChannelCenterFrequencySegment2, expectedDualBeacon,
				expectedDualCtsProtection, expectedStbcBeacon);
		assertEquals(expectedHtOperObj, htOperObj);
	}
}
