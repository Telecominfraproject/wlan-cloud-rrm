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
		 * ({@code SERVICECONFIG_NAME})
		 */
		public String name = "owrrm";

		/**
		 * RRM service ID (random persistent number)
		 * ({@code SERVICECONFIG_NAME})
		 */
		public long id = (long) (Math.random() * Long.MAX_VALUE);

		/**
		 * Private endpoint for the RRM service
		 * ({@code SERVICECONFIG_PRIVATEENDPOINT})
		 */
		public String privateEndpoint = "http://owrrm.wlan.local:16789"; // see ApiServerParams.httpPort

		/**
		 * Public endpoint for the RRM service
		 * ({@code SERVICECONFIG_PUBLICENDPOINT})
		 */
		public String publicEndpoint = "";

		/**
		 * RRM vendor name
		 * ({@code SERVICECONFIG_VENDOR})
		 */
		public String vendor = "Meta";

		/**
		 * RRM vendor URL
		 * ({@code SERVICECONFIG_VENDORURL})
		 */
		public String vendorUrl =
			"https://github.com/Telecominfraproject/wlan-cloud-rrm";

		/**
		 * RRM reference URL
		 * ({@code SERVICECONFIG_VENDORREFERENCEURL})
		 */
		public String vendorReferenceUrl =
			"https://github.com/Telecominfraproject/wlan-cloud-rrm/blob/main/ALGORITHMS.md";
	}

	/** Service configuration. */
	public ServiceConfig serviceConfig = new ServiceConfig();

	/**
	 * uCentral configuration.
	 */
	public class UCentralConfig {
		/**
		 * If set, use public service endpoints instead of private ones
		 * ({@code UCENTRALCONFIG_USEPUBLICENDPOINTS})
		 */
		public boolean usePublicEndpoints = false;

		/**
		 * uCentralSec public endpoint
		 * ({@code UCENTRALCONFIG_UCENTRALSECPUBLICENDPOINT})
		 */
		public String uCentralSecPublicEndpoint = "";

		/**
		 * uCentral username (for public endpoints only)
		 * ({@code UCENTRALCONFIG_USERNAME})
		 */
		public String username = "tip@ucentral.com";

		/**
		 * uCentral password (for public endpoints only)
		 * ({@code UCENTRALCONFIG_PASSWORD})
		 */
		public String password = "";

		/**
		 * Verify SSL/TLS certificates in HTTPS requests
		 * ({@code UCENTRALCONFIG_VERIFYSSL})
		 */
		public boolean verifySsl = false;

		/**
		 * uCentral socket parameters.
		 */
		public class UCentralSocketParams {
			/**
			 * Connection timeout for all requests, in ms
			 * ({@code UCENTRALSOCKETPARAMS_CONNECTTIMEOUTMS})
			 */
			public int connectTimeoutMs = 2000;

			/**
			 * Socket timeout for all requests, in ms
			 * ({@code UCENTRALSOCKETPARAMS_SOCKETTIMEOUTMS})
			 */
			public int socketTimeoutMs = 15000;

			/**
			 * Socket timeout for wifi scan requests, in ms
			 * ({@code UCENTRALSOCKETPARAMS_WIFISCANTIMEOUTMS})
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
		 * ({@code KAFKACONFIG_BOOTSTRAPSERVER})
		 */
		public String bootstrapServer = "127.0.0.1:9093";

		/**
		 * Kafka topic holding uCentral state
		 * ({@code KAFKACONFIG_STATETOPIC})
		 */
		public String stateTopic = "state";

		/**
		 * Kafka topic holding uCentral wifi scan results
		 * ({@code KAFKACONFIG_WIFISCANTOPIC})
		 */
		public String wifiScanTopic = "wifiscan";

		/**
		 * Kafka topic holding uCentral microservice events.
		 * Used for detecting API keys and internal endpoints.
		 * ({@code KAFKACONFIG_SERVICEEVENTSTOPIC})
		 */
		public String serviceEventsTopic = "service_events";

		/**
		 * Kafka consumer group ID
		 * ({@code KAFKACONFIG_GROUPID})
		 */
		public String groupId = "rrm-service";

		/**
		 * Kafka "auto.offset.reset" config ["earliest", "latest"]
		 * ({@code KAFKACONFIG_AUTOOFFSETRESET})
		 */
		public String autoOffsetReset = "latest";

		/**
		 * Kafka consumer poll timeout duration, in ms
		 * ({@code KAFKACONFIG_POLLTIMEOUTMS})
		 */
		public long pollTimeoutMs = 10000;
	}

	/** uCentral Kafka configuration. */
	public KafkaConfig kafkaConfig = new KafkaConfig();

	/**
	 * Database configuration.
	 */
	public class DatabaseConfig {
		/**
		 * MySQL database host:port, or empty to disable
		 * ({@code DATABASECONFIG_SERVER})
		 */
		public String server = "127.0.0.1:3306";

		/**
		 * MySQL database user
		 * ({@code DATABASECONFIG_USER})
		 */
		public String user = "root";

		/**
		 * MySQL database password
		 * ({@code DATABASECONFIG_PASSWORD})
		 */
		public String password = "openwifi";

		/**
		 * MySQL database name
		 * ({@code DATABASECONFIG_DBNAME})
		 */
		public String dbName = "rrm";

		/**
		 * Data retention interval in days (0 to disable)
		 * ({@code DATABASECONFIG_DATARETENTIONINTERVALDAYS})
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
			 * ({@code DATACOLLECTORPARAMS_UPDATEINTERVALMS})
			 */
			public int updateIntervalMs = 5000;

			/**
			 * The expected device statistics interval, in seconds (or -1 to
			 * disable managing this value)
			 * ({@code DATACOLLECTORPARAMS_DEVICESTATSINTERVALSEC})
			 */
			public int deviceStatsIntervalSec = 60;

			/**
			 * The wifi scan interval (per device), in seconds (or -1 to disable
			 * automatic scans)
			 * ({@code DATACOLLECTORPARAMS_WIFISCANINTERVALSEC})
			 */
			public int wifiScanIntervalSec = 900;

			/**
			 * The capabilities request interval (per device), in seconds
			 * ({@code DATACOLLECTORPARAMS_CAPABILITIESINTERVALSEC})
			 */
			public int capabilitiesIntervalSec = 3600;

			/**
			 * Number of executor threads for async tasks (ex. wifi scans)
			 * ({@code DATACOLLECTORPARAMS_EXECUTORTHREADCOUNT})
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
			 * ({@code CONFIGMANAGERPARAMS_UPDATEINTERVALMS})
			 */
			public int updateIntervalMs = 60000;

			/**
			 * Enable pushing device config changes?
			 * ({@code CONFIGMANAGERPARAMS_CONFIGENABLED})
			 */
			public boolean configEnabled = true;

			/**
			 * If set, device config changes will only be pushed on events
			 * (e.g. RRM algorithm execution, config API calls)
			 * ({@code CONFIGMANAGERPARAMS_CONFIGONEVENTONLY})
			 */
			public boolean configOnEventOnly = true;

			/**
			 * The debounce interval for reconfiguring the same device, in
			 * seconds (or -1 to disable)
			 * ({@code CONFIGMANAGERPARAMS_CONFIGDEBOUNCEINTERVALSEC})
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
			 * ({@code MODELERPARAMS_WIFISCANBUFFERSIZE})
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
			 * ({@code APISERVERPARAMS_HTTPPORT})
			 */
			public int httpPort = 16789;

			/**
			 * Comma-separated list of all allowed CORS domains (exact match
			 * including subdomain portions, but excluding protocol).
			 * If empty, enable CORS for all origins.
			 * ({@code APISERVERPARAMS_CORSDOMAINLIST})
			 */
			public String corsDomainList = "";

			/**
			 * Enable HTTP basic auth?
			 * ({@code APISERVERPARAMS_USEBASICAUTH})
			 */
			public boolean useBasicAuth = false;

			/**
			 * The HTTP basic auth username (if enabled)
			 * ({@code APISERVERPARAMS_BASICAUTHUSER})
			 */
			public String basicAuthUser = "admin";

			/**
			 * The HTTP basic auth password (if enabled)
			 * ({@code APISERVERPARAMS_BASICAUTHPASSWORD})
			 */
			public String basicAuthPassword = "openwifi";

			/**
			 * Enable OpenWiFi authentication via tokens (external) and API keys
			 * (internal)
			 * ({@code APISERVERPARAMS_USEOPENWIFIAUTH})
			 */
			public boolean useOpenWifiAuth = false;

			/**
			 * The maximum cache size for OpenWiFi tokens
			 * ({@code APISERVERPARAMS_OPENWIFIAUTHCACHESIZE})
			 */
			public int openWifiAuthCacheSize = 100;
		}

		/** ApiServer parameters. */
		public ApiServerParams apiServerParams = new ApiServerParams();

		/**
		 * ProvMonitor parameters.
		 */
		public class ProvMonitorParams {
			/**
			 * Enable use of venue information for topology
			 * ({@code PROVMONITORPARAMS_USEVENUES})
			 */
			public boolean useVenues = true;

			/**
			 * Sync interval, in ms, for owprov venue information etc.
			 * ({@code PROVMONITORPARAMS_SYNCINTERVALMS})
			 */
			public int syncIntervalMs = 300000;
		}

		/** ProvMonitor parameters. */
		public ProvMonitorParams provMonitorParams = new ProvMonitorParams();

		/**
		 * RRMScheduler parameters.
		 */
		public class RRMSchedulerParams {
			/**
			 * Thread pool size for executing jobs
			 * ({@code SCHEDULERPARAMS_THREADCOUNT})
			 */
			public int threadCount = 2;

			/**
			 * If set, do not apply any changes from scheduled algorithms
			 * ({@code SCHEDULERPARAMS_DRYRUN})
			 */
			public boolean dryRun = false;
		}

		/** RRMScheduler parameters. */
		public RRMSchedulerParams schedulerParams = new RRMSchedulerParams();
	}

	/** Module configuration. */
	public ModuleConfig moduleConfig = new ModuleConfig();

	/** Construct RRMConfig from environment variables. */
	public static RRMConfig fromEnv(Map<String, String> env) {
		RRMConfig config = new RRMConfig();
		String v;

		// @formatter:off

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
		if ((v = env.get("SERVICECONFIG_VENDOR")) != null) {
			serviceConfig.vendor = v;
		}
		if ((v = env.get("SERVICECONFIG_VENDORURL")) != null) {
			serviceConfig.vendorUrl = v;
		}
		if ((v = env.get("SERVICECONFIG_VENDORREFERENCEURL")) != null) {
			serviceConfig.vendorReferenceUrl = v;
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
		if ((v = env.get("UCENTRALCONFIG_VERIFYSSL")) != null) {
			uCentralConfig.verifySsl = Boolean.parseBoolean(v);
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
		if ((v = env.get("KAFKACONFIG_POLLTIMEOUTMS")) != null) {
			kafkaConfig.pollTimeoutMs = Long.parseLong(v);
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
		if ((v = env.get("APISERVERPARAMS_CORSDOMAINLIST")) != null) {
			apiServerParams.corsDomainList = v;
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
		if ((v = env.get("APISERVERPARAMS_USEOPENWIFIAUTH")) != null) {
			apiServerParams.useOpenWifiAuth = Boolean.parseBoolean(v);
		}
		if ((v = env.get("APISERVERPARAMS_OPENWIFIAUTHCACHESIZE")) != null) {
			apiServerParams.openWifiAuthCacheSize = Integer.parseInt(v);
		}
		ModuleConfig.ProvMonitorParams provManagerParams =
			config.moduleConfig.provMonitorParams;
		if ((v = env.get("PROVMONITORPARAMS_USEVENUES")) != null) {
			provManagerParams.useVenues = Boolean.parseBoolean(v);
		}
		if ((v = env.get("PROVMONITORPARAMS_SYNCINTERVALMS")) != null) {
			provManagerParams.syncIntervalMs = Integer.parseInt(v);
		}
		ModuleConfig.RRMSchedulerParams schedulerParams =
			config.moduleConfig.schedulerParams;
		if ((v = env.get("SCHEDULERPARAMS_THREADCOUNT")) != null) {
			schedulerParams.threadCount = Integer.parseInt(v);
		}
		if ((v = env.get("SCHEDULERPARAMS_DRYRUN")) != null) {
			schedulerParams.dryRun = Boolean.parseBoolean(v);
		}

		// @formatter:on

		return config;
	}
}
