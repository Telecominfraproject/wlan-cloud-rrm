/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.gw.models;

import java.util.List;

import com.google.gson.JsonObject;

public class DeviceWithStatus {
	public String owner;
	public String location;
	public String venue;
	public String serialNumber;
	public DeviceType deviceType;
	public String macAddress;
	public String manufacturer;
	public long UUID;
	public JsonObject configuration;
	public String compatible;
	public String fwUpdatePolicy;
	public List<NoteInfo> notes;
	public long createdTimestamp;
	public long lastConfigurationChange;
	public long lastConfigurationDownload;
	public long lastFWUpdate;
	public String firmware;
	public boolean connected;
	public String ipAddress;
	public long txBytes;
	public long rxBytes;
	public long associations_2G;
	public long associations_5G;
	public String devicePassword;
	// TODO: uCentralGw returns "lastContact" as string when uninitialized
	//public long lastContact;
	public long messageCount;
	public VerifiedCertificate verifiedCertificate;
}
