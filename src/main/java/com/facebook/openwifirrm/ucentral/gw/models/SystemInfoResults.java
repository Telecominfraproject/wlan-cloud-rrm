/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.gw.models;

import com.google.gson.JsonArray;

public class SystemInfoResults {
	public String version;
	public long uptime;
	public long start;
	public String os;
	public int processors;
	public String hostname;
	public JsonArray certificates;
}
