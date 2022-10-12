/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral.prov.models;

import java.util.List;

public class InventoryConfigApplyResult {
	public String appliedConfiguration;
	public List<String> errors;
	public List<String> warnings;
	public int errorCode;
}
