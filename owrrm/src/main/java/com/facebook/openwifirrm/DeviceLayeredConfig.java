/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.util.Map;
import java.util.TreeMap;

/**
 * Layered AP configuration model.
 */
public class DeviceLayeredConfig {
	/** Config per AP (by serial number) - highest priority */
	public Map<String, DeviceConfig> apConfig = new TreeMap<>();

	/** Config per "RF zone" - mid priority */
	public Map<String, DeviceConfig> zoneConfig = new TreeMap<>();

	/** Config for all APs/zones - lowest priority */
	public DeviceConfig networkConfig = new DeviceConfig();
}
