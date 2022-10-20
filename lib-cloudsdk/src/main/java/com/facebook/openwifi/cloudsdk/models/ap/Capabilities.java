/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import java.util.Map;

import com.google.gson.annotations.SerializedName;

public class Capabilities {
	public String compatible;
	public String model;
	public String platform;
	public Map<String, String[]> network;

	public static class Switch {
		public boolean enable;
		public boolean reset;
	}

	@SerializedName("switch") public Map<String, Switch> switch_;

	public static class Phy {
		public int tx_ant;
		public int rx_ant;
		public Integer[] frequencies;
		public Integer[] channels;
		public Integer[] dfs_channels;
		public String[] htmode;
		public String[] band;
		public int ht_capa;
		public int vht_capa;
		public Integer[] he_phy_capa;
		public Integer[] he_mac_capa;
	}

	public Map<String, Phy> wifi;
	// The fields below were omitted
	// macaddr;
	// country_code;
	// label_mcaddr;;
}
