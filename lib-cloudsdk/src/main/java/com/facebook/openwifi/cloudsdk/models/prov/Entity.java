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

public class Entity {
	// from ObjectInfo
	public String id;
	public String name;
	public String description;
	public List<NoteInfo> notes;
	public long created;
	public long modified;
	public List<Long> tags;

	public String parent;
	public List<String> children;
	public List<String> venues;
	public List<String> contacts;
	public List<String> locations;
	public String managementPolicy;
	public List<String> deviceConfiguration;
	public List<String> devices;
	public List<String> managementPolicies;
	public List<String> variables;
	public List<String> managementRoles;
	public List<String> maps;
	public List<String> configurations;
	public DeviceRules deviceRules;
	public List<String> sourceIP;
	public boolean defaultEntity;
	public String type;
}
