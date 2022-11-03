/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.facebook.openwifi.rrm.optimizers.TestUtils;

public class ClientSteeringOptimizerTest {

	@Test
	void testApRadioLastAttempt() {
		final String apA = "aaaaaaaaaaaa";
		final String bssidA1 = "aa:aa:aa:aa:aa:a1";
		final String bssidA2 = "aa:aa:aa:aa:aa:a2";
		final String apB = "bbbbbbbbbbbb";
		final String bssidB = "bb:bb:bb:bb:bb:bb";

		ClientSteeringOptimizer.apRadioLastAttempt.clear();

		// no attempts have been registered
		assertEquals(
			null,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);

		// when an attempt has been registered for one AP and one bssid
		final long timestamp1 = TestUtils.DEFAULT_LOCAL_TIME;
		ClientSteeringOptimizer
			.registerClientSteeringAttempt(apA, bssidA1, timestamp1);
		assertEquals(
			timestamp1,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);
		assertEquals(
			null,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA2)
		);

		// registering one radio should not affect another radio's timestamp
		final long timestamp2 = timestamp1 + 1;
		ClientSteeringOptimizer
			.registerClientSteeringAttempt(apA, bssidA2, timestamp2);
		assertEquals(
			timestamp1,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);
		assertEquals(
			timestamp2,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA2)
		);

		// registering one AP should not affect another AP's timestamp
		final long timestamp3 = timestamp2 + 1;
		ClientSteeringOptimizer
			.registerClientSteeringAttempt(apB, bssidB, timestamp3);
		assertEquals(
			timestamp1,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);
		assertEquals(
			timestamp2,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA2)
		);
		assertEquals(
			timestamp3,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apB, bssidB)
		);

		// registering older timestamp should not affect state
		ClientSteeringOptimizer
			.registerClientSteeringAttempt(apB, bssidB, timestamp2);
		assertEquals(
			timestamp1,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);
		assertEquals(
			timestamp2,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA2)
		);
		assertEquals(
			timestamp3,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apB, bssidB)
		);

		// registering new timestamp should update state
		final long timestamp4 = timestamp3 + 1;
		ClientSteeringOptimizer
			.registerClientSteeringAttempt(apB, bssidB, timestamp4);
		assertEquals(
			timestamp1,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA1)
		);
		assertEquals(
			timestamp2,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apA, bssidA2)
		);
		assertEquals(
			timestamp4,
			ClientSteeringOptimizer.getLatestClientSteeringAttempt(apB, bssidB)
		);
	}
}
