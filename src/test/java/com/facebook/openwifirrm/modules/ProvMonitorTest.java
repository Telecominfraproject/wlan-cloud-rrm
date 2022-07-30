/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMConfig;
import com.facebook.openwifirrm.ucentral.UCentralClient;

public class ProvMonitorTest {
	/** Test device data manager. */
	private DeviceDataManager deviceDataManager;

	/** Test provisioning monitor. */
	private ProvMonitor provMonitor;

	@BeforeEach
	void setup(TestInfo testInfo) {
		this.deviceDataManager = new DeviceDataManager();

		// Create config
		RRMConfig rrmConfig = new RRMConfig();

		// Create clients (null for now)
		UCentralClient client = null;

		// Instantiate ProvMonitor
		this.provMonitor = new ProvMonitor(
			rrmConfig.moduleConfig.provMonitorParams,
			deviceDataManager,
			client,
			null
		);
	}

	@Test
	@Order(1)
	void test_syncDataToProv() throws Exception {
		// TODO
	}
}
