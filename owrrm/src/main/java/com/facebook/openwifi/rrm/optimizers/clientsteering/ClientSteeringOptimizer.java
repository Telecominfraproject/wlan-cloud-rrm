/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.clientsteering;

import java.util.Map;

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

	/** Get global client steering state */
	protected final ClientSteeringState getClientSteeringState() {
		return ClientSteeringState.getInstance();
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
