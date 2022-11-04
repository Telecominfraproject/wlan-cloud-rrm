/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class ClientSteeringStateTest {

	@Test
	void testRegisterAndCheckBackoff() {
		final String apA = "aaaaaaaaaaaa";
		final String clientA1 = "1a:aa:aa:aa:aa:aa";
		final String clientA2 = "2a:aa:aa:aa:aa:aa";
		final String apB = "bbbbbbbbbbbb";
		final String clientB = "1b:bb:bb:bb:bb:bb";

		final long currentTimeNs = System.nanoTime();
		final long bufferTimeNs = 60_000_000_000L; // 1 min

		ClientSteeringState clientSteeringState =
			ClientSteeringState.getInstance();

		// TODO any better structure that doesn't require remembering to reset
		// every time?
		clientSteeringState.reset();

		// no attempts have been registered
		assertTrue(
			clientSteeringState
				.isBackoffExpired(apA, clientA1, currentTimeNs, bufferTimeNs)
		);

		// when an attempt has been registered for one AP and one client
		final long timestamp1 = currentTimeNs;
		clientSteeringState.registerAttempt(
			apA,
			clientA1,
			timestamp1
		);
		// immediately after registering, clientA1 backoff should be in effect
		assertFalse(
			clientSteeringState
				.isBackoffExpired(apA, clientA1, timestamp1, bufferTimeNs)
		);
		// later, backoff should expire
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apA,
				clientA1,
				timestamp1 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);
		// but clientA2 unaffected
		assertTrue(
			clientSteeringState
				.isBackoffExpired(apA, clientA2, timestamp1, bufferTimeNs)
		);

		// registering one client should not affect another client
		final long timestamp2 = timestamp1 + 1_000_000_000L;
		clientSteeringState.registerAttempt(
			apA,
			clientA2,
			timestamp2
		);
		assertFalse(
			clientSteeringState
				.isBackoffExpired(apA, clientA1, timestamp1, bufferTimeNs)
		);
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apA,
				clientA1,
				timestamp1 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);
		assertFalse(
			clientSteeringState
				.isBackoffExpired(apA, clientA2, timestamp2, bufferTimeNs)
		);
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apA,
				clientA2,
				timestamp2 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);

		// registering one AP should not affect another AP
		final long timestamp3 = timestamp2 + 1_000_000_000L;
		clientSteeringState.registerAttempt(
			apB,
			clientB,
			timestamp3
		);
		assertFalse(
			clientSteeringState
				.isBackoffExpired(apA, clientA2, timestamp2, bufferTimeNs)
		);
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apA,
				clientA2,
				timestamp2 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);
		assertFalse(
			clientSteeringState
				.isBackoffExpired(apB, clientB, timestamp3, bufferTimeNs)
		);
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apB,
				clientB,
				timestamp3 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);

		// registering older timestamp should not matter
		clientSteeringState.registerAttempt(
			apB,
			clientB,
			timestamp2
		);
		// false: should be based on timestamp3 not timestamp2
		assertFalse(
			clientSteeringState.isBackoffExpired(
				apB,
				clientB,
				timestamp2 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);

		// registering new timestamp should update state
		final long timestamp4 = timestamp3 + 1_000_000_000L;
		clientSteeringState.registerAttempt(
			apB,
			clientB,
			timestamp4
		);
		// false: should be based on timestamp4 not timestamp3
		assertFalse(
			clientSteeringState.isBackoffExpired(
				apB,
				clientB,
				timestamp3 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);

		// check a different backoffTimeNs
		assertTrue(
			clientSteeringState.isBackoffExpired(
				apB,
				clientB,
				timestamp4 + bufferTimeNs + 1,
				bufferTimeNs
			)
		);
		assertFalse(
			clientSteeringState.isBackoffExpired(
				apB,
				clientB,
				timestamp4 + bufferTimeNs + 1,
				bufferTimeNs + 1
			)
		);
	}
}
