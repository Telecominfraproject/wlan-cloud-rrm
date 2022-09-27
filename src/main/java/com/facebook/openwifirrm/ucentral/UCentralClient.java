/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.time.Instant;
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
import com.facebook.openwifirrm.ucentral.gw.models.TokenValidationResult;
import com.facebook.openwifirrm.ucentral.gw.models.WebTokenRefreshRequest;
import com.facebook.openwifirrm.ucentral.gw.models.WebTokenResult;
import com.facebook.openwifirrm.ucentral.gw.models.WifiScanRequest;
import com.facebook.openwifirrm.ucentral.prov.models.EntityList;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTagList;
import com.facebook.openwifirrm.ucentral.prov.models.RRMDetails;
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
 *     Use Kafka "system_endpoints" topic to find the private endpoint and API
 *     key for each service
 *   </li>
 * </ul>
 */
public class UCentralClient {
	private static final Logger logger =
		LoggerFactory.getLogger(UCentralClient.class);

	// Service names ("type" field)
	private static final String OWGW_SERVICE = "owgw";
	private static final String OWSEC_SERVICE = "owsec";
	private static final String OWPROV_SERVICE = "owprov";

	static {
		Unirest.config()
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

	/**
	 * Toggle verifying SSL/TLS certificates. This should be set only during
	 * initialization, otherwise it may NOT take effect.
	 */
	public static void verifySsl(boolean enable) {
		Unirest.config().verifySsl(enable);
	}

	/** Gson instance */
	private final Gson gson = new Gson();

	/** The RRM public endpoint. */
	private final String rrmEndpoint;

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
	private WebTokenResult accessToken;

	/** Time window to refresh token in seconds. */
	private final int TOKEN_REFRESH_WINDOW_S = 21600;

	/**
	 * Constructor.
	 * @param rrmEndpoint advertise this RRM endpoint to the SDK
	 * @param usePublicEndpoints whether to use public or private endpoints
	 * @param uCentralSecPublicEndpoint the uCentralSec public endpoint
	 *        (if needed)
	 * @param username uCentral username (for public endpoints only)
	 * @param password uCentral password (for public endpoints only)
	 * @param socketParams Socket parameters
	 */
	public UCentralClient(
		String rrmEndpoint,
		boolean usePublicEndpoints,
		String uCentralSecPublicEndpoint,
		String username,
		String password,
		UCentralSocketParams socketParams
	) {
		this.rrmEndpoint = rrmEndpoint;
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
		HttpResponse<String> response =
			httpPost("oauth2", OWSEC_SERVICE, body, null);
		if (!response.isSuccess()) {
			logger.error(
				"Login failed: Response code {}, body: {}",
				response.getStatus(),
				response.getBody()
			);
			return false;
		}

		// Parse access token from response
		WebTokenResult token;
		try {
			token = gson.fromJson(response.getBody(), WebTokenResult.class);
		} catch (JsonSyntaxException e) {
			logger.error("Login failed: Unexpected response", e);
			logger.debug("Response body: {}", response.getBody());
			return false;
		}
		if (token.access_token == null || token.access_token.isEmpty()) {
			logger.error("Login failed: Missing access token");
			logger.debug("Response body: {}", token.toString());
			return false;
		}
		this.accessToken = token;
		logger.info("Login successful as user: {}", username);
		logger.debug("Access token: {}", accessToken.access_token);
		logger.debug("Refresh token: {}", accessToken.refresh_token);

		// Load system endpoints
		return loadSystemEndpoints();
	}

	/**
	 * Check if an access token is expired.
	 *
	 * @return true if the access token is expired
	 */
	private boolean isAccessTokenExpired() {
		return accessToken.created + accessToken.idle_timeout <
			Instant.now().getEpochSecond();
	}

	/**
	 * Check if the refresh token is expired.
	 *
	 * @return true if the refresh token is expired
	 */
	private boolean isRefreshTokenExpired() {
		return accessToken.created + TOKEN_REFRESH_WINDOW_S <
			Instant.now().getEpochSecond();
	}

	/**
	 * Get access token. If the refresh token is expired, login again.
	 * If the access token is expired, POST a WebTokenRefreshRequest to refresh token.
	 * Otherwise return the current access token.
	 *
	 * @return a valid access token ({@code WebTokenResult})
	 */
	private WebTokenResult getAccessToken() {
		if (isRefreshTokenExpired()) {
			logger.info("Refresh token is expired, login again");
			if (login()) {
				return accessToken;
			}
			return null;
		} else if (isAccessTokenExpired()) {
			logger.debug("Access token is expired, start refreshing a token.");
			WebTokenResult refreshToken = refreshToken();
			if (refreshToken != null) {
				logger.debug("Successfully refresh token.");
				return refreshToken;
			}
			logger.error(
				"Fail to refresh token with access token: {}",
				accessToken.access_token
			);
			return null;
		}
		return accessToken;
	}

	/**
	 * POST a WebTokenRefreshRequest to refresh the access token.
	 *
	 * @return valid access token if success, otherwise return null.
	 */
	private WebTokenResult refreshToken() {
		WebTokenRefreshRequest refreshRequest = new WebTokenRefreshRequest();
		refreshRequest.userId = username;
		refreshRequest.refreshToken = accessToken.refresh_token;
		logger.debug("refresh token: {}", accessToken.refresh_token);
		Map<String, Object> query =
			Collections.singletonMap("grant_type", "refresh_token");
		HttpResponse<String> response =
			httpPost(
				"oauth2",
				OWSEC_SERVICE,
				refreshRequest,
				query
			);
		if (!response.isSuccess()) {
			logger.error(
				"Failed to refresh token: Response code {}, body: {}",
				response.getStatus(),
				response.getBody()
			);
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), WebTokenResult.class);
		} catch (JsonSyntaxException e) {
			logger.error(
				"Failed to serialize WebTokenResult: Unexpected response:",
				e
			);
			logger.debug("Response body: {}", response.getBody());
			return null;
		}
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
			logger.error(
				"/systemEndpoints failed: missing some required endpoints"
			);
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
		}
		accessToken = getAccessToken();
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
		}
		accessToken = getAccessToken();
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
				req.header(
					"Authorization",
					"Bearer " + accessToken.access_token
				);
			}
		} else {
			req
				.header("X-API-KEY", this.getApiKey(service))
				.header("X-INTERNAL-NAME", this.rrmEndpoint);
		}
		if (parameters != null) {
			return req.queryString(parameters).asString();
		} else {
			return req.asString();
		}
	}

	/** Send a POST request with a JSON body and query params. */
	private HttpResponse<String> httpPost(
		String endpoint,
		String service,
		Object body,
		Map<String, Object> query
	) {
		return httpPost(
			endpoint,
			service,
			body,
			query,
			socketParams.connectTimeoutMs,
			socketParams.socketTimeoutMs
		);
	}

	/** Send a POST request with a JSON body and query params using given timeout values. */
	private HttpResponse<String> httpPost(
		String endpoint,
		String service,
		Object body,
		Map<String, Object> query,
		int connectTimeoutMs,
		int socketTimeoutMs
	) {
		String url = makeServiceUrl(endpoint, service);
		HttpRequestWithBody req = Unirest.post(url)
			.header("accept", "application/json")
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
		if (query != null && !query.isEmpty()) {
			req.queryString(query);
		}
		if (usePublicEndpoints) {
			if (accessToken != null) {
				req.header(
					"Authorization",
					"Bearer " + accessToken.access_token
				);
			}
		} else {
			req
				.header("X-API-KEY", this.getApiKey(service))
				.header("X-INTERNAL-NAME", this.rrmEndpoint);
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
		HttpResponse<String> response =
			httpGet("system", OWGW_SERVICE, parameters);
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
		HttpResponse<String> response =
			httpGet("devices", OWGW_SERVICE, parameters);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(
				response.getBody(),
				DeviceListWithStatus.class
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

	/**
	 * Launch a wifi scan for a device (by serial number).
	 * <p>
	 * An AP can conduct a wifiscan, which can be either active or passive. In an
	 * active wifiscan, the AP sends out a wifiscan request and listens for
	 * responses from other APs. In a passive wifiscan, the AP does not send out a
	 * wifiscan request but instead just waits for periodic beacons from the other
	 * APs. (Note that neither the responses to requests (in active mode) or the
	 * periodic beacons are guaranteed to happen at any particular time (and it
	 * depends on network traffic)).
	 * <p>
	 * The AP conducting the wifiscan goes through every channel and listens for
	 * responses/beacons. However, the responding/beaconing APs only send responses
	 * on channels they are currently using.
	 */
	public CommandInfo wifiScan(String serialNumber, boolean verbose) {
		WifiScanRequest req = new WifiScanRequest();
		req.serialNumber = serialNumber;
		req.verbose = verbose;
		HttpResponse<String> response = httpPost(
			String.format("device/%s/wifiscan", serialNumber),
			OWGW_SERVICE,
			req,
			null,
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
				"Failed to deserialize to CommandInfo: %s",
				response.getBody()
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
			String.format("device/%s/configure", serialNumber),
			OWGW_SERVICE,
			req,
			null
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), CommandInfo.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to CommandInfo: %s",
				response.getBody()
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
			String.format("device/%s/statistics", serialNumber),
			OWGW_SERVICE,
			parameters
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
			String.format("device/%s/capabilities", serialNumber),
			OWGW_SERVICE
		);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(response.getBody(), DeviceCapabilities.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to DeviceCapabilities: %s",
				response.getBody()
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

	/**
	 * Retrieve the RRM config and schedule for a specific AP
	 *
	 * @param serialNumber the serial number of the AP
	 *
	 * @return RRMDetails, containing information about the RRM
	 *   schedule and parameters
	 */
	public RRMDetails getProvInventoryRRMDetails(String serialNumber) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("rrmSettings", true);
		HttpResponse<String> response =
			httpGet(String.format("inventory/%s", serialNumber), OWPROV_SERVICE, parameters);
		if (!response.isSuccess()) {
			logger.error("Error: {}", response.getBody());
			return null;
		}

		try {
			return gson.fromJson(response.getBody(), RRMDetails.class);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to RRMDetails: %s",
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
				"Failed to deserialize to VenueList: %s",
				response.getBody()
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
				"Failed to deserialize to EntityList: %s",
				response.getBody()
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
	 * Validate the given token via uCentralSec.
	 */
	public TokenValidationResult validateToken(String token) {
		Map<String, Object> parameters =
			Collections.singletonMap("token", token);
		HttpResponse<String> response =
			httpGet("validateToken", OWSEC_SERVICE, parameters);
		if (!response.isSuccess()) {
			logger.error("Token auth failed: {}", response.getBody());
			return null;
		}
		try {
			return gson.fromJson(
				response.getBody(),
				TokenValidationResult.class
			);
		} catch (JsonSyntaxException e) {
			String errMsg = String.format(
				"Failed to deserialize to TokenValidationResult: %s",
				response.getBody()
			);
			logger.error(errMsg, e);
			return null;
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
