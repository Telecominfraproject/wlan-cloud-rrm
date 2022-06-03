/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

/**
 * RRM service configuration model.
 */
public class RRMConfig {
	/**
	 * uCentral configuration.
	 */
	public class UCentralConfig {
		/** uCentral user */
		public String user = "tip@ucentral.com";

		/** uCentral password */
		public String password = "openwifi";

		/** uCentralSec host */
		public String uCentralSecHost = "127.0.0.1";

		/** uCentralSec port */
		public int uCentralSecPort = 16001;

		/**
		 * uCentral socket parameters
		 */
		public class UCentralSocketParams {
			/** Connection timeout for all requests, in ms */
			public int connectTimeoutMs = 2000;

			/** Socket timeout for all requests, in ms */
			public int socketTimeoutMs = 15000;

			/** Socket timeout for wifi scan requests, in ms */
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
		/** Kafka bootstrap host:port, or empty to disable */
		public String bootstrapServer = "127.0.0.1:9093";

		/** Kafka topic holding uCentral state */
		public String stateTopic = "state";

		/** Kafka topic holding uCentral wifi scan results */
		public String wifiScanTopic = "wifiscan";

		/** Kafka consumer group ID */
		public String groupId = "rrm-service";

		/** Kafka "auto.offset.reset" config ["earliest", "latest"] */
		public String autoOffsetReset = "latest";
	}

	/** uCentral Kafka configuration. */
	public KafkaConfig kafkaConfig = new KafkaConfig();

	/**
	 * Database configuration.
	 */
	public class DatabaseConfig {
		/** MySQL database host:port, or empty to disable */
		public String server = "127.0.0.1:3306";

		/** MySQL database user */
		public String user = "root";

		/** MySQL database password */
		public String password = "openwifi";

		/** MySQL database name */
		public String dbName = "rrm";

		/** Data retention interval in days (0 to disable) */
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
			/** The main logic loop interval (i.e. sleep time), in ms. */
			public int updateIntervalMs = 5000;

			/** The expected device statistics interval, in seconds. */
			public int deviceStatsIntervalSec = 60;

			/**
			 * The wifi scan interval (per device), in seconds (or -1 to disable
			 * automatic scans).
			 */
			public int wifiScanIntervalSec = 60;

			/** The capabilities request interval (per device), in seconds */
			public int capabilitiesIntervalSec = 3600;

			/** Number of executor threads for async tasks (ex. wifi scans). */
			public int executorThreadCount = 3;
		}

		/** DataCollector parameters. */
		public DataCollectorParams dataCollectorParams =
			new DataCollectorParams();

		/**
		 * ConfigManager parameters.
		 */
		public class ConfigManagerParams {
			/** The main logic loop interval (i.e. sleep time), in ms. */
			public int updateIntervalMs = 60000;

			/** Enable pushing device config changes? */
			public boolean configEnabled = true;

			/**
			 * The debounce interval for reconfiguring the same device, in
			 * seconds.
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
			/** Maximum rounds of wifi scan results to store per device. */
			public int wifiScanBufferSize = 10;
		}

		/** Modeler parameters. */
		public ModelerParams modelerParams = new ModelerParams();

		/**
		 * ApiServer parameters.
		 */
		public class ApiServerParams {
			/** The HTTP port to listen on, or -1 to disable. */
			public int httpPort = 16789;

			/** Enable HTTP basic auth? */
			public boolean useBasicAuth = true;

			/** The HTTP basic auth username (if enabled). */
			public String basicAuthUser = "admin";

			/** The HTTP basic auth password (if enabled). */
			public String basicAuthPassword = "openwifi";
		}

		/** ApiServer parameters. */
		public ApiServerParams apiServerParams = new ApiServerParams();
	}

	/** Module configuration. */
	public ModuleConfig moduleConfig = new ModuleConfig();
}
