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
	private final ConcurrentMap<String, Map<String, Long>> apClientLastAttempt =
		new ConcurrentHashMap<>();

	/** Reset the state (e.g., for testing) */
	public final void reset() {
		apClientLastAttempt.clear();
	}

	/**
	 * Register the time of the latest client steering attempt by the given AP
	 * for the given client.
	 *
	 * @param apSerialNumber non-null AP serial number
	 * @param station non-null client MAC
	 * @param currentTimeNs current JVM monotonic time (ns)
	 */
	final void registerClientSteeringAttempt(
		String apSerialNumber,
		String station,
		long currentTimeNs
	) {
		Map<String, Long> radioLastAttempt = apClientLastAttempt
			.computeIfAbsent(apSerialNumber, k -> new HashMap<>());
		Long lastAttempt = radioLastAttempt.get(station);
		if (lastAttempt == null || currentTimeNs > lastAttempt) {
			radioLastAttempt.put(station, currentTimeNs);
		}
	}

	/**
	 * Get the time of the latest client steering attempt by AP serial number
	 * and client MAC. Return null if no client steering attempt has been made
	 * for the given AP and radio.
	 *
	 * @param apSerialNumber non-null AP serial number
	 * @param station non-null client MAC
	 */
	final Long getLatestClientSteeringAttempt(
		String apSerialNumber,
		String station
	) {
		Map<String, Long> clientLatestAttempt = apClientLastAttempt
			.get(apSerialNumber);
		if (clientLatestAttempt == null) {
			return null;
		}
		return clientLatestAttempt.get(station);
	}

	/**
	 * Check if enough time (more than the backoff time) has passed since the
	 * latest client steering attempt for the given AP and radio.
	 *
	 * @param apSerialNumber AP serial number
	 * @param station client MAC
	 * @param currentTimeNs current JVM monotonic time (ns)
	 * @param backoffTimeNs backoff time (ns)
	 * @return true if enough more than the backoff time has passed
	 */
	final boolean checkBackoff(
		String apSerialNumber,
		String station,
		long currentTimeNs,
		long backoffTimeNs
	) {
		// TODO use per-AP-and-radio backoff, doubling each time up to a max
		// instead of a passed in backoff time
		Long latestClientSteeringAttempt =
			getLatestClientSteeringAttempt(apSerialNumber, station);
		if (latestClientSteeringAttempt == null) {
			return true;
		}
		return currentTimeNs - latestClientSteeringAttempt > backoffTimeNs;
	}

}
