/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.Constants.CHANNEL_WIDTH;

public class UtilsTest {
	@Test
	void test_jsonMerge() throws Exception {
		JSONObject a, b, c;
		String s;

		// "a", "b" empty
		a = new JSONObject();
		b = new JSONObject();
		s = a.toString();
		Utils.jsonMerge(a, b);
		assertEquals(s, a.toString());

		// "a" non-empty, "b" empty
		a = new JSONObject("{'x': 1}");
		b = new JSONObject();
		s = a.toString();
		Utils.jsonMerge(a, b);
		assertEquals(s, a.toString());

		// "a" empty, "b" non-empty
		a = new JSONObject();
		b = new JSONObject("{'x': 1, 'y': 2}");
		s = b.toString();
		Utils.jsonMerge(a, b);
		assertEquals(s, a.toString());

		// Regular case, non-nested objects
		a = new JSONObject("{'x': 1, 'y': 2}");
		b = new JSONObject("{'y': 4, 'z': 5}");
		c = new JSONObject("{'x': 1, 'y': 4, 'z': 5}");
		Utils.jsonMerge(a, b);
		assertEquals(c.toString(), a.toString());

		// Regular case, nested objects
		a = new JSONObject(
			"{'x': {'x1': 1, 'x2': {'x2a': 2}}, 'y': {'y1': 3}}"
		);
		b = new JSONObject(
			"{'x': {'x1': 4, 'x2': {'x2a': 5}}, 'y': {'y2': 6}}"
		);
		c = new JSONObject(
			"{'x': {'x1': 4, 'x2': {'x2a': 5}}, 'y': {'y1': 3, 'y2': 6}}"
		);
		Utils.jsonMerge(a, b);
		assertEquals(c.toString(), a.toString());
	}

	@Test
	void test_macToLong() throws Exception {
		assertEquals(0x123456789abL, Utils.macToLong("01-23-45-67-89-AB"));
		assertEquals(0xba9876543210L, Utils.macToLong("ba:98:76:54:32:10"));
		assertEquals(0x123456789abcL, Utils.macToLong("1234.5678.9abc"));

		assertThrows(
			IllegalArgumentException.class, () -> Utils.macToLong("blah")
		);
	}

	@Test
	void test_longToMac() throws Exception {
		assertEquals("00:00:00:00:00:00", Utils.longToMac(0L));
		assertEquals("ff:ff:ff:ff:ff:ff", Utils.longToMac(0xffffffffffffL));
		assertEquals("ab:cd:ef:12:34:56", Utils.longToMac(0xabcdef123456L));
	}

	@Test
	void testGetVhtOper() {
		CHANNEL_WIDTH channelWidth = CHANNEL_WIDTH.MHz_20;
		byte channel1 = (byte) 36;
		byte channel2 = (byte) 0;
		byte[] vhtMcsForNss = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 };
		String vht_oper = Utils.get_vht_oper(channelWidth, channel1, channel2, vhtMcsForNss);
		assertEquals("ACQAAAA=", vht_oper);

		channelWidth = CHANNEL_WIDTH.MHz_80;
		channel1 = 58;
		channel2 = 0;
		vhtMcsForNss = new byte[] { 1, 1, 0, 0, 0, 0, 0, 1 };
		vht_oper = Utils.get_vht_oper(channelWidth, channel1, channel2, vhtMcsForNss);
		assertEquals("AToAUAE=", vht_oper);

		channelWidth = CHANNEL_WIDTH.MHz_160;
		channel1 = 42;
		channel2 = 50;
		vht_oper = Utils.get_vht_oper(channelWidth, channel1, channel2, vhtMcsForNss);
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

		String ht_oper = Utils.get_ht_oper(primaryChannel, secondaryChannelOffset, staChannelWidth, rifsMode,
				htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent, channelCenterFrequencySegment2,
				dualBeacon, dualCtsProtection, stbcBeacon);
		assertEquals("AQAgAAAAAAAAAAAAAAAAAAAAAAAAAA==", ht_oper);

		primaryChannel = 36;
		nongreenfieldHtStasPresent = false;
		ht_oper = Utils.get_ht_oper(primaryChannel, secondaryChannelOffset, staChannelWidth, rifsMode,
				htProtection, nongreenfieldHtStasPresent, obssNonHtStasPresent, channelCenterFrequencySegment2,
				dualBeacon, dualCtsProtection, stbcBeacon);
		assertEquals("JAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==", ht_oper);
	}
}
