/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import java.util.Map;
import java.util.TreeMap;
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
	 * Map from AP serial number to radio bssid to time (JVM monotonic time in
	 * ms) of the latest attempted client steering action. The {@code Long}
	 * values are never null.
	 */
	private final ConcurrentMap<String, Map<String, Long>> apRadioLastAttempt =
		new ConcurrentHashMap<>();

	/** Reset the state (e.g., for testing) */
	public final void reset() {
		apRadioLastAttempt.clear();
	}

	/**
	 * Register the time of the latest client steering attempt by the given AP
	 * and radio.
	 *
	 * @param apSerialNumber non-null AP serial number
	 * @param bssid non-null radio bssid
	 * @param currentTimeNs current JVM monotonic time (ns)
	 */
	final void registerClientSteeringAttempt(
		String apSerialNumber,
		String bssid,
		long currentTimeNs
	) {
		Map<String, Long> radioLastAttempt = apRadioLastAttempt
			.computeIfAbsent(apSerialNumber, k -> new TreeMap<>());
		Long lastAttempt = radioLastAttempt.get(bssid);
		if (lastAttempt == null || currentTimeNs > lastAttempt) {
			radioLastAttempt.put(bssid, currentTimeNs);
		}
	}

	/**
	 * Get the time of the latest client steering attempt by AP serial number
	 * and radio bssid. Return null if no client steering attempt has been made
	 * for the given AP and radio.
	 *
	 * @param apSerialNumber non-null AP serial number
	 * @param bssid non-null radio bssid
	 */
	final Long getLatestClientSteeringAttempt(
		String apSerialNumber,
		String bssid
	) {
		Map<String, Long> clientLatestAttempt = apRadioLastAttempt
			.get(apSerialNumber);
		if (clientLatestAttempt == null) {
			return null;
		}
		return clientLatestAttempt.get(bssid);
	}

	/**
	 * Check if enough time (more than the backoff time) has passed since the
	 * latest client steering attempt for the given AP and radio.
	 *
	 * @param apSerialNumber AP serial number
	 * @param bssid radio bssid
	 * @param currentTimeNs current JVM monotonic time (ns)
	 * @param backoffTime backoff time (ms)
	 * @return true if enough more than the backoff time has passed
	 */
	final boolean checkBackoff(
		String apSerialNumber,
		String bssid,
		long currentTimeNs,
		long backoffTime
	) {
		// TODO use per-AP-and-radio backoff, doubling each time up to a max
		// instead of a passed in backoff time
		Long latestClientSteeringAttempt =
			getLatestClientSteeringAttempt(apSerialNumber, bssid);
		if (latestClientSteeringAttempt == null) {
			return true;
		}
		return currentTimeNs - latestClientSteeringAttempt > backoffTime;
	}

}
