/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.DeviceLayeredConfig;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ApiServerParams;
import com.facebook.openwifirrm.optimizers.ChannelOptimizer;
import com.facebook.openwifirrm.optimizers.LeastUsedChannelOptimizer;
import com.facebook.openwifirrm.optimizers.LocationBasedOptimalTPC;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApClientTPC;
import com.facebook.openwifirrm.optimizers.RandomChannelInitializer;
import com.facebook.openwifirrm.optimizers.RandomTxPowerInitializer;
import com.facebook.openwifirrm.optimizers.TPC;
import com.facebook.openwifirrm.optimizers.UnmanagedApAwareChannelOptimizer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import spark.Request;
import spark.Response;
import spark.Route;
import spark.Spark;

/**
 * HTTP API server.
 */
@OpenAPIDefinition(
	info = @Info(
		title = "OpenWiFi 2.0 RRM OpenAPI",
		version = "1.0.0",
		description = "This document describes the API for the Radio Resource Management service."
	),
	tags = {
		@Tag(name = "Config"),
		@Tag(name = "Optimization"),
	}
)
public class ApiServer implements Runnable {
	private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);

	/** The module parameters. */
	private final ApiServerParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The ConfigManager module instance. */
	private final ConfigManager configManager;

	/** The Modeler module instance. */
	private final Modeler modeler;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/** The cached OpenAPI instance. */
	private OpenAPI openApi;

	/** Constructor. */
	public ApiServer(
		ApiServerParams params,
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Modeler modeler
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
		this.configManager = configManager;
		this.modeler = modeler;
	}

	@Override
	public void run() {
		if (params.httpPort == -1) {
			logger.info("API server is disabled.");
			return;
		}

		Spark.port(params.httpPort);

		// Configure API docs hosting
		Spark.staticFiles.location("/public");
		Spark.get("/openapi.yaml", this::getOpenApiYaml);
		Spark.get("/openapi.json", this::getOpenApiJson);

		// Install routes
		Spark.before(this::beforeFilter);
		Spark.after(this::afterFilter);
		Spark.get("/api/v1/getTopology", new GetTopologyEndpoint());
		Spark.post("/api/v1/setTopology", new SetTopologyEndpoint());
		Spark.get(
			"/api/v1/getDeviceLayeredConfig",
			new GetDeviceLayeredConfigEndpoint()
		);
		Spark.get("/api/v1/getDeviceConfig", new GetDeviceConfigEndpoint());
		Spark.post(
			"/api/v1/setDeviceNetworkConfig",
			new SetDeviceNetworkConfigEndpoint()
		);
		Spark.post(
			"/api/v1/setDeviceZoneConfig", new SetDeviceZoneConfigEndpoint()
		);
		Spark.post(
			"/api/v1/setDeviceApConfig", new SetDeviceApConfigEndpoint()
		);
		Spark.post(
			"/api/v1/modifyDeviceApConfig", new ModifyDeviceApConfigEndpoint()
		);
		Spark.get("/api/v1/currentModel", new GetCurrentModelEndpoint());
		Spark.get("/api/v1/optimizeChannel", new OptimizeChannelEndpoint());
		Spark.get("/api/v1/optimizeTxPower", new OptimizeTxPowerEndpoint());

		logger.info("API server listening on HTTP port {}", params.httpPort);
		if (params.useBasicAuth) {
			logger.info("HTTP basic auth is enabled.");
		}
	}

	/** Stop the server. */
	public void shutdown() {
		Spark.stop();
	}

	/** Reconstructs a URL. */
	private String getFullUrl(String path, String queryString) {
		return (queryString == null)
			? path
			: String.format("%s?%s", path, queryString);
	}

	/**
	 * Perform HTTP basic authentication given an expected user/password.
	 *
	 * If authentication passes, do nothing and return true. Otherwise, send an
	 * HTTP 401 response with a "WWW-Authenticate" header and return false.
	 */
	private boolean performHttpBasicAuth(
		Request request, Response response, String user, String password
	) {
		// Extract header:
		//   Authorization: Basic <base64(<user>:<password>)>
		final String AUTH_PREFIX = "Basic ";
		String authHeader = request.headers("Authorization");
		if (authHeader != null && authHeader.startsWith(AUTH_PREFIX)) {
			String contents = authHeader.substring(AUTH_PREFIX.length());
			String creds = new String(Base64.getDecoder().decode(contents));
			int splitIdx = creds.indexOf(':');
			if (splitIdx != -1) {
				String u = creds.substring(0, splitIdx);
				String p = creds.substring(splitIdx + 1);
				if (u.equals(user) && p.equals(password)) {
					// auth success
					return true;
				}
			}
		}

		// auth failure
		response.header("WWW-Authenticate", "Basic");
		Spark.halt(401, "Unauthorized");
		return false;
	}

	/** Filter evaluated before each request. */
	private void beforeFilter(Request request, Response response) {
		// Log requests
		logger.debug(
			"[{}] {} {}",
			request.ip(),
			request.requestMethod(),
			getFullUrl(request.pathInfo(), request.queryString())
		);

		// Remove "Server: Jetty" header
		response.header("Server", "");

		// HTTP basic auth (if enabled)
		if (params.useBasicAuth) {
			performHttpBasicAuth(
				request,
				response,
				params.basicAuthUser,
				params.basicAuthPassword
			);
		}
	}

	/** Filter evaluated after each request. */
	private void afterFilter(Request request, Response response) {
		// Enable gzip if supported
		String acceptEncoding = request.headers("Accept-Encoding");
		if (acceptEncoding != null) {
			boolean gzipEnabled = Arrays
				.stream(acceptEncoding.split(","))
				.map(String::trim)
				.anyMatch(s -> s.equalsIgnoreCase("gzip"));
			if (gzipEnabled) {
				response.header("Content-Encoding", "gzip");
			}
		}
	}

	/** Returns the OpenAPI object, generating and caching it if needed. */
	private OpenAPI getOpenApi() {
		if (openApi == null) {
			// Find all annotated classes
			Reflections reflections = new Reflections(
				new ConfigurationBuilder().forPackages(this.getClass().getPackageName())
			);
			Set<Class<?>> apiClasses =
				reflections.getTypesAnnotatedWith(Path.class);
			apiClasses.add(this.getClass());

			// Scan annotations
			Reader reader = new Reader(new OpenAPI());
			this.openApi = reader.read(apiClasses);
		}
		return openApi;
	}

	/** Return an OpenAPI 3.0 YAML document. */
	private String getOpenApiYaml(Request request, Response response) {
		response.type(MediaType.TEXT_PLAIN);
		return Yaml.pretty(getOpenApi());
	}

	/** Return an OpenAPI 3.0 JSON document. */
	private String getOpenApiJson(Request request, Response response) {
		response.type(MediaType.APPLICATION_JSON);
		return Json.pretty(getOpenApi());
	}

	@Path("/api/v1/getTopology")
	public class GetTopologyEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get device topology",
			description = "Returns the device topology.",
			operationId = "getTopology",
			tags = {"Config"},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Device topology",
					content = @Content(
						schema = @Schema(implementation = DeviceTopology.class)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			response.type(MediaType.APPLICATION_JSON);
			return deviceDataManager.getTopologyJson();
		}
	}

	@Path("/api/v1/setTopology")
	public class SetTopologyEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Set device topology",
			description = "Set the device topology.",
			operationId = "setTopology",
			tags = {"Config"},
			requestBody = @RequestBody(
				description = "The device topology",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = DeviceTopology.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success"
				),
				@ApiResponse(
					responseCode = "400",
					description = "Bad request"
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			try {
				DeviceTopology topology =
					gson.fromJson(request.body(), DeviceTopology.class);
				deviceDataManager.setTopology(topology);

				// Revalidate data model
				modeler.revalidate();
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
			return "";
		}
	}

	@Path("/api/v1/getDeviceLayeredConfig")
	public class GetDeviceLayeredConfigEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get device layered configuration",
			description = "Returns the device layered configuration.",
			operationId = "getDeviceLayeredConfig",
			tags = {"Config"},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Device layered configuration",
					content = @Content(
						schema = @Schema(implementation = DeviceLayeredConfig.class)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			response.type(MediaType.APPLICATION_JSON);
			return deviceDataManager.getDeviceLayeredConfigJson();
		}
	}

	@Path("/api/v1/getDeviceConfig")
	public class GetDeviceConfigEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get device configuration",
			description = "Returns the device configuration by applying all configuration layers.",
			operationId = "getDeviceConfig",
			tags = {"Config"},
			parameters = {
				@Parameter(
					name = "serial",
					description = "The device serial number",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				)
			},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Device configuration",
					content = @Content(
						schema = @Schema(implementation = DeviceConfig.class)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String serialNumber = request.queryParams("serial");
			if (serialNumber == null || serialNumber.trim().isEmpty()) {
				response.status(400);
				return "Invalid serial number";
			}

			DeviceConfig config =
				deviceDataManager.getDeviceConfig(serialNumber);
			if (config == null) {
				response.status(400);
				return "Unknown device";
			}

			response.type(MediaType.APPLICATION_JSON);
			Gson gson = new GsonBuilder().serializeNulls().create();
			return gson.toJson(config);
		}
	}

	@Path("/api/v1/setDeviceNetworkConfig")
	public class SetDeviceNetworkConfigEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Set device network configuration",
			description = "Set the network layer of the device configuration.",
			operationId = "setDeviceNetworkConfig",
			tags = {"Config"},
			requestBody = @RequestBody(
				description = "The device network configuration",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = DeviceConfig.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success"
				),
				@ApiResponse(
					responseCode = "400",
					description = "Bad request"
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			try {
				DeviceConfig networkConfig =
					gson.fromJson(request.body(), DeviceConfig.class);
				deviceDataManager.setDeviceNetworkConfig(networkConfig);
				configManager.wakeUp();

				// Revalidate data model
				modeler.revalidate();
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
			return "";
		}
	}

	@Path("/api/v1/setDeviceZoneConfig")
	public class SetDeviceZoneConfigEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Set device zone configuration",
			description = "Set the zone layer of the network configuration for the given zone.",
			operationId = "setDeviceZoneConfig",
			tags = {"Config"},
			parameters = {
				@Parameter(
					name = "zone",
					description = "The RF zone",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				)
			},
			requestBody = @RequestBody(
				description = "The device zone configuration",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = DeviceConfig.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success"
				),
				@ApiResponse(
					responseCode = "400",
					description = "Bad request"
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String zone = request.queryParams("zone");
			if (zone == null || zone.trim().isEmpty()) {
				response.status(400);
				return "Invalid zone";
			}

			try {
				DeviceConfig zoneConfig =
					gson.fromJson(request.body(), DeviceConfig.class);
				deviceDataManager.setDeviceZoneConfig(zone, zoneConfig);
				configManager.wakeUp();

				// Revalidate data model
				modeler.revalidate();
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
			return "";
		}
	}

	@Path("/api/v1/setDeviceApConfig")
	public class SetDeviceApConfigEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Set device AP configuration",
			description = "Set the AP layer of the network configuration for the given AP.",
			operationId = "setDeviceApConfig",
			tags = {"Config"},
			parameters = {
				@Parameter(
					name = "serial",
					description = "The device serial number",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				)
			},
			requestBody = @RequestBody(
				description = "The device AP configuration",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = DeviceConfig.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success"
				),
				@ApiResponse(
					responseCode = "400",
					description = "Bad request"
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String serialNumber = request.queryParams("serial");
			if (serialNumber == null || serialNumber.trim().isEmpty()) {
				response.status(400);
				return "Invalid serial number";
			}

			try {
				DeviceConfig apConfig =
					gson.fromJson(request.body(), DeviceConfig.class);
				deviceDataManager.setDeviceApConfig(serialNumber, apConfig);
				configManager.wakeUp();

				// Revalidate data model
				modeler.revalidate();
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
			return "";
		}
	}

	@Path("/api/v1/modifyDeviceApConfig")
	public class ModifyDeviceApConfigEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Modify device AP configuration",
			description =
				"Modify the AP layer of the network configuration for the given AP. " +
				"Any existing fields absent from the request body will be preserved.",
			operationId = "modifyDeviceApConfig",
			tags = {"Config"},
			parameters = {
				@Parameter(
					name = "serial",
					description = "The device serial number",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				)
			},
			requestBody = @RequestBody(
				description = "The device AP configuration",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = DeviceConfig.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success"
				),
				@ApiResponse(
					responseCode = "400",
					description = "Bad request"
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String serialNumber = request.queryParams("serial");
			if (serialNumber == null || serialNumber.trim().isEmpty()) {
				response.status(400);
				return "Invalid serial number";
			}
			if (!deviceDataManager.isDeviceInTopology(serialNumber)) {
				response.status(400);
				return "Unknown serial number";
			}

			try {
				DeviceConfig apConfig =
					gson.fromJson(request.body(), DeviceConfig.class);
				if (apConfig.isEmpty()) {
					response.status(400);
					return "No supported fields in request body";
				}
				deviceDataManager.updateDeviceApConfig(configMap -> {
					configMap
						.computeIfAbsent(serialNumber, k -> new DeviceConfig())
						.apply(apConfig);
				});
				configManager.wakeUp();

				// Revalidate data model
				modeler.revalidate();
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
			return "";
		}
	}

	@Path("/api/v1/currentModel")
	public class GetCurrentModelEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get current RRM model",
			description = "Returns the current RRM data model.",
			operationId = "getCurrentModel",
			tags = {"Optimization"},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Data model",
					content = @Content(
						schema = @Schema(
							// TODO: can't use Modeler.DataModel because it has
							// gson class members that should not be reflected
							implementation = Object.class
						)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(modeler.getDataModel());
		}
	}

	@Path("/api/v1/optimizeChannel")
	public class OptimizeChannelEndpoint implements Route {
		// Hack for use in @ApiResponse -> @Content -> @Schema
		@SuppressWarnings("unused")
		private class ChannelAllocation {
			public Map<String, Map<String, Integer>> data;
			public ChannelAllocation(Map<String, Map<String, Integer>> channelMap) {
				this.data = channelMap;
			}
		}

		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Optimize channel configuration",
			description = "Run channel optimizer and return the new channel allocation.",
			operationId = "optimizeChannel",
			tags = {"Optimization"},
			parameters = {
				@Parameter(
					name = "mode",
					description = "The assignment algorithm to use:\n"
							+ "- random: random channel initialization\n"
							+ "- least_used: least used channel assignment\n"
							+ "- unmanaged_aware: unmanaged AP aware least used channel assignment\n",
					in = ParameterIn.QUERY,
					schema = @Schema(
						type = "string",
						allowableValues = {"random", "least_used", "unmanaged_aware"}
					),
					required = true
				),
				@Parameter(
					name = "zone",
					description = "The RF zone",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				),
				@Parameter(
					name = "dry_run",
					description = "Do not apply changes",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "boolean")
				)
			},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Channel allocation",
					content = @Content(
						schema = @Schema(
							implementation = ChannelAllocation.class
						)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String mode = request.queryParamOrDefault("mode", "");
			String zone = request.queryParamOrDefault("zone", "");
			boolean dryRun = request
				.queryParamOrDefault("dry_run", "")
				.equalsIgnoreCase("true");

			// Validate zone
			if (!deviceDataManager.isZoneInTopology(zone)) {
				response.status(400);
				return "Invalid zone";
			}

			// Get ChannelOptimizer implementation
			ChannelOptimizer optimizer;
			switch (mode) {
			case "random":
			    // Run random channel initializer
				optimizer = new RandomChannelInitializer(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			case "least_used":
				// Run least used channel optimizer
				optimizer = new LeastUsedChannelOptimizer(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			case "unmanaged_aware":
				// Run unmanaged AP aware least used channel optimizer
				optimizer = new UnmanagedApAwareChannelOptimizer(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			default:
				response.status(400);
				return "Invalid mode";
			}

			// Compute channel map
			Map<String, Map<String, Integer>> channelMap =
				optimizer.computeChannelMap();
			if (!dryRun) {
				optimizer.applyConfig(
					deviceDataManager, configManager, channelMap
				);
			}

			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(new ChannelAllocation(channelMap));
		}
	}

	@Path("/api/v1/optimizeTxPower")
	public class OptimizeTxPowerEndpoint implements Route {
		// Hack for use in @ApiResponse -> @Content -> @Schema
		@SuppressWarnings("unused")
		private class TxPowerAllocation {
			public Map<String, Map<String, Integer>> data;
			public TxPowerAllocation(Map<String, Map<String, Integer>> txPowerMap) {
				this.data = txPowerMap;
			}
		}

		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Optimize tx power configuration",
			description = "Run tx power optimizer and return the new tx power allocation.",
			operationId = "optimizeTxPower",
			tags = {"Optimization"},
			parameters = {
				@Parameter(
					name = "mode",
					description = "The assignment algorithm to use:\n"
							+ "- random: random tx power initialier\n"
							+ "- measure_ap_client: measurement-based AP-client TPC algorithm\n"
							+ "- measure_ap_ap: measurement-based AP-AP TPC algorithm\n"
							+ "- location_optimal: location-based optimal TPC algorithm\n",
					in = ParameterIn.QUERY,
					schema = @Schema(
						type = "string",
						allowableValues = {"random", "measure_ap_client", "measure_ap_ap", "location_optimal"}
					),
					required = true
				),
				@Parameter(
					name = "zone",
					description = "The RF zone",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				),
				@Parameter(
					name = "dry_run",
					description = "Do not apply changes",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "boolean")
				)
			},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Tx power allocation",
					content = @Content(
						schema = @Schema(
							implementation = TxPowerAllocation.class
						)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String mode = request.queryParamOrDefault("mode", "");
			String zone = request.queryParamOrDefault("zone", "");
			boolean dryRun = request
				.queryParamOrDefault("dry_run", "")
				.equalsIgnoreCase("true");

			// Validate zone
			if (!deviceDataManager.isZoneInTopology(zone)) {
				response.status(400);
				return "Invalid zone";
			}

			// Get TPC implementation
			TPC optimizer;
			switch (mode) {
			case "random":
			    // Run random tx power initialization
				optimizer = new RandomTxPowerInitializer(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			case "measure_ap_client":
				// Run measurement-based AP-client tx power optimization
				optimizer = new MeasurementBasedApClientTPC(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			case "measure_ap_ap":
				// Run measurement-based AP-AP tx power optimization
				optimizer = new MeasurementBasedApApTPC(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			case "location_optimal":
				// Run location-based optimal tx power optimization
				optimizer = new LocationBasedOptimalTPC(
					modeler.getDataModelCopy(), zone, deviceDataManager
				);
				break;
			default:
				response.status(400);
				return "Invalid mode";
			}

			// Compute tx power map
			Map<String, Map<String, Integer>> txPowerMap =
				optimizer.computeTxPowerMap();
			if (!dryRun) {
				optimizer.applyConfig(
					deviceDataManager, configManager, txPowerMap
				);
			}

			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(new TxPowerAllocation(txPowerMap));
		}
	}
}
