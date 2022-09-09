/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.JSONObject;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.DeviceLayeredConfig;
import com.facebook.openwifirrm.DeviceTopology;
import com.facebook.openwifirrm.RRMAlgorithm;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ApiServerParams;
import com.facebook.openwifirrm.RRMConfig.ServiceConfig;
import com.facebook.openwifirrm.RRMSchedule;
import com.facebook.openwifirrm.Utils.LruCache;
import com.facebook.openwifirrm.VersionProvider;
import com.facebook.openwifirrm.optimizers.channel.LeastUsedChannelOptimizer;
import com.facebook.openwifirrm.optimizers.channel.RandomChannelInitializer;
import com.facebook.openwifirrm.optimizers.channel.UnmanagedApAwareChannelOptimizer;
import com.facebook.openwifirrm.optimizers.tpc.LocationBasedOptimalTPC;
import com.facebook.openwifirrm.optimizers.tpc.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.tpc.MeasurementBasedApClientTPC;
import com.facebook.openwifirrm.optimizers.tpc.RandomTxPowerInitializer;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.gw.models.SystemInfoResults;
import com.facebook.openwifirrm.ucentral.gw.models.TokenValidationResult;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTag;
import com.facebook.openwifirrm.ucentral.prov.models.InventoryTagList;
import com.facebook.openwifirrm.ucentral.prov.models.RRMAlgorithmDetails;
import com.facebook.openwifirrm.ucentral.prov.models.RRMDetails;
import com.facebook.openwifirrm.ucentral.prov.models.SerialNumberList;
import com.facebook.openwifirrm.ucentral.prov.rrm.models.Algorithm;
import com.facebook.openwifirrm.ucentral.prov.rrm.models.Provider;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
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
		version = "2.7.0", // NOTE: needs manual update!
		description = "This document describes the API for the Radio Resource Management service."
	),
	tags = {
		@Tag(name = "SDK"),
		@Tag(name = "Config"),
		@Tag(name = "Optimization"),
	},
	security = {
		@SecurityRequirement(name = "bearerAuth"),
	}
)
@SecurityScheme(
	name = "bearerAuth",
	type = SecuritySchemeType.HTTP,
	scheme = "bearer"
)
public class ApiServer implements Runnable {
	private static final Logger logger =
		LoggerFactory.getLogger(ApiServer.class);

	/** The module parameters. */
	private final ApiServerParams params;

	/** The service config. */
	private final ServiceConfig serviceConfig;

	/** The service key. */
	private final String serviceKey;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The ConfigManager module instance. */
	private final ConfigManager configManager;

	/** The Modeler module instance. */
	private final Modeler modeler;

	/** The uCentral Client instance. */
	private final UCentralClient client;

	/** The RRM scheduler. */
	private final RRMScheduler scheduler;

	/**
	 * The auth token cache (map from token to expiration time, in UNIX seconds)
	 * for incoming requests.
	 *
	 * @see #performOpenWifiAuth(Request, Response)
	 */
	private final Map<String, Long> tokenCache;

	/** The list of allowed CORS domains. If null, all domains are allowed. */
	private final Set<String> corsDomains;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/** The cached OpenAPI instance. */
	private OpenAPI openApi;

	/** The module start time (real time), in ms. */
	private long startTimeMs;

	/** Constructor. */
	public ApiServer(
		ApiServerParams params,
		ServiceConfig serviceConfig,
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Modeler modeler,
		UCentralClient client,
		RRMScheduler scheduler
	) {
		this.params = params;
		this.serviceConfig = serviceConfig;
		this.serviceKey = UCentralUtils.generateServiceKey(serviceConfig);
		this.deviceDataManager = deviceDataManager;
		this.configManager = configManager;
		this.modeler = modeler;
		this.client = client;
		this.scheduler = scheduler;
		this.tokenCache = Collections.synchronizedMap(
			new LruCache<>(Math.max(params.openWifiAuthCacheSize, 1))
		);
		this.corsDomains = getCorsDomains(params.corsDomainList);
	}

	/**
	 * Build the CORS domain set.
	 *
	 * @see ApiServerParams#corsDomainList
	 */
	private Set<String> getCorsDomains(String corsDomainList) {
		if (corsDomainList.isEmpty()) {
			return null;
		}
		Set<String> ret = ConcurrentHashMap.newKeySet();
		for (String domain : corsDomainList.split(",")) {
			ret.add(domain);
		}
		return ret;
	}

	@Override
	public void run() {
		this.startTimeMs = System.currentTimeMillis();

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
		Spark.options("/*", this::options);
		Spark.get("/api/v1/system", new SystemEndpoint());
		Spark.post("/api/v1/system", new SetSystemEndpoint());
		Spark.get("/api/v1/provider", new ProviderEndpoint());
		Spark.get("/api/v1/algorithms", new AlgorithmsEndpoint());
		Spark.put("/api/v1/runRRM", new RunRRMEndpoint());
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
			"/api/v1/setDeviceZoneConfig",
			new SetDeviceZoneConfigEndpoint()
		);
		Spark.post(
			"/api/v1/setDeviceApConfig",
			new SetDeviceApConfigEndpoint()
		);
		Spark.post(
			"/api/v1/modifyDeviceApConfig",
			new ModifyDeviceApConfigEndpoint()
		);
		Spark.get("/api/v1/currentModel", new GetCurrentModelEndpoint());
		Spark.get("/api/v1/optimizeChannel", new OptimizeChannelEndpoint());
		Spark.get("/api/v1/optimizeTxPower", new OptimizeTxPowerEndpoint());

		logger.info("API server listening on HTTP port {}", params.httpPort);
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
	 * Perform OpenWiFi authentication via tokens (external) and API keys
	 * (internal).
	 *
	 * If authentication passes, do nothing and return true. Otherwise, send an
	 * HTTP 403 response and return false.
	 */
	private boolean performOpenWifiAuth(Request request, Response response) {
		// TODO check if request came from internal endpoint
		boolean internal = true;
		String internalName = request.headers("X-INTERNAL-NAME");
		if (internal && internalName != null) {
			// Internal request, validate "X-API-KEY"
			String apiKey = request.headers("X-API-KEY");
			if (apiKey != null && apiKey.equals(serviceKey)) {
				// auth success
				return true;
			}
		} else {
			// External request, validate token:
			//   Authorization: Bearer <token>
			final String AUTH_PREFIX = "Bearer ";
			String authHeader = request.headers("Authorization");
			if (authHeader != null && authHeader.startsWith(AUTH_PREFIX)) {
				String token = authHeader.substring(AUTH_PREFIX.length());
				if (!token.isEmpty()) {
					boolean valid = validateOpenWifiToken(token);
					if (valid) {
						// auth success
						return true;
					}
				}
			}
		}

		// auth failure
		Spark.halt(403, "Forbidden");
		return false;
	}

	/**
	 * Validate an OpenWiFi token (external), caching successful lookups.
	 * @return true if token is valid
	 */
	private boolean validateOpenWifiToken(String token) {
		// The below only checks /api/v1/validateToken and caches it as necessary.
		// TODO - /api/v1/validateSubToken still has to be implemented.
		Long expiry = tokenCache.get(token);
		if (expiry == null) {
			TokenValidationResult result = client.validateToken(token);
			if (result == null) {
				return false;
			}
			expiry = result.tokenInfo.created + result.tokenInfo.expires_in;
			tokenCache.put(token, expiry);
		}
		return expiry > Instant.now().getEpochSecond();
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

		// Set CORS headers
		String origin = request.headers("Origin");
		if (origin != null) {
			boolean allowOrigin = false;
			if (corsDomains == null) {
				allowOrigin = true;
			} else {
				try {
					String originHost = new URI(origin).getHost();
					allowOrigin = corsDomains.contains(originHost);
				} catch (URISyntaxException e) { /* invalid origin URI */ }
			}
			if (allowOrigin) {
				response.header("Access-Control-Allow-Origin", origin);
				response.header("Vary", "Origin");
			} else {
				logger.debug("CORS checks failed for origin: {}", origin);
			}
		}

		// OpenWifi auth (if enabled)
		if (params.useOpenWifiAuth) {
			// Only protect API endpoints
			if (request.pathInfo().startsWith("/api/")) {
				this.performOpenWifiAuth(request, response);
			}
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

	/** Global OPTIONS handler. */
	private String options(Request request, Response response) {
		// Set CORS headers
		String acrh = request.headers("Access-Control-Request-Headers");
		if (acrh != null) {
			response.header("Access-Control-Allow-Headers", acrh);
		}
		String acrm = request.headers("Access-Control-Request-Method");
		if (acrm != null) {
			response.header("Access-Control-Allow-Methods", acrm);
		}
		response.header("Access-Control-Allow-Credentials", "true");
		response.header("Access-Control-Max-Age", "86400" /* 1 day */);
		return "OK";
	}

	/** Returns the OpenAPI object, generating and caching it if needed. */
	private OpenAPI getOpenApi() {
		if (openApi == null) {
			// Find all annotated classes
			Reflections reflections = new Reflections(
				new ConfigurationBuilder()
					.forPackages(this.getClass().getPackageName())
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

	/** TODO [ZoneBasedRrmScheduling] remove this once we move to venues
	 * Calls prov to get a mapping of all RRM enabled devices to their settings
	 * and updates the device config.
	 */
	private void getAndUpdateRRMDetails() {
		InventoryTagList inventory = client.getProvInventory();
		SerialNumberList inventoryForRRM = client.getProvInventoryForRRM();
		if (inventory == null || inventoryForRRM == null) {
			return;
		}

		Map<String, RRMDetails> rrmDetails = new HashMap<String, RRMDetails>();
		for (String serialNumber : inventoryForRRM.serialNumbers) {
			RRMDetails details =
				client.getProvInventoryRrmDetails(serialNumber);
			if (details == null) {
				logger
					.error("Could not fetch RRM details for {}", serialNumber);
				continue;
			}

			rrmDetails.put(serialNumber, details);
		}

		deviceDataManager.updateDeviceApConfig(
			configMap -> {
				// Pass 1: disable RRM on all devices
				for (InventoryTag tag : inventory.taglist) {
					DeviceConfig cfg = configMap.computeIfAbsent(
						tag.serialNumber,
						k -> new DeviceConfig()
					);
					cfg.enableRRM =
						cfg.enableConfig = cfg.enableWifiScan = false;
				}

				// Pass 2: re-enable RRM on specific devices
				for (String serialNumber : inventoryForRRM.serialNumbers) {
					DeviceConfig cfg = configMap.computeIfAbsent(
						serialNumber,
						k -> new DeviceConfig()
					);
					cfg.enableRRM =
						cfg.enableConfig = cfg.enableWifiScan = true;

					// read the details from the config
					RRMDetails details = rrmDetails.get(serialNumber);
					if (details == null) {
						logger.error(
							"No RRM details available for {} even though it has RRM enabled",
							serialNumber
						);
						continue;
					}

					cfg.schedule = new RRMSchedule();
					if (details.rrm != null) {
						cfg.schedule.cron = details.rrm.schedule;

						if (details.rrm.algorithms != null) {
							cfg.schedule.algorithms =
								new ArrayList<RRMAlgorithm>();
							for (
								RRMAlgorithmDetails algorithmDetails : details.rrm.algorithms
							) {
								cfg.schedule.algorithms.add(
									RRMAlgorithm.parse(
										algorithmDetails.name,
										algorithmDetails.parameters
									)
								);
							}
						}
						logger.info("Set RRM settings for {}", serialNumber);
					} else {
						logger
							.error("RRM setting for {} is null", serialNumber);
					}
				}
			}
		);
	}

	@Path("/api/v1/system")
	public class SystemEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get system info",
			description = "Returns the system info from the running service.",
			operationId = "system",
			tags = { "SDK" },
			parameters = {
				@Parameter(
					name = "command",
					description = "Get a value",
					in = ParameterIn.QUERY,
					schema = @Schema(
						type = "string",
						allowableValues = { "info" }
					),
					required = true
				)
			},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success",
					content = @Content(
						schema = @Schema(
							implementation = SystemInfoResults.class
						)
					)
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String command = request.queryParams("command");
			if (command == null || !command.equals("info")) {
				response.status(400);
				return "Invalid command";
			}

			SystemInfoResults result = new SystemInfoResults();
			result.version = VersionProvider.get();
			result.uptime =
				Math.max(System.currentTimeMillis() - startTimeMs, 0) / 1000L;
			result.start = startTimeMs / 1000L;
			result.os = System.getProperty("os.name");
			result.processors = Runtime.getRuntime().availableProcessors();
			try {
				result.hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				logger.error("Unable to get hostname", e);
			}
			// TODO certificates

			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(result);
		}
	}

	@Path("/api/v1/system")
	public class SetSystemEndpoint implements Route {
		@POST
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Run system commands",
			description = "Perform some system-wide commands.",
			operationId = "setSystem",
			tags = { "SDK" },
			requestBody = @RequestBody(
				description = "Command details",
				content = {
					@Content(
						mediaType = "application/json",
						schema = @Schema(implementation = Object.class)
					)
				},
				required = true
			),
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Successful command execution",
					content = @Content(
						// TODO: Provide a specific class as value of Schema.implementation
						schema = @Schema(implementation = Object.class)
					)
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			try {
				JSONObject jsonObj = new JSONObject(request.body());
				String command = jsonObj.get("command").toString();
				switch (command) {
				case "setloglevel":
				case "reload":
				case "getloglevels":
				case "getloglevelnames":
				case "getsubsystemnames":
					return "[]";
				default:
					response.status(400);
					return "Invalid command";
				}
			} catch (Exception e) {
				response.status(400);
				return e.getMessage();
			}
		}
	}

	@Path("/api/v1/provider")
	public class ProviderEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get RRM provider info",
			description = "Returns the RRM provider info.",
			operationId = "provider",
			tags = { "SDK" },
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success",
					content = @Content(
						schema = @Schema(implementation = Provider.class)
					)
				)
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			Provider provider = new Provider();
			provider.vendor = serviceConfig.vendor;
			provider.vendorShortname =
				serviceConfig.vendor.replaceAll("[^A-Za-z0-9]", "");
			provider.version = VersionProvider.get();
			provider.about = serviceConfig.vendorUrl;

			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(provider);
		}
	}

	@Path("/api/v1/algorithms")
	public class AlgorithmsEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get RRM algorithms",
			description = "Returns the RRM algorithm list.",
			operationId = "algorithms",
			tags = { "SDK" },
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success",
					content = @Content(
						array = @ArraySchema(
							schema = @Schema(implementation = Algorithm.class)
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
			List<Algorithm> algorithms = Arrays
				.stream(RRMAlgorithm.AlgorithmType.values())
				.map(algo -> {
					Algorithm a = new Algorithm();
					a.name = algo.longName;
					a.description = algo.description;
					a.shortName = algo.name();
					// comma-separated key=value pairs, see RRMAlgorithm.parse()
					a.parameterFormat =
						"((([^,=])+)=(([^,=])+))(,(([^,=])+)=(([^,=])+))*";
					a.parameterSamples =
						Arrays.asList("mode=random", "key1=val1,key2=val2");
					a.helper = serviceConfig.vendorReferenceUrl;
					return a;
				})
				.collect(Collectors.toList());

			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(algorithms);
		}
	}

	@Path("/api/v1/runRRM")
	public class RunRRMEndpoint implements Route {
		@PUT
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Run RRM algorithm",
			description = "Run a specific RRM algorithm now.",
			operationId = "runRRM",
			tags = { "SDK" },
			parameters = {
				@Parameter(
					name = "algorithm",
					description = "The algorithm name",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				),
				@Parameter(
					name = "args",
					description = "The algorithm arguments",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string")
				),
				@Parameter(
					name = "venue",
					description = "The RF zone",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "string"),
					required = true
				),
				@Parameter(
					name = "mock",
					description = "Do not apply changes",
					in = ParameterIn.QUERY,
					schema = @Schema(type = "boolean")
				)
			},
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Success",
					content = @Content(
						schema = @Schema(
							implementation = RRMAlgorithm.AlgorithmResult.class
						)
					)
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			String algorithm = request.queryParamOrDefault("algorithm", "");
			String args = request.queryParamOrDefault("args", "");
			String venue = request.queryParamOrDefault("venue", "");
			boolean mock = request
				.queryParamOrDefault("mock", "")
				.equalsIgnoreCase("true");

			// Validate zone
			if (!deviceDataManager.isZoneInTopology(venue)) {
				response.status(400);
				return "Invalid venue";
			}

			// Validate inputs
			RRMAlgorithm algo = RRMAlgorithm.parse(algorithm, args);
			if (algo == null) {
				response.status(400);
				return "Invalid inputs";
			}

			// Run algorithm
			RRMAlgorithm.AlgorithmResult result = algo.run(
				deviceDataManager,
				configManager,
				modeler,
				venue,
				mock,
				true /* allowDefaultMode */
			);
			if (result.error != null) {
				response.status(400);
				return result.error;
			}
			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(result);
		}
	}

	@Path("/api/v1/getTopology")
	public class GetTopologyEndpoint implements Route {
		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Get device topology",
			description = "Returns the device topology.",
			operationId = "getTopology",
			tags = { "Config" },
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
			tags = { "Config" },
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
				@ApiResponse(responseCode = "200", description = "Success"),
				@ApiResponse(responseCode = "400", description = "Bad Request")
			}
		)
		@Override
		public String handle(
			@Parameter(hidden = true) Request request,
			@Parameter(hidden = true) Response response
		) {
			// TODO - block if "ProvMonitorParams.useVenues" is enabled?
			try {
				DeviceTopology topology =
					gson.fromJson(request.body(), DeviceTopology.class);
				deviceDataManager.setTopology(topology);

				// Revalidate data model
				modeler.revalidate();

				// Update scheduler

				// TODO [ZoneBasedRrmScheduling] reeanable after venue based configs
				// scheduler.syncTriggers();
				getAndUpdateRRMDetails();
				scheduler.syncTriggersForDevices();
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
			tags = { "Config" },
			responses = {
				@ApiResponse(
					responseCode = "200",
					description = "Device layered configuration",
					content = @Content(
						schema = @Schema(
							implementation = DeviceLayeredConfig.class
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
			tags = { "Config" },
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
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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
			tags = { "Config" },
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
				@ApiResponse(responseCode = "200", description = "Success"),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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

				// Update scheduler
				// TODO [ZoneBasedRrmScheduling] reeanable after venue based configs
				// scheduler.syncTriggers();
				getAndUpdateRRMDetails();
				scheduler.syncTriggersForDevices();
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
			tags = { "Config" },
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
				@ApiResponse(responseCode = "200", description = "Success"),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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

				// Update scheduler
				// TODO [ZoneBasedRrmScheduling] reeanable after venue based configs
				// scheduler.syncTriggers();
				getAndUpdateRRMDetails();
				scheduler.syncTriggersForDevices();
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
			tags = { "Config" },
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
				@ApiResponse(responseCode = "200", description = "Success"),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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
			description = "Modify the AP layer of the network configuration for the given AP. " +
				"Any existing fields absent from the request body will be preserved.",
			operationId = "modifyDeviceApConfig",
			tags = { "Config" },
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
				@ApiResponse(responseCode = "200", description = "Success"),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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
			tags = { "Optimization" },
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

			public ChannelAllocation(
				Map<String, Map<String, Integer>> channelMap
			) {
				this.data = channelMap;
			}
		}

		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Optimize channel configuration",
			description = "Run channel optimizer and return the new channel allocation.",
			operationId = "optimizeChannel",
			tags = { "Optimization" },
			parameters = {
				@Parameter(
					name = "mode",
					// @formatter:off
					description = "The assignment algorithm to use:\n" +
							"- " + RandomChannelInitializer.ALGORITHM_ID + ": random channel initialization\n" +
							"- " + LeastUsedChannelOptimizer.ALGORITHM_ID + ": least used channel assignment\n" +
							"- " + UnmanagedApAwareChannelOptimizer.ALGORITHM_ID + ": unmanaged AP aware least used channel assignment\n",
					// @formatter:on
					in = ParameterIn.QUERY,
					schema = @Schema(
						type = "string",
						allowableValues = {
							RandomChannelInitializer.ALGORITHM_ID,
							LeastUsedChannelOptimizer.ALGORITHM_ID,
							UnmanagedApAwareChannelOptimizer.ALGORITHM_ID,
						}
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
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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

			// Run algorithm
			Map<String, String> args = new HashMap<>();
			args.put("mode", mode);
			RRMAlgorithm algo = new RRMAlgorithm(
				RRMAlgorithm.AlgorithmType.OptimizeChannel.name(),
				args
			);
			RRMAlgorithm.AlgorithmResult result = algo.run(
				deviceDataManager,
				configManager,
				modeler,
				zone,
				dryRun,
				false /* allowDefaultMode */
			);
			if (result.error != null) {
				response.status(400);
				return result.error;
			}
			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(new ChannelAllocation(result.channelMap));
		}
	}

	@Path("/api/v1/optimizeTxPower")
	public class OptimizeTxPowerEndpoint implements Route {
		// Hack for use in @ApiResponse -> @Content -> @Schema
		@SuppressWarnings("unused")
		private class TxPowerAllocation {
			public Map<String, Map<String, Integer>> data;

			public TxPowerAllocation(
				Map<String, Map<String, Integer>> txPowerMap
			) {
				this.data = txPowerMap;
			}
		}

		@GET
		@Produces({ MediaType.APPLICATION_JSON })
		@Operation(
			summary = "Optimize tx power configuration",
			description = "Run tx power optimizer and return the new tx power allocation.",
			operationId = "optimizeTxPower",
			tags = { "Optimization" },
			parameters = {
				@Parameter(
					name = "mode",
					// @formatter:off
					description = "The assignment algorithm to use:\n" +
							"- " + RandomTxPowerInitializer.ALGORITHM_ID + ": random tx power initializer\n" +
							"- " + MeasurementBasedApClientTPC.ALGORITHM_ID + ": measurement-based AP-client TPC algorithm\n" +
							"- " + MeasurementBasedApApTPC.ALGORITHM_ID + ": measurement-based AP-AP TPC algorithm\n" +
							"- " + LocationBasedOptimalTPC.ALGORITHM_ID + ": location-based optimal TPC algorithm\n",
					// @formatter:on
					in = ParameterIn.QUERY,
					schema = @Schema(
						type = "string",
						allowableValues = {
							RandomTxPowerInitializer.ALGORITHM_ID,
							MeasurementBasedApClientTPC.ALGORITHM_ID,
							MeasurementBasedApApTPC.ALGORITHM_ID,
							LocationBasedOptimalTPC.ALGORITHM_ID,
						}
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
				),
				@ApiResponse(responseCode = "400", description = "Bad Request")
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

			// Run algorithm
			Map<String, String> args = new HashMap<>();
			args.put("mode", mode);
			RRMAlgorithm algo = new RRMAlgorithm(
				RRMAlgorithm.AlgorithmType.OptimizeTxPower.name(),
				args
			);
			RRMAlgorithm.AlgorithmResult result = algo.run(
				deviceDataManager,
				configManager,
				modeler,
				zone,
				dryRun,
				false /* allowDefaultMode */
			);
			if (result.error != null) {
				response.status(400);
				return result.error;
			}
			response.type(MediaType.APPLICATION_JSON);
			return gson.toJson(new TxPowerAllocation(result.txPowerMap));
		}
	}
}
