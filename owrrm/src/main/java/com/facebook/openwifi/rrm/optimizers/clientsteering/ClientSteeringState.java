/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Class to manage global client steering state */
public class ClientSteeringState {

	/** Private default constructor (this is a singleton class) */
	public ClientSteeringState() {};

	/**
	 * Map from AP serial number to client MAC to time (JVM monotonic time in
	 * ns) of the latest attempted client steering action. The {@code Long}
	 * values are never null.
	 */
	private ConcurrentMap<String, Map<String, Long>> apClientLastAttempt =
		new ConcurrentHashMap<>();

	/**
	 * Register a client steering attempt for the given AP and station at the
	 * given time if there is no previous registered attempt or more than the
	 * given backoff time has passed since the registration time of the last
	 * attempt and the current time. Note that only registration times are
	 * checked and/or entered, and nothing is executed here. Return true if the
	 * attempt was registered; false otherwise. The attempt is not registered if
	 * this run is specified as a dry run.
	 * <p>
	 * The backoff time must be non-negative. The backoff time window is
	 * "exclusive" - e.g., if the backoff time is X ns, and the current time is
	 * exactly X ns after the last attempt, the backoff is considered expired.
	 * <p>
	 * Note that if there was a previous attempt for the given AP and station,
	 * the current time must not be before the last attempt.
	 *
	 * @param apSerialNumber AP serial number
	 * @param station client MAC
	 * @param currentTimeNs JVM monotonic time in ns
	 * @param backoffTimeNs non-negative backoff time (ns)
	 * @param dryRun if set, do not apply changes
	 * @return true if client steering attempt was registered; false otherwise
	 */
	public boolean registerIfBackoffExpired(
		String apSerialNumber,
		String station,
		long currentTimeNs,
		long backoffTimeNs,
		boolean dryRun
	) {
		if (backoffTimeNs < 0) {
			throw new IllegalArgumentException(
				"Backoff time must be non-negative."
			);
		}
		// get last attempt
		Map<String, Long> clientLastAttempt = apClientLastAttempt
			.computeIfAbsent(apSerialNumber, k -> new HashMap<>());
		synchronized (clientLastAttempt) {
			Long lastAttempt = clientLastAttempt.get(station);
			// check if backoff expired
			if (
				lastAttempt != null &&
					currentTimeNs - lastAttempt < backoffTimeNs
			) {
				return false;
			}
			// register attempt
			if (!dryRun) {
				clientLastAttempt.put(station, currentTimeNs);
			}
		}
		return true;
	}
}
