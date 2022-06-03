/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.RRMConfig.UCentralConfig.UCentralSocketParams;
import com.facebook.openwifirrm.ucentral.gw.models.CommandInfo;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceCapabilities;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceConfigureRequest;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceListWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.StatisticsRecords;
import com.facebook.openwifirrm.ucentral.gw.models.SystemInfoResults;
import com.facebook.openwifirrm.ucentral.gw.models.WifiScanRequest;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import kong.unirest.Config;
import kong.unirest.FailedResponse;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequestSummary;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.Interceptor;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;

/**
 * uCentral OpenAPI client.
 */
public class UCentralClient {
	private static final Logger logger = LoggerFactory.getLogger(UCentralClient.class);

	static {
		Unirest.config()
			// TODO currently disabling SSL/TLS cert verification
			.verifySsl(false)

			// Suppress unchecked exceptions (ex. SocketTimeoutException),
			// instead sending a (fake) FailedResponse.
			.interceptor(new Interceptor() {
				@SuppressWarnings("rawtypes")
				@Override
				public HttpResponse<?> onFail(
					Exception e,
					HttpRequestSummary request,
					Config config
				) throws UnirestException {
					String errMsg = String.format(
						"Request failed: %s %s",
						request.getHttpMethod(),
						request.getUrl()
					);
					logger.error(errMsg, e);
			        return new FailedResponse(e);
			    }
			});
	}

	/** Gson instance */
	private final Gson gson = new Gson();

	/** uCentral username */
	private final String username;
	/** uCentral password */
	private final String password;
	/** uCentralSec host */
	private final String uCentralSecHost;
	/** uCentralSec port */
	private final int uCentralSecPort;
	/** Socket parameters */
	private final UCentralSocketParams socketParams;

	/** Access token */
	private String accessToken;
	/** uCentralGw URL */
	private String uCentralGwUrl;

	/**
	 * Constructor.
	 * @param username uCentral username
	 * @param password uCentral password
	 * @param uCentralSecHost uCentralSec host
	 * @param uCentralSecPort uCentralSec port
	 * @param socketParams Socket parameters
	 */
	public UCentralClient(
		String username,
		String password,
		String uCentralSecHost,
		int uCentralSecPort,
		UCentralSocketParams socketParams
	) {
		this.username = username;
		this.password = password;
		this.uCentralSecHost = uCentralSecHost;
		this.uCentralSecPort = uCentralSecPort;
		this.socketParams = socketParams;
	}

	/** Return uCentralSec URL using the given endpoint. */
	private String makeUCentralSecUrl(String endpoint) {
		return String.format(
			"https://%s:%d/api/v1/%s",
			uCentralSecHost, uCentralSecPort, endpoint
		);
	}

	/** Return uCentralGw URL using the given endpoint. */
	private String makeUCentralGwUrl(String endpoint) {
		return String.format("%s/api/v1/%s", uCentralGwUrl, endpoint);
	}

	/** Perform login and uCentralGw endpoint retrieval. */
	public boolean login() {
		// Make request
		String url = makeUCentralSecUrl("oauth2");
		Map<String, Object> body = new HashMap<>();
		body.put("userId", username);
		body.put("password", password);
		HttpResponse<String> response = Unirest.post(url)
		      .header("accept", "application/json")
		      .body(body)
		      .asString();
		if (!response.isSuccess()) {
			logger.error(
				"Login failed: Response code {}", response.getStatus()
			);
			return false;
		}

		// Parse access token from response
		JSONObject respBody;
		try {
			respBody = new JSONObject(response.getBody());
		} catch (JSONException e) {
			logger.error("Login failed: Unexpected response", e);
			logger.debug("Response body: {}", response.getBody());
			return false;
		}
		if (!respBody.has("access_token")) {
			logger.error("Login failed: Missing access token");
			logger.debug("Response body: {}", respBody.toString());
			return false;
		}
		this.accessToken = respBody.getString("access_token");
		logger.info("Login successful as user: {}", username);
		logger.debug("Access token: {}", accessToken);

		// Find uCentral gateway URL
		return findGateway();
	}

	/** Find uCentralGw URL from uCentralSec. */
	private boolean findGateway() {
		// Make request
		String url = makeUCentralSecUrl("systemEndpoints");
		HttpResponse<String> response = Unirest.get(url)
		      .header("accept", "application/json")
		      .header("Authorization", "Bearer " + accessToken)
		      .asString();
		if (!response.isSuccess()) {
			logger.error(
				"/systemEndpoints failed: Response code {}",
				response.getStatus()
			);
			return false;
		}

		// Parse endpoints from response
		JSONObject respBody;
		JSONArray endpoints;
		try {
			respBody = new JSONObject(response.getBody());
			endpoints = respBody.getJSONArray("endpoints");
		} catch (JSONException e) {
			logger.error("/systemEndpoints failed: Unexpected response", e);
			logger.debug("Response body: {}", response.getBody());
			return false;
		}
		for (Object o : endpoints) {
			JSONObject endpoint = (JSONObject) o;
			if (
				endpoint.optString("type").equals("owgw") && endpoint.has("uri")
			) {
				this.uCentralGwUrl = endpoint.getString("uri");
				logger.info("Using uCentral gateway URL: {}", uCentralGwUrl);
				return true;
			}
		}
		logger.error("/systemEndpoints failed: Missing uCentral gateway URL");
		logger.debug("Response body: {}", respBody.toString());
		return false;
	}

	/** Send a GET request. */
	@SuppressWarnings("unused")
	private HttpResponse<String> httpGet(String endpoint) {
		return httpGet(endpoint, null);
	}

	/** Send a GET request with query parameters. */
	private HttpResponse<String> httpGet(
		String endpoint,
		Map<String, Object> parameters
	) {
		return httpGet(
			endpoint,
			parameters,
			socketParams.connectTimeoutMs,
			socketParams.socketTimeoutMs
		);
	}

	/** Send a GET request with query parameters using given timeout values. */
	private HttpResponse<String> httpGet(
		String endpoint,
		Map<String, Object> parameters,
		int connectTimeoutMs,
		int socketTimeoutMs
	) {
		String url = makeUCentralGwUrl(endpoint);
		GetRequest req = Unirest.get(url)
			.header("accept", "application/json")
			.header("Authorization", "Bearer " + accessToken)
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
		if (parameters != null) {
			return req.queryString(parameters).asString();
		} else {
			return req.asString();
		}
	}

	/** Send a POST request with a JSON body. */
	private HttpResponse<String> httpPost(String endpoint, Object body) {
		return httpPost(
			endpoint,
			body,
			socketParams.connectTimeoutMs,
			socketParams.socketTimeoutMs
		);
	}

	/** Send a POST request with a JSON body using given timeout values. */
	private HttpResponse<String> httpPost(
		String endpoint,
		Object body,
		int connectTimeoutMs,
		int socketTimeoutMs
	) {
		String url = makeUCentralGwUrl(endpoint);
		HttpRequestWithBody req = Unirest.post(url)
			.header("accept", "application/json")
			.header("Authorization", "Bearer " + accessToken)
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
		if (body != null) {
			req.header("Content-Type", "application/json");
			return req.body(body).asString();
		} else {
			return req.asString();
		}
	}

	/** Get uCentralGw system info. */
	public SystemInfoResults getSystemInfo() {
		Map<String, Object> parameters =
			Collections.singletonMap("command", "info");
		HttpResponse<String> response = httpGet("system", parameters);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), SystemInfoResults.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to SystemInfoResults: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Get a list of devices. */
	public List<DeviceWithStatus> getDevices() {
		Map<String, Object> parameters =
			Collections.singletonMap("deviceWithStatus", true);
		HttpResponse<String> response = httpGet("devices", parameters);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(
				response.getBody(), DeviceListWithStatus.class
			).devicesWithStatus;
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to DeviceListWithStatus: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Launch a wifi scan for a device (by serial number). */
	public CommandInfo wifiScan(String serialNumber, boolean verbose) {
		WifiScanRequest req = new WifiScanRequest();
		req.serialNumber = serialNumber;
		req.verbose = verbose;
		HttpResponse<String> response = httpPost(
			String.format("device/%s/wifiscan", serialNumber),
			req,
			socketParams.connectTimeoutMs,
			socketParams.wifiScanTimeoutMs
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), CommandInfo.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to CommandInfo: %s", response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Configure a device (by serial number). */
	public CommandInfo configure(String serialNumber, String configuration) {
		DeviceConfigureRequest req = new DeviceConfigureRequest();
		req.serialNumber = serialNumber;
		req.UUID = ThreadLocalRandom.current().nextLong();
		req.configuration = configuration;
		HttpResponse<String> response = httpPost(
			String.format("device/%s/configure", serialNumber), req
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), CommandInfo.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to CommandInfo: %s", response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/**
	 * Return the given number of latest statistics from a device (by serial
	 * number).
	 */
	public StatisticsRecords getLatestStats(String serialNumber, int limit) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("newest", true);
		parameters.put("limit", limit);
		HttpResponse<String> response = httpGet(
			String.format("device/%s/statistics", serialNumber), parameters
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), StatisticsRecords.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to StatisticsRecords: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Launch a get capabilities command for a device (by serial number). */
	public DeviceCapabilities getCapabilities(String serialNumber) {
		HttpResponse<String> response = httpGet(
			String.format("device/%s/capabilities", serialNumber)
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), DeviceCapabilities.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to DeviceCapabilities: %s", response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}
}
