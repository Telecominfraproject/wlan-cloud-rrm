/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class DeviceCapabilities {
	public String compatible;
	public String model;
	public String platform;
	public JsonObject network;
	@SerializedName("switch") public JsonObject switch_;
	public JsonObject wifi;
}
