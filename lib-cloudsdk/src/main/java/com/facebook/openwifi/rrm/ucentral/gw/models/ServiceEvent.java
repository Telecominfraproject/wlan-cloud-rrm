/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral.gw.models;

public class ServiceEvent {
	public static final String EVENT_JOIN = "join";
	public static final String EVENT_LEAVE = "leave";
	public static final String EVENT_KEEPALIVE = "keep-alive";
	public static final int KEEPALIVE_INTERVAL_S = 30;

	public String event;
	public long id;
	public String key;
	public String type;
	public String version;
	public String privateEndPoint;
	public String publicEndPoint;
}
