/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.gw.models;

import com.google.gson.JsonObject;

public class CommandInfo {
	public String UUID;
	public String command;
	public JsonObject details;
	public String serialNumber;
	public long submitted;
	public long executed;
	public long completed;
	public long when;
	public String errorText;
	public JsonObject results;
	public long errorCode;
	public String submittedBy;
	public String status;
	public long custom;
	public long waitingForFile;
	public long attachFile;
	public long attachSize;
	public String attachType;
}
