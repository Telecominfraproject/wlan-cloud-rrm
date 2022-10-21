/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.facebook.openwifi.cloudsdk.models.ap.UCentralSchema;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Wrapper around uCentral AP configuration.
 */
public class UCentralApConfiguration {
	/** The raw configuration. */
	private final UCentralSchema config;

	/** Constructor from JSON string. */
	public UCentralApConfiguration(String configJson) {
		this.config = new Gson().fromJson(configJson, UCentralSchema.class);
	}

	/** Constructor from JsonObject */
	public UCentralApConfiguration(JsonObject config) {
		this.config = new Gson().fromJson(config, UCentralSchema.class);
	}

	@Override
	public String toString() {
		return config.toString();
	}

	/** Serialize the configuration to JSON using the given Gson instance. */
	public String toString(Gson gson) {
		return gson.toJson(config);
	}

	/** Return the number of radios, or -1 if the field is missing/malformed. */
	public int getRadioCount() {
		if (config.radios == null) {
			return -1;
		}
		return config.radios.size();
	}

	/** Return all info in the radio config (or an empty array if none). */
	public List<UCentralSchema.Radio> getRadioConfigList() {
		return config.radios;
	}

	/** Return all the operational bands of an AP (from the radio config) */
	public Set<String> getRadioBandsSet(
		List<UCentralSchema.Radio> radioConfigList
	) {
		Set<String> radioBandsSet = new HashSet<>();
		if (radioConfigList == null) {
			return radioBandsSet;
		}
		for (
			int radioIndex = 0; radioIndex < radioConfigList.size();
			radioIndex++
		) {
			UCentralSchema.Radio radio = radioConfigList.get(radioIndex);
			if (radio == null || radio.band == null) {
				continue;
			}
			radioBandsSet.add(radio.band);
		}
		return radioBandsSet;
	}

	/** Return the radio config at the given index, or null if invalid. */
	public UCentralSchema.Radio getRadioConfig(int index) {
		if (getRadioCount() < index) {
			return null;
		}
		List<UCentralSchema.Radio> radios = config.radios;
		if (radios == null) {
			return null;
		}
		UCentralSchema.Radio radio = radios.get(index);
		return radio;
	}

	/** Set radio config at the given index. Adds empty objects as needed. */
	public void setRadioConfig(int index, UCentralSchema.Radio radioConfig) {
		int radioCount = getRadioCount();
		if (radioCount == -1) {
			config.radios = new ArrayList<UCentralSchema.Radio>();
			radioCount = 0;
		}
		List<UCentralSchema.Radio> radios = config.radios;
		for (int i = radioCount; i <= index; i++) {
			// insert empty objects as needed
			radios.add(new UCentralSchema.Radio());
		}
		radios.set(index, radioConfig);
	}

	/**
	 * Return the statistics interval (in seconds), or -1 if the field is
	 * missing/malformed.
	 */
	public int getStatisticsInterval() {
		try {
			return config.metrics.statistics.interval;
		} catch (Exception e) {
			return -1;
		}
	}

	/** Set the statistics interval to the given value (in seconds). */
	public void setStatisticsInterval(int intervalSec) {
		if (config.metrics == null) {
			config.metrics = new UCentralSchema.Metrics();
		}
		if (
			config.metrics.statistics == null
		) {
			config.metrics.statistics = new UCentralSchema.Metrics.Statistics();
		}
		config.metrics.statistics.interval = intervalSec;
	}
}
