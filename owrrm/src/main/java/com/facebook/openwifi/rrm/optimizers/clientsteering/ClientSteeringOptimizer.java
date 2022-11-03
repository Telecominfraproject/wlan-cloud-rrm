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

import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.modules.Modeler.DataModel;

/** Client steering base class */
public abstract class ClientSteeringOptimizer {
	// TODO call upon triggers, not only via one-off or period runs

	/** Represents client steering actions an AP Can take */
	public static enum CLIENT_STEERING_ACTIONS {
		/** Steer from 2G to 5G/6G */
		STEER_UP,
		/** Steer from 5G/6G to 2G */
		STEER_DOWN,
		/** Deauthenticate client */
		DEAUTHENTICATE
	}

	/**
	 * Map from AP serial number to radio bssid to time (unix time in ms) of the
	 * latest attempted client steering action. It is static here to track
	 * attempts across all instances of all subclasses. The {@code Long} values
	 * are never null.
	 */
	public static final Map<String, Map<String, Long>> apRadioLastAttempt =
		new TreeMap<>();

	/**
	 * Register the time of the latest client steering attempt by the given AP
	 * and radio.
	 *
	 * @param apSerialNumber non-null AP serial number
	 * @param bssid non-null radio bssid
	 * @param currentTimeNs current Java VM monotonic time (ns)
	 */
	protected static final void registerClientSteeringAttempt(
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
	protected static final Long getLatestClientSteeringAttempt(
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
	 * @param currentTimeNs current Java VM monotonic time (ns)
	 * @param backoffTime backoff time (ms)
	 * @return true if enough more than the backoff time has passed
	 */
	protected static final boolean checkBackoff(
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

	/** The input data model. */
	protected final DataModel model;

	/** The RF zone. */
	protected final String zone;

	/** The device configs within {@link #zone}, keyed on serial number. */
	protected final Map<String, DeviceConfig> deviceConfigs;

	/** Constructor */
	public ClientSteeringOptimizer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this.model = model;
		this.zone = zone;
		this.deviceConfigs = deviceDataManager.getAllDeviceConfigs(zone);

		// Remove model entries not in the given zone
		this.model.latestWifiScans.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestStates.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestDeviceStatusRadios.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
		this.model.latestDeviceCapabilitiesPhy.keySet()
			.removeIf(serialNumber -> !deviceConfigs.containsKey(serialNumber));
	}

	/**
	 * Computed map from AP serial number to client MAC to client steering
	 * action.
	 */
	public abstract Map<String, Map<String, String>> computeApClientActionMap();

	/**
	 * Steer clients (steer up, steer down, and deauthenticate).
	 *
	 * @param apClientActionMap the map from AP serial number to client MAC to
	 *                          action to take
	 */
	public void steer(
		Map<String, Map<String, String>> apClientActionMap
	) {
		// FIXME implement this
	}
}
