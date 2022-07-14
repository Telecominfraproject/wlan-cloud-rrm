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
import com.facebook.openwifirrm.ucentral.gw.models.ServiceEvent;
import com.facebook.openwifirrm.ucentral.gw.models.StatisticsRecords;
import com.facebook.openwifirrm.ucentral.gw.models.SystemInfoResults;
import com.facebook.openwifirrm.ucentral.gw.models.WifiScanRequest;
import com.facebook.openwifirrm.ucentral.prov.models.EntityList;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTagList;
import com.facebook.openwifirrm.ucentral.prov.models.SerialNumberList;
import com.facebook.openwifirrm.ucentral.prov.models.VenueList;
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
 * This implementation supports both public and private endpoints.
 * <p>
 * For public endpoint communication:
 * <ul>
 *   <li>
 *     Hardcode owsec URL and use "/systemendpoints" endpoint since Kafka may
 *     be inaccessible; access to Kafka is a hack for development only, but
 *     could be secured in production with SASL/MTLS
 *   </li>
 *   <li>
 *     Exchange username/password for an oauth token to pass to other services
 *   </li>
 * </ul>
 * For private endpoint communication:
 * <ul>
 *   <li>
 *     Use Kafka "system_endpoints" topic to get the private endpoint and the
 *     key to use in the "X-API-KEY" header for each service
 *   </li>
 * </ul>
 */
public class UCentralClient {
	private static final Logger logger = LoggerFactory.getLogger(UCentralClient.class);

	// Service names ("type" field)
	private static final String OWGW_SERVICE = "owgw";
	private static final String OWSEC_SERVICE = "owsec";
	private static final String OWPROV_SERVICE = "owprov";

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

	/** The RRM private endpoint. */
	private final String privateEndpoint;

	/** Whether to use public endpoints. */
	private boolean usePublicEndpoints;

	/** uCentral username */
	private final String username;

	/** uCentral password */
	private final String password;

	/** Socket parameters */
	private final UCentralSocketParams socketParams;

	/** The learned service endpoints. */
	private final Map<String, ServiceEvent> serviceEndpoints = new HashMap<>();

	/**
	 * The access token obtained from uCentralSec, needed only when using public
	 * endpoints.
	 */
	private String accessToken;

	/**
	 * Constructor.
	 * @param privateEndpoint advertise the RRM private endpoint to the SDK
	 * @param usePublicEndpoints whether to use public or private endpoints
	 * @param uCentralSecPublicEndpoint the uCentralSec public endpoint
	 *        (if needed)
	 * @param username uCentral username (for public endpoints only)
	 * @param password uCentral password (for public endpoints only)
	 * @param socketParams Socket parameters
	 */
	public UCentralClient(
		String privateEndpoint,
		boolean usePublicEndpoints,
		String uCentralSecPublicEndpoint,
		String username,
		String password,
		UCentralSocketParams socketParams
	) {
		this.privateEndpoint = privateEndpoint;
		this.usePublicEndpoints = usePublicEndpoints;
		this.username = username;
		this.password = password;
		this.socketParams = socketParams;

		if (usePublicEndpoints) {
			setServicePublicEndpoint(OWSEC_SERVICE, uCentralSecPublicEndpoint);
		}
	}

	/** Return uCentral service URL using the given endpoint. */
	private String makeServiceUrl(String endpoint, String service) {
		ServiceEvent e = serviceEndpoints.get(service);
		if (e == null) {
			throw new RuntimeException("unknown service: " + service);
		}
		String url = usePublicEndpoints ? e.publicEndPoint : e.privateEndPoint;
		return String.format("%s/api/v1/%s", url, endpoint);
	}

	/** Perform login and uCentralGw endpoint retrieval. */
	public boolean login() {
		// Make request
		Map<String, Object> body = new HashMap<>();
		body.put("userId", username);
		body.put("password", password);
		HttpResponse<String> response = httpPost("oauth2", OWSEC_SERVICE, body);
		if (!response.isSuccess()) {
			logger.error(
				"Login failed: Response code {}, body: {}",
				response.getStatus(),
				response.getBody()
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

		// Load system endpoints
		return loadSystemEndpoints();
	}

	/** Read system endpoint URLs from uCentralSec. */
	private boolean loadSystemEndpoints() {
		// Make request
		HttpResponse<String> response =
			httpGet("systemEndpoints", OWSEC_SERVICE);
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
			if (endpoint.has("type") && endpoint.has("uri")) {
				String service = endpoint.getString("type");
				String uri = endpoint.getString("uri");
				setServicePublicEndpoint(service, uri);
				logger.info("Using {} URL: {}", service, uri);
			}
		}
		if (!isInitialized()) {
			logger.error("/systemEndpoints failed: missing some required endpoints");
			logger.debug("Response body: {}", respBody.toString());
			return false;
		}
		return true;
	}

	/**
	 * Return true if this service has learned the endpoints of all essential
	 * dependent services, along with API keys (if necessary).
	 */
	public boolean isInitialized() {
		if (
			!serviceEndpoints.containsKey(OWGW_SERVICE) ||
			!serviceEndpoints.containsKey(OWSEC_SERVICE)
		) {
			return false;
		};
		if (usePublicEndpoints && accessToken == null) {
			return false;
		}
		return true;
	}

	/**
	 * Return true if this service has learned the owprov endpoint, along with
	 * API keys (if necessary).
	 */
	public boolean isProvInitialized() {
		if (!serviceEndpoints.containsKey(OWPROV_SERVICE)) {
			return false;
		};
		if (usePublicEndpoints && accessToken == null) {
			return false;
		}
		return true;
	}

	/** Send a GET request. */
	private HttpResponse<String> httpGet(String endpoint, String service) {
		return httpGet(endpoint, service, null);
	}

	/** Send a GET request with query parameters. */
	private HttpResponse<String> httpGet(
		String endpoint,
		String service,
		Map<String, Object> parameters
	) {
		return httpGet(
			endpoint,
			service,
			parameters,
			socketParams.connectTimeoutMs,
			socketParams.socketTimeoutMs
		);
	}

	/** Send a GET request with query parameters using given timeout values. */
	private HttpResponse<String> httpGet(
		String endpoint,
		String service,
		Map<String, Object> parameters,
		int connectTimeoutMs,
		int socketTimeoutMs
	) {
		String url = makeServiceUrl(endpoint, service);
		GetRequest req = Unirest.get(url)
			.header("accept", "application/json")
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
		if (usePublicEndpoints) {
			if (accessToken != null) {
				req.header("Authorization", "Bearer " + accessToken);
			}
		} else {
			req
				.header("X-API-KEY", this.getApiKey(OWGW_SERVICE))
				.header("X-INTERNAL-NAME", this.privateEndpoint);
		}
		if (parameters != null) {
			return req.queryString(parameters).asString();
		} else {
			return req.asString();
		}
	}

	/** Send a POST request with a JSON body. */
	private HttpResponse<String> httpPost(
		String endpoint,
		String service,
		Object body
	) {
		return httpPost(
			endpoint,
			service,
			body,
			socketParams.connectTimeoutMs,
			socketParams.socketTimeoutMs
		);
	}

	/** Send a POST request with a JSON body using given timeout values. */
	private HttpResponse<String> httpPost(
		String endpoint,
		String service,
		Object body,
		int connectTimeoutMs,
		int socketTimeoutMs
	) {
		String url = makeServiceUrl(endpoint, service);
		HttpRequestWithBody req = Unirest.post(url)
			.header("accept", "application/json")
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
		if (usePublicEndpoints) {
			if (accessToken != null) {
				req.header("Authorization", "Bearer " + accessToken);
			}
		} else {
			req
				.header("X-API-KEY", this.getApiKey(OWGW_SERVICE))
				.header("X-INTERNAL-NAME", this.privateEndpoint);
		}
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
		HttpResponse<String> response = httpGet("system", OWGW_SERVICE, parameters);
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
		HttpResponse<String> response = httpGet("devices", OWGW_SERVICE, parameters);
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
			OWGW_SERVICE,
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
			String.format("device/%s/configure", serialNumber), OWGW_SERVICE, req
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
			String.format("device/%s/statistics", serialNumber), OWGW_SERVICE, parameters
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
			String.format("device/%s/capabilities", serialNumber), OWGW_SERVICE
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

	/** Retrieve a list of inventory from owprov. */
	public InventoryTagList getProvInventory() {
		HttpResponse<String> response = httpGet("inventory", OWPROV_SERVICE);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), InventoryTagList.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to InventoryTagList: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Retrieve a list of inventory with RRM enabled from owprov. */
	public SerialNumberList getProvInventoryForRRM() {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("rrmOnly", true);
		HttpResponse<String> response =
			httpGet("inventory", OWPROV_SERVICE, parameters);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), SerialNumberList.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to SerialNumberList: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Retrieve a list of venues from owprov. */
	public VenueList getProvVenues() {
		HttpResponse<String> response = httpGet("venue", OWPROV_SERVICE);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), VenueList.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to VenueList: %s", response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/** Retrieve a list of entities from owprov. */
	public EntityList getProvEntities() {
		HttpResponse<String> response = httpGet("entity", OWPROV_SERVICE);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), EntityList.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to EntityList: %s", response.getBody()
			);
			logger.error(errMsg, e);
			return null;
		}
	}

	/**
	 * System endpoints and API keys come from the service_event Kafka topic.
	 */
	public void setServiceEndpoint(String service, ServiceEvent event) {
		if (usePublicEndpoints) {
			logger.trace(
				"Dropping service endpoint for '{}' (using public endpoints)",
				service
			);
		} else {
			if (this.serviceEndpoints.put(service, event) == null) {
				logger.info(
					"Adding service endpoint for {}: '{}' <public>, '{}' <private>",
					service,
					event.publicEndPoint,
					event.privateEndPoint
				);
			}
		}
	}

	/**
	 * Set a public endpoint for a service, completely overriding any existing
	 * entry.
	 */
	private void setServicePublicEndpoint(String service, String endpoint) {
		ServiceEvent event = new ServiceEvent();
		event.type = service;
		event.publicEndPoint = endpoint;
		this.serviceEndpoints.put(service, event);
	}

	/**
	 * Get the API key for a service
	 * @param service Service identifier. From the "type" field of service_events topic.
	 *   E.g.: owgw, owsec, ...
	 */
	private String getApiKey(String service) {
		ServiceEvent s = this.serviceEndpoints.get(service);
		if (s == null) {
			logger.error("Error: API key not found for service: {}", service);
			return null;
		}
		return s.key;
	}
}
