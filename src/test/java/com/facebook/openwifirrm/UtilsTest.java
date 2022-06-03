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
}
