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

	/** Represents client steering actions an AP can take */
	public static enum CLIENT_STEERING_ACTIONS {
		/** Steer from 2G to 5G/6G */
		STEER_UP,
		/** Steer from 5G/6G to 2G */
		STEER_DOWN,
		/** Deauthenticate client */
		DEAUTHENTICATE
	}

	/** 802.11 BTM reason codes (ex. for deauth) */
	public static class BTMReasonCode {
		private BTMReasonCode() {}

		public static final int UNSPECIFIED = 1;
		public static final int PREV_AUTH_NOT_VALID = 2;
		public static final int DEAUTH_LEAVING = 3;
		public static final int DISASSOC_DUE_TO_INACTIVITY = 4;
		public static final int DISASSOC_AP_BUSY = 5;
		public static final int CLASS2_FRAME_FROM_NONAUTH_STA = 6;
		public static final int CLASS3_FRAME_FROM_NONASSOC_STA = 7;
		public static final int DISASSOC_STA_HAS_LEFT = 8;
		public static final int STA_REQ_ASSOC_WITHOUT_AUTH = 9;
		public static final int PWR_CAPABILITY_NOT_VALID = 10;
		public static final int SUPPORTED_CHANNEL_NOT_VALID = 11;
		public static final int INVALID_IE = 13;
		public static final int MICHAEL_MIC_FAILURE = 14;
		public static final int FOURWAY_HANDSHAKE_TIMEOUT = 15;
		public static final int GROUP_KEY_UPDATE_TIMEOUT = 16;
		public static final int IE_IN_4WAY_DIFFERS = 17;
		public static final int GROUP_CIPHER_NOT_VALID = 18;
		public static final int PAIRWISE_CIPHER_NOT_VALID = 19;
		public static final int AKMP_NOT_VALID = 20;
		public static final int UNSUPPORTED_RSN_IE_VERSION = 21;
		public static final int INVALID_RSN_IE_CAPAB = 22;
		public static final int IEEE_802_1X_AUTH_FAILED = 23;
		public static final int CIPHER_SUITE_REJECTED = 24;
		public static final int TDLS_TEARDOWN_UNREACHABLE = 25;
		public static final int TDLS_TEARDOWN_UNSPECIFIED = 26;
		public static final int DISASSOC_LOW_ACK = 34;
		public static final int MESH_PEERING_CANCELLED = 52;
		public static final int MESH_MAX_PEERS = 53;
		public static final int MESH_CONFIG_POLICY_VIOLATION = 54;
		public static final int MESH_CLOSE_RCVD = 55;
		public static final int MESH_MAX_RETRIES = 56;
		public static final int MESH_CONFIRM_TIMEOUT = 57;
		public static final int MESH_INVALID_GTK = 58;
		public static final int MESH_INCONSISTENT_PARAMS = 59;
		public static final int MESH_INVALID_SECURITY_CAP = 60;
	}

	/** The input data model. */
	protected final DataModel model;
	/** The RF zone. */
	protected final String zone;
	/** The device configs within {@link #zone}, keyed on serial number. */
	protected final Map<String, DeviceConfig> deviceConfigs;
	/** Client steering state */
	protected final ClientSteeringState clientSteeringState;

	/** Constructor */
	public ClientSteeringOptimizer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		ClientSteeringState clientSteeringState
	) {
		this.model = model;
		this.zone = zone;
		this.deviceConfigs = deviceDataManager.getAllDeviceConfigs(zone);

		this.clientSteeringState = clientSteeringState;

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
	 * Compute map from AP serial number to client MAC to client steering
	 * action.
	 */
	public abstract Map<String, Map<String, String>> computeApClientActionMap(
		boolean dryRun
	);

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
		//
		// TODO: input must also contain AP interface for each client (needed in hostapd commands below)
		//
		// NOTE: 802.11k/v features must first be enabled on APs:
		//         ubus call hostapd.<iface> bss_mgmt_enable \
		//         '{"neighbor_report": true, "beacon_report": true, "link_measurements": true, "bss_transition": true}'
		//
		// Actions:
		//
		// - Kick/Deauth:
		//     ubus call hostapd.<iface> del_client \
		//     '{"addr": "<client_mac>", "reason": 5, "deauth": true}'
		//   Where "reason" is a code in BTMReasonCode
		//
		// - Steer:
		//     ubus call hostapd.<iface> bss_transition_request \
		//     '{"addr": "<client_mac>", "disassociation_imminent": false, "disassociation_timer": 0, "validity_period": 30, "neighbors": ["<hex>"], "abridged": 1}'
		//   Where "neighbors" list element = a hex identifier (array index 2 in command below) - MUST fetch per interface per AP
		//     ubus call hostapd.<iface> rrm_nr_get_own
		//   TODO: also send Multi Band Operation (MBO) code ("mbo_reason") for 802.11ax clients
	}

	// TODO Issue 802.11k RRM Beacon Measurement Requests periodically
	// 1. Enable 802.11k/v features on the AP ("bss_mgmt_enable" hostapd command)
	// 2. Send request to client
	//      ubus call hostapd.wlan0-1 rrm_beacon_req '{"addr": "<client_mac>", "channel": <number>, "mode": 1, "op_class": 128, "duration": 100}'
	// 3. Must be subscribed to hostapd 'beacon-report' event on AP to receive reply ("BEACON-RESP-RX")
	//      ubus subscribe hostapd.<iface>
}
