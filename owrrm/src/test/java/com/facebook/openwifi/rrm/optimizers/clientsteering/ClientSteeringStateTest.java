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

public class ClientSteeringStateTest {

	@Test
	void testApRadioLastAttempt() {
		final String apA = "aaaaaaaaaaaa";
		final String clientA1 = "1a:aa:aa:aa:aa:aa";
		final String clientA2 = "2a:aa:aa:aa:aa:aa";
		final String apB = "bbbbbbbbbbbb";
		final String clientB = "1b:bb:bb:bb:bb:bb";

		ClientSteeringState clientSteeringState =
			ClientSteeringState.getInstance();

		// TODO any better structure that doesn't require remembering to reset
		// every time?
		clientSteeringState.reset();

		// no attempts have been registered
		assertEquals(
			null,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);

		// when an attempt has been registered for one AP and one bssid
		final long timestamp1 = TestUtils.DEFAULT_LOCAL_TIME;
		clientSteeringState.registerClientSteeringAttempt(
			apA,
			clientA1,
			timestamp1
		);
		assertEquals(
			timestamp1,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);
		assertEquals(
			null,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA2)
		);

		// registering one radio should not affect another radio's timestamp
		final long timestamp2 = timestamp1 + 1;
		clientSteeringState.registerClientSteeringAttempt(
			apA,
			clientA2,
			timestamp2
		);
		assertEquals(
			timestamp1,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);
		assertEquals(
			timestamp2,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA2)
		);

		// registering one AP should not affect another AP's timestamp
		final long timestamp3 = timestamp2 + 1;
		clientSteeringState.registerClientSteeringAttempt(
			apB,
			clientB,
			timestamp3
		);
		assertEquals(
			timestamp1,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);
		assertEquals(
			timestamp2,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA2)
		);
		assertEquals(
			timestamp3,
			clientSteeringState.getLatestClientSteeringAttempt(apB, clientB)
		);

		// registering older timestamp should not affect state
		clientSteeringState.registerClientSteeringAttempt(
			apB,
			clientB,
			timestamp2
		);
		assertEquals(
			timestamp1,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);
		assertEquals(
			timestamp2,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA2)
		);
		assertEquals(
			timestamp3,
			clientSteeringState.getLatestClientSteeringAttempt(apB, clientB)
		);

		// registering new timestamp should update state
		final long timestamp4 = timestamp3 + 1;
		clientSteeringState.registerClientSteeringAttempt(
			apB,
			clientB,
			timestamp4
		);
		assertEquals(
			timestamp1,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA1)
		);
		assertEquals(
			timestamp2,
			clientSteeringState.getLatestClientSteeringAttempt(apA, clientA2)
		);
		assertEquals(
			timestamp4,
			clientSteeringState.getLatestClientSteeringAttempt(apB, clientB)
		);
	}
}
