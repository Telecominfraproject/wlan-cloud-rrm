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

public class DeviceConfiguration {
	public static class DeviceConfigurationElement {
		public String name;
		public String description;
		public Integer weight;
		public String configuration;
	}

	// from ObjectInfo
	public String id;
	public String name;
	public String description;
	public List<NoteInfo> notes;
	public long created;
	public long modified;
	public List<Long> tags;

	public String managementPolicy;
	public List<String> deviceTypes;
	public List<DeviceConfigurationElement> configuration;
	public List<String> variables;
	public List<String> inUse;
	public boolean subscriberOnly;
	public DeviceRules deviceRules;
	public String venue;
	public String entity;
	public String subscriber;
}
