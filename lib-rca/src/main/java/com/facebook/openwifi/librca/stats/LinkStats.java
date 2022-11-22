/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca.stats;

import java.util.List;

/**
 * Aggregation Statistics Model of InputStats.
 * Aggregate by bssid, station and RadioConfig.
 */
public class LinkStats {
	public static class RadioConfig {
		public int channel;
		public int channelWidth;
		public int txPower;
		public String phy;
	}

	public static class AssociationInfo {
		/** Rate information for receive/transmit data rate. */
		public static class Rate {
			public long bitRate;
			public int chWidth;
			public int mcs;
		}

		public long connected;
		public long inactive;
		public int rssi;
		public long rxBytes;
		public long rxPackets;
		public Rate rxRate;
		public long txBytes;
		public long txDuration;
		public long txFailed;
		public long txPackets;
		public Rate txRate;
		public long txRetries;
		public int ackSignal;
		public int ackSignalAvg;

		// The metrics below are from Interface the client was connected to.
		public long txPacketsCounters;
		public long txErrorsCounters;
		public long txDroppedCounters;

		// The metrics below are from the radio the client was associated to.
		public long activeMsRadio;
		public long busyMsRadio;
		public long noiseRadio;
		public long receiveMsRadio;
		public long transmitMsRadio;

		/** Unix time in milliseconds */
		public long timestamp;
	}

	/** BSSID of the AP radio */
	public String bssid;

	/** Client MAC */
	public String station;

	/** Radio configuration parameters */
	public RadioConfig radioConfig;

	/** Association list */
	public List<AssociationInfo> associationInfoList;
}
