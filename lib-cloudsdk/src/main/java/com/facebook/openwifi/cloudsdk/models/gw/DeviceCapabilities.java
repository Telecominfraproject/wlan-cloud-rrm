/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.gw;

import com.facebook.openwifi.cloudsdk.models.ap.Capabilities;

public class DeviceCapabilities {
	public Capabilities capabilities;
	public long firstUpdate;
	public long lastUpdate;
	public String serialNumber;
}
