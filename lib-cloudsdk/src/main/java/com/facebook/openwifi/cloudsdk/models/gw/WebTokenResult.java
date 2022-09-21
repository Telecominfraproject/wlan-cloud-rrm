/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.gw;

public class WebTokenResult {
	public String access_token;
	public String refresh_token;
	public String token_type;
	public long expires_in;
	public int idle_timeout;
	public String username;
	public long created;
	public boolean userMustChangePassword;
	public int errorCode;
	public WebTokenAclTemplate aclTemplate;
	public long lastRefresh;
}
