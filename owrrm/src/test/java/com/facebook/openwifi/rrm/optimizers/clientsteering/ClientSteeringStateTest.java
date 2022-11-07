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

		ClientSteeringState clientSteeringState = new ClientSteeringState();

		// first attempt should register
		assertTrue(
			clientSteeringState.registerIfBackoffExpired(
				apA,
				clientA1,
				currentTimeNs,
				bufferTimeNs,
				false // dryRun
			)
		);
		// should not register AP A & clientA1 again while backoff is in effect
		assertFalse(
			clientSteeringState.registerIfBackoffExpired(
				apA,
				clientA1,
				currentTimeNs + 1,
				bufferTimeNs,
				false // dryRun
			)
		);
		// one client's backoff should not affect another client
		assertTrue(
			clientSteeringState.registerIfBackoffExpired(
				apA,
				clientA2,
				currentTimeNs + 1,
				bufferTimeNs,
				false // dryRun
			)
		);
		// one AP should not affect another
		assertTrue(
			clientSteeringState.registerIfBackoffExpired(
				apB,
				clientB,
				currentTimeNs + 1,
				bufferTimeNs,
				false // dryRun
			)
		);
		// should re-register AP A & clientA1 after backoff has expired
		assertTrue(
			clientSteeringState.registerIfBackoffExpired(
				apA,
				clientA1,
				currentTimeNs + bufferTimeNs,
				bufferTimeNs,
				false // dryRun
			)
		);
		// an older timestamp should not register
		assertFalse(
			clientSteeringState.registerIfBackoffExpired(
				apB,
				clientB,
				currentTimeNs - 1,
				bufferTimeNs,
				false // dryRun
			)
		);
		// try a different backoffTimeNs
		assertTrue(
			clientSteeringState
				.registerIfBackoffExpired(
					apA,
					clientA2,
					currentTimeNs + 2,
					1,
					false /* dryRun */
				)
		);
		// same client on a different AP should have separate timer
		assertTrue(
			clientSteeringState.registerIfBackoffExpired(
				apB,
				clientA2,
				currentTimeNs + 3,
				bufferTimeNs,
				false // dryRun
			)
		);
	}
}
