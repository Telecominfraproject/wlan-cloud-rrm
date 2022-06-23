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

import com.facebook.openwifirrm.ucentral.gw.models.CommandInfo;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceCapabilities;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceConfigureRequest;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceListWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceWithStatus;
import com.facebook.openwifirrm.ucentral.gw.models.ServiceEvent;
import com.facebook.openwifirrm.ucentral.gw.models.StatisticsRecords;
import com.facebook.openwifirrm.ucentral.gw.models.SystemInfoResults;
import com.facebook.openwifirrm.ucentral.gw.models.WifiScanRequest;
import com.facebook.openwifirrm.ucentral.prov.models.VenueList;
import com.facebook.openwifirrm.RRMConfig.UCentralConfig.UCentralSocketParams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

	/** Socket parameters */
	private final UCentralSocketParams socketParams;

	private final Map<String, ServiceEvent> serviceEndpoints = new HashMap<>();
	private final String privateEndpoint;

	/**
	 * Constructor.
	 * @param privateEndpoint advertise the RRM private endpoint to the SDK
	 * @param socketParams Socket parameters
	 */
	public UCentralClient(
		String privateEndpoint,
		UCentralSocketParams socketParams
	) {
		this.privateEndpoint = privateEndpoint;
		this.socketParams = socketParams;
	}

	/** Return uCentral GW/Provisioning URL using the given endpoint. */
	private String makeUCentralUrl(String endpoint, String service) {
		ServiceEvent e = serviceEndpoints.get(service);
		if (e == null) {
			throw new RuntimeException("unknown uCentral URL");
		}
		String uCentralUrl = e.publicEndPoint; // TODO(andreilee): remove after development testing
		return String.format("%s/api/v1/%s", uCentralUrl, endpoint);
	}

	/**
	 * Check if the service has received service events for all service dependencies. The service
	 * events contain the API keys that the client uses to communicate with the services.
	 * */
	public boolean isInitialized(){
		return this.serviceEndpoints.containsKey(OWGW_SERVICE) &&
			this.serviceEndpoints.containsKey(OWSEC_SERVICE) &&
			this.serviceEndpoints.containsKey(OWPROV_SERVICE);
	}

	/** Send a GET request. */
	@SuppressWarnings("unused")
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
		String url = makeUCentralUrl(endpoint, service);
		GetRequest req = Unirest.get(url)
			.header("accept", "application/json")
			.header("X-API-KEY", this.getApiKey(OWGW_SERVICE))
			.header("X-INTERNAL-NAME", this.privateEndpoint)
			.header("Authorization", "Bearer 69e630fc1ff90cd10684f5272ecd404eb5238b8af86d85d1eedcf1fd5e8b4e54") // TODO(andreilee): remove after development testing
			.connectTimeout(connectTimeoutMs)
			.socketTimeout(socketTimeoutMs);
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
		String url = makeUCentralUrl(endpoint, service);
		HttpRequestWithBody req = Unirest.post(url)
			.header("accept", "application/json")
			.header("X-API-KEY", this.getApiKey(service))
			.header("X-INTERNAL-NAME", this.privateEndpoint)
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

	/** Launch a get venues command. */
	public VenueList getVenues() {
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

	/**
	 * System endpoints and API keys come from the service_event Kafka topic.
	 */
	public void setServiceEndpoint(String service, ServiceEvent event){
		this.serviceEndpoints.put(service, event);
	}

	/**
	 * Get the API key for a service
	 * @param service Service identifier. From the "type" field of service_events topic.
	 *   E.g.: owgw, owsec, ...
	 */
	private String getApiKey(String service){
		ServiceEvent s = this.serviceEndpoints.get(service);
		if (s == null) {
			logger.error("Error: API key not found for service: {}", service);
			return null;
		}
		return s.key;
	}
}
