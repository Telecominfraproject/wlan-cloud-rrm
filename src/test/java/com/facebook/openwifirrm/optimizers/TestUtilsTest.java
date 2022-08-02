package com.facebook.openwifirrm.optimizers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.Constants.CHANNEL_WIDTH;

public class TestUtilsTest {
	@Test
	void testGetVhtOper() {
		CHANNEL_WIDTH channelWidth = CHANNEL_WIDTH.MHz_20;
		byte channel1 = (byte) 36;
		byte channel2 = (byte) 0;
		byte[] vhtMcsForNss = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		String vht_oper = TestUtils.getVhtOper(channelWidth, channel1, channel2, vhtMcsForNss);
		assertEquals("ACQAAAA=", vht_oper);

		channelWidth = CHANNEL_WIDTH.MHz_80;
		channel1 = 58;
		channel2 = 0;
		vhtMcsForNss = new byte[] { 1, 1, 0, 0, 0, 0, 0, 1 };
		vht_oper = TestUtils.getVhtOper(channelWidth, channel1, channel2, vhtMcsForNss);
		assertEquals("AToAUAE=", vht_oper);

		channelWidth = CHANNEL_WIDTH.MHz_160;
		channel1 = 42;
		channel2 = 50;
		vht_oper = TestUtils.getVhtOper(channelWidth, channel1, channel2, vhtMcsForNss);
		assertEquals("ASoyUAE=", vht_oper);
	}

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

		String ht_oper = TestUtils.getHtOper(primaryChannel, secondaryChannelOffset, staChannelWidth, rifsMode,
				htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent, channelCenterFrequencySegment2,
				dualBeacon, dualCtsProtection, stbcBeacon);
		assertEquals("AQAgAAAAAAAAAAAAAAAAAAAAAAAAAA==", ht_oper);

		primaryChannel = 36;
		nongreenfieldHtStasPresent = false;
		ht_oper = TestUtils.getHtOper(primaryChannel, secondaryChannelOffset, staChannelWidth, rifsMode, htProtection,
				nongreenfieldHtStasPresent, obssNonHtStasPresent, channelCenterFrequencySegment2, dualBeacon,
				dualCtsProtection, stbcBeacon);
		assertEquals("JAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", ht_oper);
	}
}
