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

/** Singleton class to manage global client steering state */
public final class ClientSteeringState {

	/** Singleton instance */
	private static final ClientSteeringState singleton =
		new ClientSteeringState();

	/** Private default constructor (this is a singleton class) */
	private ClientSteeringState() {};

	/** Get the singleton instance */
	public static ClientSteeringState getInstance() {
		return singleton;
	}

	/**
	 * Map from AP serial number to client MAC to time (JVM monotonic time in
	 * ns) of the latest attempted client steering action. The {@code Long}
	 * values are never null.
	 */
	private ConcurrentMap<String, Map<String, Long>> apClientLastAttempt =
		new ConcurrentHashMap<>();

	/** Reset the state - ONLY FOR TESTING */
	public synchronized void reset() {
		// TODO better way then to call this before every test??
		apClientLastAttempt.clear();
	}

	/**
	 * Register a client steering attempt for the given AP and station at the
	 * given time if there has been no previous attempt or more than the given
	 * backoff time has passed since the last attempt and the current time.
	 * Return true if the attempt was registered; false otherwise.
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
	 * @return true if client steering attempt was registered; false otherwise
	 */
	public synchronized boolean registerIfBackoffExpired(
		String apSerialNumber,
		String station,
		long currentTimeNs,
		long backoffTimeNs
	) {
		if (backoffTimeNs < 0) {
			throw new IllegalArgumentException(
				"Backoff time must be non-negative."
			);
		}
		// get last attempt
		Map<String, Long> clientLastAttempt = apClientLastAttempt
			.computeIfAbsent(apSerialNumber, k -> new HashMap<>());
		Long lastAttempt = clientLastAttempt.get(station);
		// check if backoff expired
		if (
			lastAttempt != null && currentTimeNs - lastAttempt < backoffTimeNs
		) {
			return false;
		}
		// register attempt
		clientLastAttempt.put(station, currentTimeNs);
		return true;
	}
}
