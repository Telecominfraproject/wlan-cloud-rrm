/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.gw;

public class ScriptRequest {
	public String serialNumber;
	public long timeout = 30; // in seconds
	public String type; // "shell", "ucode", "uci"
	public String script;
	public String scriptId; // required but unused?
	public long when = 0;
}
