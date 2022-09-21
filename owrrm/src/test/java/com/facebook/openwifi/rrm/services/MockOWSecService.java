/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.services;

import java.util.Map;
import java.util.HashMap;
import java.time.Instant;

import com.facebook.openwifirrm.ucentral.gw.models.TokenValidationResult;
import com.facebook.openwifirrm.ucentral.gw.models.UserInfo;
import com.facebook.openwifirrm.ucentral.gw.models.WebTokenResult;

import com.google.gson.Gson;
import spark.Service;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * This is a mock OW Security service meant to be used in tests.
 *
 * @see <a href="https://github.com/Telecominfraproject/wlan-cloud-ucentralsec">owsec</a>
 */
public class MockOWSecService {
	private class TokenInfo {
		long expiry;
		long created;
	}

	private final Gson gson = new Gson();

	/** A mapping of valid tokens to their expiry time in seconds since epoch */
	private Map<String, TokenInfo> validTokens;

	/** The Spark service */
	private Service service;

	public MockOWSecService(int port) {
		validTokens = new HashMap<>();
		service = Service.ignite();
		service.port(port);

		service.get("/api/v1/validateToken", new ValidateTokenEndpoint());
		service.get("/api/v1/oauth2", new ValidateTokenEndpoint());
		service.get("/api/v1/systemEndpoints", new SystemEndpoint());

		service.awaitInitialization();
	}

	public void stop() {
		service.stop();
		service.awaitStop();
	}

	public void addToken(String token, long expiresInSec) {
		TokenInfo time = new TokenInfo();
		time.created = Instant.now().getEpochSecond();
		time.expiry = expiresInSec;

		validTokens.put(token, time);
	}

	public void removeToken(String token) {
		validTokens.remove(token);
	}

	public int getPort() { return service.port(); }

	public class Oauth2Endpoint implements Route {
		@Override
		public String handle(Request request, Response response) {
			response.status(501);
			return "Not Implemented";
		}
	}

	public class SystemEndpoint implements Route {
		@Override
		public String handle(Request request, Response response) {
			response.status(501);
			return "Not Implemented";
		}
	}

	public class ValidateTokenEndpoint implements Route {
		@Override
		public String handle(Request request, Response response) {
			String token = request.queryParams("token");
			if (token == null) {
				response.status(403);
				return "Forbidden";
			}

			TokenInfo info = validTokens.get(token);
			if (info == null) {
				response.status(403);
				return "Forbidden";
			}

			if (info.created + info.expiry < Instant.now().getEpochSecond()) {
				response.status(403);
				return "Forbidden";
			}

			TokenValidationResult result = new TokenValidationResult();
			result.userInfo = new UserInfo();
			result.tokenInfo = new WebTokenResult();
			result.tokenInfo.access_token = token;
			result.tokenInfo.created = info.created;
			result.tokenInfo.expires_in = info.expiry;

			return gson.toJson(result);
		}
	}
}
