/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.prov;

import java.util.List;

import com.facebook.openwifi.cloudsdk.models.gw.NoteInfo;

public class InventoryTag {
	// from ObjectInfo
	public String id;
	public String name;
	public String description;
	public List<NoteInfo> notes;
	public long created;
	public long modified;
	public List<Long> tags;

	public String serialNumber;
	public String deviceType;
	public String venue;
	public String entity;
	public String subscriber;
	public String qrCode;
	public String geoCode;
	public String location;
	public String contact;
	public String deviceConfiguration;
	public DeviceRules deviceRules;
	public String managementPolicy;
	public String state;
	public String devClass;
	public String locale;
}
