/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.util.HashSet;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Wrapper around uCentral AP configuration.
 */
public class UCentralApConfiguration {
	/** The raw configuration. */
	private final JsonObject config;

	/** Constructor from JSON string. */
	public UCentralApConfiguration(String configJson) {
		this.config = new Gson().fromJson(configJson, JsonObject.class);
	}

	/** Constructor from JsonObject (makes deep copy). */
	public UCentralApConfiguration(JsonObject config) {
		this.config = config.deepCopy();
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
		if (!config.has("radios") || !config.get("radios").isJsonArray()) {
			return -1;
		}
		return config.getAsJsonArray("radios").size();
	}

	/** Return all info in the radio config (or an empty array if none). */
	public JsonArray getRadioConfigList() {
		if (!config.has("radios") || !config.get("radios").isJsonArray()) {
			return new JsonArray();
		}
		return config.getAsJsonArray("radios");
	}

	/** Return all the operational bands of an AP (from the radio config) */
	public Set<String> getRadioBandsSet(JsonArray radioConfigList) {
		Set<String> radioBandsSet = new HashSet<>();
		if (radioConfigList == null) {
			return radioBandsSet;
		}
		for (
			int radioIndex = 0; radioIndex < radioConfigList.size();
			radioIndex++
		) {
			JsonElement e = radioConfigList.get(radioIndex);
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject radioObject = e.getAsJsonObject();
			if (!radioObject.has("band")) {
				continue;
			}
			radioBandsSet.add(radioObject.get("band").getAsString());
		}
		return radioBandsSet;
	}

	/** Return the radio config at the given index, or null if invalid. */
	public JsonObject getRadioConfig(int index) {
		if (getRadioCount() < index) {
			return null;
		}
		JsonArray radios = config.getAsJsonArray("radios");
		if (radios == null) {
			return null;
		}
		JsonElement e = radios.get(index);
		if (!e.isJsonObject()) {
			return null;
		}
		return e.getAsJsonObject();
	}

	/** Set radio config at the given index. Adds empty objects as needed. */
	public void setRadioConfig(int index, JsonObject radioConfig) {
		int radioCount = getRadioCount();
		if (radioCount == -1) {
			config.add("radios", new JsonArray());
			radioCount = 0;
		}
		JsonArray radios = config.getAsJsonArray("radios");
		for (int i = radioCount; i <= index; i++) {
			// insert empty objects as needed
			radios.add(new JsonObject());
		}
		radios.set(index, radioConfig);
	}

	/**
	 * Return the statistics interval (in seconds), or -1 if the field is
	 * missing/malformed.
	 */
	public int getStatisticsInterval() {
		try {
			return config
				.getAsJsonObject("metrics")
				.getAsJsonObject("statistics")
				.get("interval")
				.getAsInt();
		} catch (Exception e) {
			return -1;
		}
	}

	/** Set the statistics interval to the given value (in seconds). */
	public void setStatisticsInterval(int intervalSec) {
		if (!config.has("metrics") || !config.get("metrics").isJsonObject()) {
			config.add("metrics", new JsonObject());
		}
		JsonObject metrics = config.getAsJsonObject("metrics");
		if (
			!metrics.has("statistics") ||
				!metrics.get("statistics").isJsonObject()
		) {
			metrics.add("statistics", new JsonObject());
		}
		JsonObject statistics = metrics.getAsJsonObject("statistics");
		statistics.addProperty("interval", intervalSec);
	}
}
