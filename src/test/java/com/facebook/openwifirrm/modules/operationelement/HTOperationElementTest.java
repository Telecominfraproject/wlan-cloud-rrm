package com.facebook.openwifirrm.modules.operationelement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Test the High Throughput (HT) Operation Element */
public class HTOperationElementTest {
	@Test
	void testGetHtOper() {
		byte primaryChannel = 1;
		byte secondaryChannelOffset = 0;
		boolean staChannelWidth = false;
		boolean rifsMode = false;
		byte htProtection = 0;
		boolean nongreenfieldHtStasPresent = true;
		boolean obssNonHtStasPresent = false;
		byte channelCenterFrequencySegment2 = 0;
		boolean dualBeacon = false;
		boolean dualCtsProtection = false;
		boolean stbcBeacon = false;

		HTOperationElement htOperObj = new HTOperationElement(primaryChannel, secondaryChannelOffset, staChannelWidth,
				rifsMode, htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent,
				channelCenterFrequencySegment2, dualBeacon, dualCtsProtection, stbcBeacon);
		String htOper = htOperObj.getAsBase64String();
		String expectedHtOperString = "AQAgAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		assertEquals(expectedHtOperString, htOper);
		HTOperationElement recreatedHtOper = new HTOperationElement(htOper);
		assertEquals(recreatedHtOper, htOperObj);
		assertEquals(expectedHtOperString, recreatedHtOper.getAsBase64String());

		primaryChannel = 36;
		nongreenfieldHtStasPresent = false;
		htOperObj = new HTOperationElement(primaryChannel, secondaryChannelOffset, staChannelWidth, rifsMode,
				htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent, channelCenterFrequencySegment2,
				dualBeacon, dualCtsProtection, stbcBeacon);
		htOper = htOperObj.getAsBase64String();
		expectedHtOperString = "JAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==";
		assertEquals(expectedHtOperString, htOper);
		recreatedHtOper = new HTOperationElement(htOper);
		assertEquals(recreatedHtOper, htOperObj);
		assertEquals(expectedHtOperString, recreatedHtOper.getAsBase64String());
	}
}
