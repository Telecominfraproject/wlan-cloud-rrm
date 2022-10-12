/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral.gw.models;

import java.util.List;

public class UserInfo {
	public String id;
	public String name;
	public String description;
	public String avatar;
	public String email;
	public boolean validated;
	public String validationEmail;
	public long validationDate;
	public long created;
	public String validationURI;
	public long lastPasswordChange;
	public long lastEmailCheck;
	public long lastLogin;
	public String currentLoginURI;
	public String currentPassword;
	public List<String> lastPasswords;
	public boolean waitingForEmailCheck;
	public List<NoteInfo> notes;
	public String location;
	public String owner;
	public boolean suspended;
	public boolean blacklisted;
	public String locale;
	public String userRole;
	public String oauthType;
	public String oauthUserInfo;
	public String securityPolicy;
	public long securityPolicyChange;
	public long modified;
	public UserLoginLoginExtensions userTypeProprietaryInfo;
	public String signingUp;
}
