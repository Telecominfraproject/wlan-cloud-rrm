/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.util.Map;

/**
 * RRM service configuration model.
 */
public class RRMConfig {
	/**
	 * Service configuration.
	 */
	public class ServiceConfig {
		/**
		 * RRM service name/type
		 * (<tt>SERVICECONFIG_NAME</tt>)
		 */
		public String name = "owrrm";

		/**
		 * RRM service ID (random persistent number)
		 * (<tt>SERVICECONFIG_NAME</tt>)
		 */
		public long id = (long) (Math.random() * Long.MAX_VALUE);

		/**
		 * Private endpoint for the RRM service
		 * (<tt>SERVICECONFIG_PRIVATEENDPOINT</tt>)
		 */
		public String privateEndpoint = "https://owrrm.wlan.local:17006";

		/**
		 * Public endpoint for the RRM service
		 * (<tt>SERVICECONFIG_PUBLICENDPOINT</tt>)
		 */
		public String publicEndpoint = "";
	}

	/** Service configuration. */
	public ServiceConfig serviceConfig = new ServiceConfig();

	/**
	 * uCentral configuration.
	 */
	public class UCentralConfig {
		/**
		 * If set, use public service endpoints instead of private ones
		 * (<tt>UCENTRALCONFIG_USEPUBLICENDPOINTS</tt>)
		 */
		public boolean usePublicEndpoints = false;

		/**
		 * uCentralSec public endpoint
		 * (<tt>UCENTRALCONFIG_UCENTRALSECPUBLICENDPOINT</tt>)
		 */
		public String uCentralSecPublicEndpoint = "";

		/**
		 * uCentral username (for public endpoints only)
		 * (<tt>UCENTRALCONFIG_USERNAME</tt>)
		 */
		public String username = "tip@ucentral.com";

		/**
		 * uCentral password (for public endpoints only)
		 * (<tt>UCENTRALCONFIG_PASSWORD</tt>)
		 */
		public String password = "";

		/**
		 * uCentral socket parameters
		 */
		public class UCentralSocketParams {
			/**
			 * Connection timeout for all requests, in ms
			 * (<tt>UCENTRALSOCKETPARAMS_CONNECTTIMEOUTMS</tt>)
			 */
			public int connectTimeoutMs = 2000;

			/**
			 * Socket timeout for all requests, in ms
			 * (<tt>UCENTRALSOCKETPARAMS_SOCKETTIMEOUTMS</tt>)
			 */
			public int socketTimeoutMs = 15000;

			/**
			 * Socket timeout for wifi scan requests, in ms
			 * (<tt>UCENTRALSOCKETPARAMS_WIFISCANTIMEOUTMS</tt>)
			 */
			public int wifiScanTimeoutMs = 45000;
		}

		/** uCentral socket parameters */
		public UCentralSocketParams uCentralSocketParams =
			new UCentralSocketParams();
	}

	/** uCentral configuration. */
	public UCentralConfig uCentralConfig = new UCentralConfig();

	/**
	 * uCentral Kafka configuration.
	 */
	public class KafkaConfig {
		/**
		 * Kafka bootstrap host:port, or empty to disable
		 * (<tt>KAFKACONFIG_BOOTSTRAPSERVER</tt>)
		 */
		public String bootstrapServer = "127.0.0.1:9093";

		/**
		 * Kafka topic holding uCentral state
		 * (<tt>KAFKACONFIG_STATETOPIC</tt>)
		 */
		public String stateTopic = "state";

		/**
		 * Kafka topic holding uCentral wifi scan results
		 * (<tt>KAFKACONFIG_WIFISCANTOPIC</tt>)
		 */
		public String wifiScanTopic = "wifiscan";

		/**
		 * Kafka topic holding uCentral microservice events.
		 * Used for detecting API keys and internal endpoints.
		 * (<tt>KAFKACONFIG_SERVICEEVENTSTOPIC</tt>)
		 */
		public String serviceEventsTopic = "service_events";

		/**
		 * Kafka consumer group ID
		 * (<tt>KAFKACONFIG_GROUPID</tt>)
		 */
		public String groupId = "rrm-service";

		/**
		 * Kafka "auto.offset.reset" config ["earliest", "latest"]
		 * (<tt>KAFKACONFIG_AUTOOFFSETRESET</tt>)
		 */
		public String autoOffsetReset = "latest";
	}

	/** uCentral Kafka configuration. */
	public KafkaConfig kafkaConfig = new KafkaConfig();

	/**
	 * Database configuration.
	 */
	public class DatabaseConfig {
		/**
		 * MySQL database host:port, or empty to disable
		 * (<tt>DATABASECONFIG_SERVER</tt>)
		 */
		public String server = "127.0.0.1:3306";

		/**
		 * MySQL database user
		 * (<tt>DATABASECONFIG_USER</tt>)
		 */
		public String user = "root";

		/**
		 * MySQL database password
		 * (<tt>DATABASECONFIG_PASSWORD</tt>)
		 */
		public String password = "openwifi";

		/**
		 * MySQL database name
		 * (<tt>DATABASECONFIG_DBNAME</tt>)
		 */
		public String dbName = "rrm";

		/**
		 * Data retention interval in days (0 to disable)
		 * (<tt>DATABASECONFIG_DATARETENTIONINTERVALDAYS</tt>)
		 */
		public int dataRetentionIntervalDays = 14;
	}

	/** Database configuration. */
	public DatabaseConfig databaseConfig = new DatabaseConfig();

	/**
	 * Module configuration.
	 */
	public class ModuleConfig {
		/**
		 * DataCollector parameters.
		 */
		public class DataCollectorParams {
			/**
			 * The main logic loop interval (i.e. sleep time), in ms
			 * (<tt>DATACOLLECTORPARAMS_UPDATEINTERVALMS</tt>)
			 */
			public int updateIntervalMs = 5000;

			/**
			 * The expected device statistics interval, in seconds (or -1 to
			 * disable managing this value)
			 * (<tt>DATACOLLECTORPARAMS_DEVICESTATSINTERVALSEC</tt>)
			 */
			public int deviceStatsIntervalSec = 60;

			/**
			 * The wifi scan interval (per device), in seconds (or -1 to disable
			 * automatic scans)
			 * (<tt>DATACOLLECTORPARAMS_WIFISCANINTERVALSEC</tt>)
			 */
			public int wifiScanIntervalSec = 60;

			/**
			 * The capabilities request interval (per device), in seconds
			 * (<tt>DATACOLLECTORPARAMS_CAPABILITIESINTERVALSEC</tt>)
			 */
			public int capabilitiesIntervalSec = 3600;

			/**
			 * Number of executor threads for async tasks (ex. wifi scans)
			 * (<tt>DATACOLLECTORPARAMS_EXECUTORTHREADCOUNT</tt>)
			 */
			public int executorThreadCount = 3;
		}

		/** DataCollector parameters. */
		public DataCollectorParams dataCollectorParams =
			new DataCollectorParams();

		/**
		 * ConfigManager parameters.
		 */
		public class ConfigManagerParams {
			/**
			 * The main logic loop interval (i.e. sleep time), in ms
			 * (<tt>CONFIGMANAGERPARAMS_UPDATEINTERVALMS</tt>)
			 */
			public int updateIntervalMs = 60000;

			/**
			 * Enable pushing device config changes?
			 * (<tt>CONFIGMANAGERPARAMS_CONFIGENABLED</tt>)
			 */
			public boolean configEnabled = true;

			/**
			 * If set, device config changes will only be pushed on events
			 * (e.g. RRM algorithm execution, config API calls)
			 * (<tt>CONFIGMANAGERPARAMS_CONFIGONEVENTONLY</tt>)
			 */
			public boolean configOnEventOnly = false;

			/**
			 * The debounce interval for reconfiguring the same device, in
			 * seconds (or -1 to disable)
			 * (<tt>CONFIGMANAGERPARAMS_CONFIGDEBOUNCEINTERVALSEC</tt>)
			 */
			public int configDebounceIntervalSec = 30;
		}

		/** ConfigManager parameters. */
		public ConfigManagerParams configManagerParams =
			new ConfigManagerParams();

		/**
		 * Modeler parameters.
		 */
		public class ModelerParams {
			/**
			 * Maximum rounds of wifi scan results to store per device
			 * (<tt>MODELERPARAMS_WIFISCANBUFFERSIZE</tt>)
			 */
			public int wifiScanBufferSize = 10;
		}

		/** Modeler parameters. */
		public ModelerParams modelerParams = new ModelerParams();

		/**
		 * ApiServer parameters.
		 */
		public class ApiServerParams {
			/**
			 * The HTTP port to listen on, or -1 to disable
			 * (<tt>APISERVERPARAMS_HTTPPORT</tt>)
			 */
			public int httpPort = 16789;

			/**
			 * Enable HTTP basic auth?
			 * (<tt>APISERVERPARAMS_USEBASICAUTH</tt>)
			 */
			public boolean useBasicAuth = true;

			/**
			 * The HTTP basic auth username (if enabled)
			 * (<tt>APISERVERPARAMS_BASICAUTHUSER</tt>)
			 */
			public String basicAuthUser = "admin";

			/**
			 * The HTTP basic auth password (if enabled)
			 * (<tt>APISERVERPARAMS_BASICAUTHPASSWORD</tt>)
			 */
			public String basicAuthPassword = "openwifi";
		}

		/** ApiServer parameters. */
		public ApiServerParams apiServerParams = new ApiServerParams();

		/**
		 * ProvMonitor parameters.
		 */
		public class ProvMonitorParams {
			/**
			 * Enable use of venue information for topology
			 * (<tt>PROVMONITORPARAMS_USEVENUES</tt>)
			 */
			public boolean useVenues = true;

			/**
			 * Sync interval, in ms, for owprov venue information etc.
			 * (<tt>PROVMONITORPARAMS_SYNCINTERVALMS</tt>)
			 */
			 public int syncIntervalMs = 300000;
		}

		/** ProvMonitor parameters. */
		public ProvMonitorParams provMonitorParams = new ProvMonitorParams();
	}

	/** Module configuration. */
	public ModuleConfig moduleConfig = new ModuleConfig();

	/** Construct RRMConfig from environment variables. */
	public static RRMConfig fromEnv(Map<String, String> env) {
		RRMConfig config = new RRMConfig();
		String v;

		/* ServiceConfig */
		ServiceConfig serviceConfig = config.serviceConfig;
		if ((v = env.get("SERVICECONFIG_NAME")) != null) {
			serviceConfig.name = v;
		}
		if ((v = env.get("SERVICECONFIG_NAME")) != null) {
			serviceConfig.id = Long.parseLong(v);
		}
		if ((v = env.get("SERVICECONFIG_PRIVATEENDPOINT")) != null) {
			serviceConfig.privateEndpoint = v;
		}
		if ((v = env.get("SERVICECONFIG_PUBLICENDPOINT")) != null) {
			serviceConfig.publicEndpoint = v;
		}

		/* UCentralConfig */
		UCentralConfig uCentralConfig = config.uCentralConfig;
		if ((v = env.get("UCENTRALCONFIG_USEPUBLICENDPOINTS")) != null) {
			uCentralConfig.usePublicEndpoints = Boolean.parseBoolean(v);
		}
		if ((v = env.get("UCENTRALCONFIG_UCENTRALSECPUBLICENDPOINT")) != null) {
			uCentralConfig.uCentralSecPublicEndpoint = v;
		}
		if ((v = env.get("UCENTRALCONFIG_USERNAME")) != null) {
			uCentralConfig.username = v;
		}
		if ((v = env.get("UCENTRALCONFIG_PASSWORD")) != null) {
			uCentralConfig.password = v;
		}
		UCentralConfig.UCentralSocketParams uCentralSocketParams =
			config.uCentralConfig.uCentralSocketParams;
		if ((v = env.get("UCENTRALSOCKETPARAMS_CONNECTTIMEOUTMS")) != null) {
			uCentralSocketParams.connectTimeoutMs = Integer.parseInt(v);
		}
		if ((v = env.get("UCENTRALSOCKETPARAMS_SOCKETTIMEOUTMS")) != null) {
			uCentralSocketParams.socketTimeoutMs = Integer.parseInt(v);
		}
		if ((v = env.get("UCENTRALSOCKETPARAMS_WIFISCANTIMEOUTMS")) != null) {
			uCentralSocketParams.wifiScanTimeoutMs = Integer.parseInt(v);
		}

		/* KafkaConfig */
		KafkaConfig kafkaConfig = config.kafkaConfig;
		if ((v = env.get("KAFKACONFIG_BOOTSTRAPSERVER")) != null) {
			kafkaConfig.bootstrapServer = v;
		}
		if ((v = env.get("KAFKACONFIG_STATETOPIC")) != null) {
			kafkaConfig.stateTopic = v;
		}
		if ((v = env.get("KAFKACONFIG_WIFISCANTOPIC")) != null) {
			kafkaConfig.wifiScanTopic = v;
		}
		if ((v = env.get("KAFKACONFIG_SERVICEEVENTSTOPIC")) != null) {
			kafkaConfig.serviceEventsTopic = v;
		}
		if ((v = env.get("KAFKACONFIG_GROUPID")) != null) {
			kafkaConfig.groupId = v;
		}
		if ((v = env.get("KAFKACONFIG_AUTOOFFSETRESET")) != null) {
			kafkaConfig.autoOffsetReset = v;
		}

		/* DatabaseConfig */
		DatabaseConfig databaseConfig = config.databaseConfig;
		if ((v = env.get("DATABASECONFIG_SERVER")) != null) {
			databaseConfig.server = v;
		}
		if ((v = env.get("DATABASECONFIG_USER")) != null) {
			databaseConfig.user = v;
		}
		if ((v = env.get("DATABASECONFIG_PASSWORD")) != null) {
			databaseConfig.password = v;
		}
		if ((v = env.get("DATABASECONFIG_DBNAME")) != null) {
			databaseConfig.dbName = v;
		}
		if ((v = env.get("DATABASECONFIG_DATARETENTIONINTERVALDAYS")) != null) {
			databaseConfig.dataRetentionIntervalDays = Integer.parseInt(v);
		}

		/* ModuleConfig */
		ModuleConfig.DataCollectorParams dataCollectorParams =
			config.moduleConfig.dataCollectorParams;
		if ((v = env.get("DATACOLLECTORPARAMS_UPDATEINTERVALMS")) != null) {
			dataCollectorParams.updateIntervalMs = Integer.parseInt(v);
		}
		if ((v = env.get("DATACOLLECTORPARAMS_DEVICESTATSINTERVALSEC")) != null) {
			dataCollectorParams.deviceStatsIntervalSec = Integer.parseInt(v);
		}
		if ((v = env.get("DATACOLLECTORPARAMS_WIFISCANINTERVALSEC")) != null) {
			dataCollectorParams.wifiScanIntervalSec = Integer.parseInt(v);
		}
		if ((v = env.get("DATACOLLECTORPARAMS_CAPABILITIESINTERVALSEC")) != null) {
			dataCollectorParams.capabilitiesIntervalSec = Integer.parseInt(v);
		}
		if ((v = env.get("DATACOLLECTORPARAMS_EXECUTORTHREADCOUNT")) != null) {
			dataCollectorParams.executorThreadCount = Integer.parseInt(v);
		}
		ModuleConfig.ConfigManagerParams configManagerParams =
			config.moduleConfig.configManagerParams;
		if ((v = env.get("CONFIGMANAGERPARAMS_UPDATEINTERVALMS")) != null) {
			configManagerParams.updateIntervalMs = Integer.parseInt(v);
		}
		if ((v = env.get("CONFIGMANAGERPARAMS_CONFIGENABLED")) != null) {
			configManagerParams.configEnabled = Boolean.parseBoolean(v);
		}
		if ((v = env.get("CONFIGMANAGERPARAMS_CONFIGONEVENTONLY")) != null) {
			configManagerParams.configOnEventOnly = Boolean.parseBoolean(v);
		}
		if ((v = env.get("CONFIGMANAGERPARAMS_CONFIGDEBOUNCEINTERVALSEC")) != null) {
			configManagerParams.configDebounceIntervalSec = Integer.parseInt(v);
		}
		ModuleConfig.ModelerParams modelerParams =
			config.moduleConfig.modelerParams;
		if ((v = env.get("MODELERPARAMS_WIFISCANBUFFERSIZE")) != null) {
			modelerParams.wifiScanBufferSize = Integer.parseInt(v);
		}
		ModuleConfig.ApiServerParams apiServerParams =
			config.moduleConfig.apiServerParams;
		if ((v = env.get("APISERVERPARAMS_HTTPPORT")) != null) {
			apiServerParams.httpPort = Integer.parseInt(v);
		}
		if ((v = env.get("APISERVERPARAMS_USEBASICAUTH")) != null) {
			apiServerParams.useBasicAuth = Boolean.parseBoolean(v);
		}
		if ((v = env.get("APISERVERPARAMS_BASICAUTHUSER")) != null) {
			apiServerParams.basicAuthUser = v;
		}
		if ((v = env.get("APISERVERPARAMS_BASICAUTHPASSWORD")) != null) {
			apiServerParams.basicAuthPassword = v;
		}
		ModuleConfig.ProvMonitorParams provManagerParams =
			config.moduleConfig.provMonitorParams;
		if ((v = env.get("PROVMONITORPARAMS_USEVENUES")) != null) {
			provManagerParams.useVenues = Boolean.parseBoolean(v);
		}
		if ((v = env.get("PROVMONITORPARAMS_SYNCINTERVALMS")) != null) {
			provManagerParams.syncIntervalMs = Integer.parseInt(v);
		}

		return config;
	}
}
