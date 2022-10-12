/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.DeviceLayeredConfig;
import com.facebook.openwifi.rrm.DeviceTopology;
import com.facebook.openwifi.rrm.RRMAlgorithm;
import com.facebook.openwifi.rrm.RRMConfig;
import com.facebook.openwifi.rrm.VersionProvider;
import com.facebook.openwifi.rrm.mysql.DatabaseManager;
import com.facebook.openwifi.rrm.ucentral.UCentralClient;
import com.facebook.openwifi.rrm.ucentral.UCentralConstants;
import com.facebook.openwifi.rrm.ucentral.UCentralKafkaConsumer;
import com.google.gson.Gson;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.json.JSONObject;
import spark.Spark;

@TestMethodOrder(OrderAnnotation.class)
public class ApiServerTest {
	/** The test server port. */
	private static final int TEST_PORT = spark.Service.SPARK_DEFAULT_PORT;

	/** Test device data manager. */
	private DeviceDataManager deviceDataManager;

	/** Test RRM config. */
	private RRMConfig rrmConfig;

	/** Test API server instance. */
	private ApiServer server;

	/** Test modeler instance. */
	private Modeler modeler;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/** Build an endpoint URL. */
	private String endpoint(String path) {
		return String.format("http://localhost:%d%s", TEST_PORT, path);
	}

	@BeforeEach
	void setup(TestInfo testInfo) {
		this.deviceDataManager = new DeviceDataManager();

		// Create config
		this.rrmConfig = new RRMConfig();
		rrmConfig.moduleConfig.apiServerParams.httpPort = TEST_PORT;

		// Create clients (null for now)
		UCentralClient client = null;
		UCentralKafkaConsumer consumer = null;
		DatabaseManager dbManager = null;

		// Create scheduler
		RRMScheduler scheduler = new RRMScheduler(
			rrmConfig.moduleConfig.schedulerParams,
			deviceDataManager
		);

		// Instantiate dependent instances
		ConfigManager configManager = new ConfigManager(
			rrmConfig.moduleConfig.configManagerParams,
			deviceDataManager,
			client
		);
		DataCollector dataCollector = new DataCollector(
			rrmConfig.moduleConfig.dataCollectorParams,
			deviceDataManager,
			client,
			consumer,
			configManager,
			dbManager
		);
		this.modeler = new Modeler(
			rrmConfig.moduleConfig.modelerParams,
			deviceDataManager,
			consumer,
			client,
			dataCollector,
			configManager
		);

		// Instantiate ApiServer
		this.server = new ApiServer(
			rrmConfig.moduleConfig.apiServerParams,
			rrmConfig.serviceConfig,
			deviceDataManager,
			configManager,
			modeler,
			client,
			scheduler
		);
		try {
			server.run();
			Spark.awaitInitialization();
		} catch (Exception e) {
			fail("Could not instantiate ApiServer.", e);
		}
	}

	@AfterEach
	void tearDown() {
		// Destroy ApiServer
		if (server != null) {
			server.shutdown();
			Spark.awaitStop();
		}
		server = null;

		// Reset Unirest client
		// Without this, Unirest randomly throws:
		// kong.unirest.UnirestException: java.net.SocketException: Software caused connection abort: recv failed
		Unirest.shutDown();
	}

	@Test
	@Order(1)
	void test_getTopology() throws Exception {
		// Create topology
		DeviceTopology topology = new DeviceTopology();
		topology.put("test-zone", new TreeSet<>(Arrays.asList("aaaaaaaaaaa")));
		deviceDataManager.setTopology(topology);

		// Fetch topology
		HttpResponse<String> resp =
			Unirest.get(endpoint("/api/v1/getTopology")).asString();
		assertEquals(200, resp.getStatus());
		assertEquals(deviceDataManager.getTopologyJson(), resp.getBody());
	}

	@Test
	@Order(2)
	void test_setTopology() throws Exception {
		String url = endpoint("/api/v1/setTopology");

		// Create topology
		DeviceTopology topology = new DeviceTopology();
		topology.put("test-zone", new TreeSet<>(Arrays.asList("aaaaaaaaaaa")));

		// Set topology
		HttpResponse<String> resp = Unirest
			.post(url)
			.body(gson.toJson(topology))
			.asString();
		assertEquals(200, resp.getStatus());
		assertEquals(
			gson.toJson(topology),
			deviceDataManager.getTopologyJson()
		);

		// Missing/wrong parameters
		assertEquals(
			400,
			Unirest.post(url).body("not json").asString().getStatus()
		);
	}

	@Test
	@Order(3)
	void test_getDeviceLayeredConfig() throws Exception {
		// Create topology and configs
		final String zone = "test-zone";
		final String ap = "aaaaaaaaaaa";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(ap)));
		deviceDataManager.setTopology(topology);
		DeviceLayeredConfig cfg = new DeviceLayeredConfig();
		cfg.networkConfig.enableRRM = true;
		DeviceConfig zoneConfig = new DeviceConfig();
		zoneConfig.enableWifiScan = false;
		cfg.zoneConfig.put(zone, zoneConfig);
		DeviceConfig apConfig = new DeviceConfig();
		apConfig.enableConfig = false;
		cfg.apConfig.put(ap, apConfig);
		deviceDataManager.setDeviceApConfig(ap, apConfig);

		// Fetch config
		HttpResponse<String> resp =
			Unirest.get(endpoint("/api/v1/getDeviceLayeredConfig")).asString();
		assertEquals(200, resp.getStatus());
		assertEquals(
			deviceDataManager.getDeviceLayeredConfigJson(),
			resp.getBody()
		);
	}

	@Test
	@Order(4)
	void test_getDeviceConfig() throws Exception {
		String url = endpoint("/api/v1/getDeviceConfig");

		// Create topology
		final String zone = "test-zone";
		final String ap = "aaaaaaaaaaa";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(ap)));
		deviceDataManager.setTopology(topology);

		// Fetch config
		HttpResponse<String> resp =
			Unirest.get(url + "?serial=" + ap).asString();
		assertEquals(200, resp.getStatus());
		String normalizedResp =
			gson.toJson(gson.fromJson(resp.getBody(), DeviceConfig.class));
		assertEquals(
			gson.toJson(deviceDataManager.getDeviceConfig(ap)),
			normalizedResp
		);

		// Missing/wrong parameters
		assertEquals(400, Unirest.get(url).asString().getStatus());
		assertEquals(
			400,
			Unirest.get(url + "?serial=asdf").asString().getStatus()
		);
	}

	@Test
	@Order(5)
	void test_setDeviceNetworkConfig() throws Exception {
		DeviceConfig config = new DeviceConfig();
		config.enableConfig = false;

		// Set config
		HttpResponse<String> resp = Unirest
			.post(endpoint("/api/v1/setDeviceNetworkConfig"))
			.body(gson.toJson(config))
			.asString();
		assertEquals(200, resp.getStatus());
		DeviceLayeredConfig fullCfg = gson.fromJson(
			deviceDataManager.getDeviceLayeredConfigJson(),
			DeviceLayeredConfig.class
		);
		assertEquals(gson.toJson(config), gson.toJson(fullCfg.networkConfig));
	}

	@Test
	@Order(6)
	void test_setDeviceZoneConfig() throws Exception {
		String url = endpoint("/api/v1/setDeviceZoneConfig");

		// Create topology
		final String zone = "test-zone";
		final String ap = "aaaaaaaaaaa";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(ap)));
		deviceDataManager.setTopology(topology);

		DeviceConfig config = new DeviceConfig();
		config.enableConfig = false;

		// Set config
		HttpResponse<String> resp = Unirest
			.post(url + "?zone=" + zone)
			.body(gson.toJson(config))
			.asString();
		assertEquals(200, resp.getStatus());
		DeviceLayeredConfig fullCfg = gson.fromJson(
			deviceDataManager.getDeviceLayeredConfigJson(),
			DeviceLayeredConfig.class
		);
		assertEquals(1, fullCfg.zoneConfig.size());
		assertTrue(fullCfg.zoneConfig.containsKey(zone));
		assertEquals(
			gson.toJson(config),
			gson.toJson(fullCfg.zoneConfig.get(zone))
		);

		// Missing/wrong parameters
		assertEquals(
			400,
			Unirest.post(url).body(gson.toJson(config)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?zone=asdf").body(gson.toJson(config)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?zone=" + zone).body("not json").asString().getStatus()
		);
	}

	@Test
	@Order(7)
	void test_setDeviceApConfig() throws Exception {
		String url = endpoint("/api/v1/setDeviceApConfig");

		// Create topology
		final String zone = "test-zone";
		final String ap = "aaaaaaaaaaa";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(ap)));
		deviceDataManager.setTopology(topology);

		DeviceConfig config = new DeviceConfig();
		config.enableConfig = false;

		// Set config
		HttpResponse<String> resp = Unirest
			.post(url + "?serial=" + ap)
			.body(gson.toJson(config))
			.asString();
		assertEquals(200, resp.getStatus());
		DeviceLayeredConfig fullCfg = gson.fromJson(
			deviceDataManager.getDeviceLayeredConfigJson(),
			DeviceLayeredConfig.class
		);
		assertEquals(1, fullCfg.apConfig.size());
		assertTrue(fullCfg.apConfig.containsKey(ap));
		assertEquals(
			gson.toJson(config),
			gson.toJson(fullCfg.apConfig.get(ap))
		);

		// Missing/wrong parameters
		assertEquals(
			400,
			Unirest.post(url).body(gson.toJson(config)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?serial=asdf").body(gson.toJson(config)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?serial=" + ap).body("not json").asString().getStatus()
		);
	}

	@Test
	@Order(8)
	void test_modifyDeviceApConfig() throws Exception {
		String url = endpoint("/api/v1/modifyDeviceApConfig");

		// Create topology
		final String zone = "test-zone";
		final String ap = "aaaaaaaaaaa";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList(ap)));
		deviceDataManager.setTopology(topology);

		// Set initial AP config
		DeviceConfig apConfig = new DeviceConfig();
		apConfig.enableConfig = false;
		apConfig.autoChannels = new HashMap<>();
		apConfig.autoChannels.put(UCentralConstants.BAND_2G, 7);
		apConfig.autoChannels.put(UCentralConstants.BAND_5G, 165);
		deviceDataManager.setDeviceApConfig(ap, apConfig);

		// Construct config request
		DeviceConfig configReq = new DeviceConfig();
		configReq.enableConfig = true;
		configReq.autoTxPowers = new HashMap<>();
		configReq.autoTxPowers.put(UCentralConstants.BAND_2G, 20);
		configReq.autoTxPowers.put(UCentralConstants.BAND_5G, 28);

		// Merge config objects (expected result)
		apConfig.apply(configReq);

		// Set config
		HttpResponse<String> resp = Unirest
			.post(url + "?serial=" + ap)
			.body(gson.toJson(configReq))
			.asString();
		assertEquals(200, resp.getStatus());
		DeviceLayeredConfig fullCfg = gson.fromJson(
			deviceDataManager.getDeviceLayeredConfigJson(),
			DeviceLayeredConfig.class
		);
		assertTrue(fullCfg.apConfig.containsKey(ap));
		assertEquals(
			gson.toJson(apConfig),
			gson.toJson(fullCfg.apConfig.get(ap))
		);

		// Missing/wrong parameters
		assertEquals(
			400,
			Unirest.post(url).body(gson.toJson(configReq)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?serial=asdf").body(gson.toJson(configReq)).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?serial=" + ap).body("not json").asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url + "?serial=" + ap).body("{}").asString().getStatus()
		);
	}

	@Test
	@Order(100)
	void test_currentModel() throws Exception {
		// Fetch RRM model
		HttpResponse<String> resp =
			Unirest.get(endpoint("/api/v1/currentModel")).asString();
		assertEquals(200, resp.getStatus());
		assertEquals(gson.toJson(modeler.getDataModel()), resp.getBody());
	}

	@Test
	@Order(101)
	void test_optimizeChannel() throws Exception {
		String url = endpoint("/api/v1/optimizeChannel");

		// Create topology
		final String zone = "test-zone";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList("aaaaaaaaaaa")));
		deviceDataManager.setTopology(topology);

		// Correct requests
		final String[] modes =
			new String[] { "random", "least_used", "unmanaged_aware" };
		for (String mode : modes) {
			String endpoint =
				String.format("%s?mode=%s&zone=%s", url, mode, zone);
			HttpResponse<JsonNode> resp = Unirest.get(endpoint).asJson();
			assertEquals(200, resp.getStatus());
			assertNotNull(resp.getBody().getObject().getJSONObject("data"));
		}

		// Missing/wrong parameters
		assertEquals(400, Unirest.get(url).asString().getStatus());
		assertEquals(
			400,
			Unirest.get(url + "?mode=test123&zone=" + zone).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.get(url + "?zone=asdf&mode=" + modes[0]).asString().getStatus()
		);
	}

	@Test
	@Order(102)
	void test_optimizeTxPower() throws Exception {
		String url = endpoint("/api/v1/optimizeTxPower");

		// Create topology
		final String zone = "test-zone";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList("aaaaaaaaaaa")));
		deviceDataManager.setTopology(topology);

		// Correct requests
		final String[] modes = new String[] { "random",
			"measure_ap_client",
			"measure_ap_ap",
			"location_optimal" };
		for (String mode : modes) {
			String endpoint =
				String.format("%s?mode=%s&zone=%s", url, mode, zone);
			HttpResponse<JsonNode> resp = Unirest.get(endpoint).asJson();
			assertEquals(200, resp.getStatus());
			assertNotNull(resp.getBody().getObject().getJSONObject("data"));
		}

		// Missing/wrong parameters
		assertEquals(400, Unirest.get(url).asString().getStatus());
		assertEquals(
			400,
			Unirest.get(url + "?mode=test123&zone=" + zone).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.get(url + "?zone=asdf&mode=" + modes[0]).asString().getStatus()
		);
	}

	@Test
	@Order(1000)
	void testDocs() throws Exception {
		// Index page paths
		assertEquals(200, Unirest.get(endpoint("/")).asString().getStatus());
		assertEquals(
			200,
			Unirest.get(endpoint("/index.html")).asString().getStatus()
		);

		// OpenAPI YAML/JSON
		HttpResponse<String> yamlResp =
			Unirest.get(endpoint("/openapi.yaml")).asString();
		assertEquals(200, yamlResp.getStatus());
		HttpResponse<JsonNode> jsonResp =
			Unirest.get(endpoint("/openapi.json")).asJson();
		assertEquals(200, jsonResp.getStatus());
		// Check that we got the OpenAPI 3.x version string
		assertTrue(
			Arrays.stream(
				yamlResp.getBody().split("\\R+")
			)
				.filter(line -> line.matches("^openapi: 3[0-9.]*$"))
				.findFirst()
				.isPresent()
		);
		assertTrue(
			jsonResp.getBody().getObject().getString("openapi").matches("^3[0-9.]*$")
		);
		// Check that we got some endpoint paths
		JSONObject paths =
			jsonResp.getBody().getObject().getJSONObject("paths");
		assertFalse(paths.isEmpty());
		assertTrue(paths.keys().next().startsWith("/api/"));
	}

	@Test
	@Order(1001)
	void test404() throws Exception {
		final String fakeEndpoint = endpoint("/test123");
		assertEquals(404, Unirest.get(fakeEndpoint).asString().getStatus());
		assertEquals(404, Unirest.post(fakeEndpoint).asString().getStatus());
		assertEquals(404, Unirest.put(fakeEndpoint).asString().getStatus());
		assertEquals(404, Unirest.delete(fakeEndpoint).asString().getStatus());
		assertEquals(404, Unirest.head(fakeEndpoint).asString().getStatus());
		assertEquals(404, Unirest.patch(fakeEndpoint).asString().getStatus());
	}

	@Test
	@Order(1002)
	void testCORS() throws Exception {
		final String fakeEndpoint = endpoint("/test123");
		final String HEADERS = "authorization";
		final String METHOD = "GET";
		final String ORIGIN = "https://example.com";
		HttpResponse<String> resp = Unirest.options(fakeEndpoint)
			.header("Access-Control-Request-Headers", HEADERS)
			.header("Access-Control-Request-Method", METHOD)
			.header("Origin", ORIGIN)
			.asString();
		assertEquals(200, resp.getStatus());
		assertEquals(
			ORIGIN,
			resp.getHeaders().getFirst("Access-Control-Allow-Origin")
		);
		assertEquals("Origin", resp.getHeaders().getFirst("Vary"));
		assertEquals(
			HEADERS,
			resp.getHeaders().getFirst("Access-Control-Allow-Headers")
		);
		assertEquals(
			METHOD,
			resp.getHeaders().getFirst("Access-Control-Allow-Methods")
		);
		assertEquals(
			"true",
			resp.getHeaders().getFirst("Access-Control-Allow-Credentials")
		);
		String maxAge = resp.getHeaders().getFirst("Access-Control-Max-Age");
		assertTrue(Integer.parseInt(maxAge) >= 0);
	}

	@Test
	@Order(2000)
	void test_system() throws Exception {
		// Test on GET api
		HttpResponse<JsonNode> get_resp =
			Unirest.get(endpoint("/api/v1/system?command=info")).asJson();
		assertEquals(200, get_resp.getStatus());
		assertEquals(
			VersionProvider.get(),
			get_resp.getBody().getObject().getString("version")
		);

		// Test on POST api
		String url = endpoint("/api/v1/system");
		// Valid command
		HttpResponse<String> post_resp = Unirest
			.post(url)
			.body("{\"command\": \"reload\"}")
			.asString();
		assertEquals(200, post_resp.getStatus());

		// Missing/wrong parameters
		assertEquals(
			400,
			Unirest.post(url).body("{\"command\": \"xxx\"}").asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.post(url).body("{\"invalid command\": \"xxx\"}").asString().getStatus()
		);
	}

	@Test
	@Order(2001)
	void test_provider() throws Exception {
		HttpResponse<JsonNode> resp =
			Unirest.get(endpoint("/api/v1/provider")).asJson();
		assertEquals(200, resp.getStatus());
		assertEquals(
			rrmConfig.serviceConfig.vendor,
			resp.getBody().getObject().getString("vendor")
		);
		assertEquals(
			rrmConfig.serviceConfig.vendorUrl,
			resp.getBody().getObject().getString("about")
		);
		assertEquals(
			VersionProvider.get(),
			resp.getBody().getObject().getString("version")
		);
	}

	@Test
	@Order(2002)
	void test_algorithms() throws Exception {
		HttpResponse<JsonNode> resp =
			Unirest.get(endpoint("/api/v1/algorithms")).asJson();
		assertEquals(200, resp.getStatus());
		assertEquals(
			RRMAlgorithm.AlgorithmType.values().length,
			resp.getBody().getArray().length()
		);
	}

	@Test
	@Order(2003)
	void test_runRRM() throws Exception {
		String url = endpoint("/api/v1/runRRM");

		// Create topology
		final String zone = "test-zone";
		DeviceTopology topology = new DeviceTopology();
		topology.put(zone, new TreeSet<>(Arrays.asList("aaaaaaaaaaa")));
		deviceDataManager.setTopology(topology);

		// Correct requests
		final List<String> algorithms = Arrays
			.stream(RRMAlgorithm.AlgorithmType.values())
			.map(RRMAlgorithm.AlgorithmType::name)
			.collect(Collectors.toList());
		for (String name : algorithms) {
			String endpoint =
				String.format("%s?algorithm=%s&venue=%s", url, name, zone);
			HttpResponse<JsonNode> resp = Unirest.put(endpoint).asJson();
			assertEquals(200, resp.getStatus());
			assertFalse(resp.getBody().getObject().has("error"));
			assertEquals(1, resp.getBody().getObject().keySet().size());
		}

		// Missing/wrong parameters
		assertEquals(400, Unirest.put(url).asString().getStatus());
		assertEquals(
			400,
			Unirest.put(url + "?mode=test123&venue=" + zone).asString().getStatus()
		);
		assertEquals(
			400,
			Unirest.put(url + "?venue=asdf&algorithm=" + algorithms.get(0)).asString().getStatus()
		);
	}
}
