/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.prov.models;

import java.util.List;

import com.facebook.openwifirrm.ucentral.gw.models.NoteInfo;

public class Venue {
	// from ObjectInfo
	public String id;
	public String name;
	public String description;
	public List<NoteInfo> notes;
	public long created;
	public long modified;
	public List<Long> tags;

	public String entity;
	public String parent;
	public List<String> children;
	public String managementPolicy;
	public List<String> devices;
	public DiGraph topology;
	public String design;
	public List<String> deviceConfiguration;
	public List<String> contacts;
	public String location;
	public DeviceRules deviceRules;
	public List<String> sourceIP;
	public List<String> managementPolicies;
	public List<String> managementRoles;
	public List<String> variables;
	public List<String> maps;
	public List<String> configurations;
	public List<String> boards;
}
