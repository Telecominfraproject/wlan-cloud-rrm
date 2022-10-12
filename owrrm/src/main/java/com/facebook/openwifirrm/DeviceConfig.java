/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * AP configuration model.
 * <p>
 * Implementation notes:
 * <ul>
 * <li>Fields must be nullable (no primitives, use boxed types instead).</li>
 * <li>This currently does not support merging of nested structures.</li>
 * </ul>
 */
public class DeviceConfig {
	/** Whether RRM algorithms are enabled */
	public Boolean enableRRM;

	/** The RRM schedule (if enabled), only applied at the zone or network layer */
	public RRMSchedule schedule;

	/** Whether pushing device config is enabled */
	public Boolean enableConfig;

	/** Whether automatic wifi scans are enabled */
	public Boolean enableWifiScan;

	/** The length of a square/cube for the target area (see {@link #location}) */
	public Integer boundary;

	/** The AP location either 2D or 3D */
	public List<Integer> location;

	/**
	 * The list of allowed channels, or null to allow all
	 * (map from band to channel)
	 */
	public Map<String, List<Integer>> allowedChannels;

	/**
	 * The list of allowed channel widths, or null to allow all
	 * (map from band to channel)
	 */
	public Map<String, List<Integer>> allowedChannelWidths;

	/** The RRM-assigned channels to use (map from radio to channel) */
	public Map<String, Integer> autoChannels;

	/**
	 * The user-assigned channels to use, overriding "autoChannels"
	 * (map from band to channel)
	 */
	public Map<String, Integer> userChannels;

	/**
	 * The list of allowed tx powers, or null to allow all
	 * (map from band to tx power)
	 */
	public Map<String, List<Integer>> allowedTxPowers;

	/** The RRM-assigned tx powers to use (map from band to tx power) */
	public Map<String, Integer> autoTxPowers;

	/** The user-assigned tx powers to use, overriding "autoTxPowers" */
	public Map<String, Integer> userTxPowers;

	/** Create the default config. */
	public static DeviceConfig createDefault() {
		DeviceConfig config = new DeviceConfig();

		config.enableRRM = true;
		config.schedule = null;
		config.enableConfig = true;
		config.enableWifiScan = true;
		config.boundary = null;
		config.location = null;
		config.allowedChannels = null;
		config.allowedChannelWidths = null;
		config.autoChannels = null;
		config.userChannels = null;
		config.allowedTxPowers = null;
		config.autoTxPowers = null;
		config.userTxPowers = null;

		return config;
	}

	/** Return true if all public instance fields are null (via reflection). */
	@Hidden /* prevent Jackson object mapper from generating "empty" property */
	public boolean isEmpty() {
		for (Field field : getClass().getFields()) {
			try {
				if (field.get(this) != null) {
					return false;
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				continue;
			}
		}
		return true;
	}

	/**
	 * Merge all non-null public fields of the given object into this object
	 * (via reflection).
	 */
	public void apply(DeviceConfig override) {
		if (override == null) {
			return;
		}
		for (Field field : getClass().getFields()) {
			try {
				Object overrideVal = field.get(override);
				if (overrideVal != null) {
					field.set(this, overrideVal);
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				continue;
			}
		}
	}
}
