/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.rca;

import java.util.Map;

/**
 * Root cause analysis service configuration model.
 */
public class RCAConfig {
	//
	// NOTE:
	// Currently assumes RCA is embedded in the RRM service and does NOT
	// duplicate SDK-related fields.
	//

	/**
	 * StationPinger parameters.
	 */
	public class StationPingerParams {
		/**
		 * How often to ping each station, in seconds (or 0 to disable)
		 * ({@code STATIONPINGERPARAMS_PINGINTERVALSEC})
		 */
		// NOTE: cannot be shorter than Kafka "state" publish interval
		public int pingIntervalSec = 0 /* TODO enable by default */;

		/**
		 * The number of pings to send to each station
		 * ({@code STATIONPINGERPARAMS_PINGCOUNT})
		 */
		public int pingCount = 5;

		/**
		 * Number of executor threads for ping tasks
		 * ({@code STATIONPINGERPARAMS_EXECUTORTHREADCOUNT})
		 */
		public int executorThreadCount = 3;
	}

	/** StationPinger parameters. */
	public StationPingerParams stationPingerParams = new StationPingerParams();

	/** Construct RCAConfig from environment variables. */
	public static RCAConfig fromEnv(Map<String, String> env) {
		RCAConfig config = new RCAConfig();
		String v;

		// @formatter:off

		/* StationPingerParams */
		StationPingerParams stationPingerParams = config.stationPingerParams;
		if ((v = env.get("STATIONPINGERPARAMS_PINGINTERVALSEC")) != null) {
			stationPingerParams.pingIntervalSec = Integer.parseInt(v);
		}
		if ((v = env.get("STATIONPINGERPARAMS_PINGCOUNT")) != null) {
			stationPingerParams.pingCount = Integer.parseInt(v);
		}
		if ((v = env.get("STATIONPINGERPARAMS_EXECUTORTHREADCOUNT")) != null) {
			stationPingerParams.executorThreadCount = Integer.parseInt(v);
		}

		// @formatter:on

		return config;
	}
}
