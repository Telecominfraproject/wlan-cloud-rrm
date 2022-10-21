/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import java.util.Map;
import java.util.List;

import com.google.gson.annotations.SerializedName;

public class Capabilities {
	public String compatible;
	public String model;
	public String platform;
	public Map<String, List<String>> network;

	public static class Switch {
		public boolean enable;
		public boolean reset;
	}

	@SerializedName("switch") public Map<String, Switch> switch_;

	public static class Phy {
		public int tx_ant;
		public int rx_ant;
		public int[] frequencies;
		public int[] channels;
		public int[] dfs_channels;
		public String[] htmode;
		public String[] band;
		public int ht_capa;
		public int vht_capa;
		public int[] he_phy_capa;
		public int[] he_mac_capa;
		public String country;
		public String dfs_region;
		public int temperature;
	}

	public Map<String, Phy> wifi;
	// TODO The fields below were omitted
	// macaddr;
	// country_code;
	// label_macaddr;
}
