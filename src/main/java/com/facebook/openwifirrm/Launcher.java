/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.Callable;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.mysql.DatabaseManager;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralKafkaConsumer;
import com.facebook.openwifirrm.ucentral.UCentralKafkaProducer;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Launcher CLI.
 */
@Command(
	name = "",
	versionProvider = VersionProvider.class,
	descriptionHeading = "%n",
	description = "OpenWiFi uCentral-based radio resource management service.",
	optionListHeading = "%nOptions:%n",
	commandListHeading = "%nCommands:%n",
	mixinStandardHelpOptions = true,
	showDefaultValues = true
)
public class Launcher implements Callable<Integer> {
	private static final Logger logger = LoggerFactory.getLogger(Launcher.class);

	/** Default config file location. */
	private static final File DEFAULT_CONFIG_FILE = new File("settings.json");

	/** Default device topology file location. */
	private static final File DEFAULT_TOPOLOGY_FILE = new File("topology.json");

	/** Default device layered config file location. */
	private static final File DEFAULT_DEVICE_LAYERED_CONFIG_FILE =
		new File("device_config.json");

	/**
	 * Read the input RRM config file.
	 *
	 * If the file does not exist, try to create it using default values.
	 *
	 * If the file is missing any default fields in {@link RRMConfig}, try to
	 * rewrite it.
	 *
	 * @throws IOException if file I/O fails
	 */
	private RRMConfig readRRMConfig(File configFile) throws IOException {
		RRMConfig config;
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		JSONObject userConfig = null;
		if (configFile.isFile()) {
			// Read file
			logger.info("Reading config file '{}'", configFile.getPath());
			String contents = Utils.readFile(configFile);
			if (!contents.isEmpty()) {
				userConfig = new JSONObject(contents);
			}
		}
		if (userConfig == null) {
			// Missing/empty file, write defaults to disk
			logger.info("Creating default config file '{}'", configFile.getPath());
			config = new RRMConfig();
			Utils.writeJsonFile(configFile, config);
		} else {
			// In case of any added/missing values, we want to build off the
			// defaults in RRMConfig, so this code gets more complex...
			JSONObject fullConfig = new JSONObject(gson.toJson(new RRMConfig()));
			Utils.jsonMerge(fullConfig, userConfig);
			config = gson.fromJson(fullConfig.toString(), RRMConfig.class);

			// Compare merged config with contents as read from disk
			// If any differences (ex. added fields), overwrite config file
			if (!fullConfig.toString().equals(userConfig.toString())) {
				logger.info("Rewriting config file with new changes...");
				try (Writer writer = new FileWriter(configFile)) {
					gson.toJson(config, writer);
				}
			}
		}
		return config;
	}

	@Command(
		name = "run",
		description = "Run the RRM service.",
		mixinStandardHelpOptions = true
	)
	private Integer run(
		@Option(
			names = { "-c", "--config-file" },
			paramLabel = "<FILE>",
			description = "RRM config file"
		)
		File configFile,

		@Option(
			names = { "--config-env" },
			description = "Read RRM config from environment variables (overrides --config-file)"
		)
		boolean configEnv,

		@Option(
			names = { "-t", "--topology-file" },
			paramLabel = "<FILE>",
			description = "Device topology file"
		)
		File topologyFile,

		@Option(
			names = { "-d", "--device-config-file" },
			paramLabel = "<FILE>",
			description = "Device layered config file"
		)
		File deviceLayeredConfigFile
	) throws Exception {
		// Read local files
		RRMConfig config;
		if (configEnv) {
			logger.info("Loading config from environment variables...");
			config = RRMConfig.fromEnv(System.getenv());
		} else {
			config = readRRMConfig(
				configFile != null ? configFile : DEFAULT_CONFIG_FILE
			);
		}

		DeviceDataManager deviceDataManager = new DeviceDataManager(
			topologyFile != null ? topologyFile : DEFAULT_TOPOLOGY_FILE,
			deviceLayeredConfigFile != null
				? deviceLayeredConfigFile
				: DEFAULT_DEVICE_LAYERED_CONFIG_FILE
		);

		String serviceKey =
			UCentralUtils.generateServiceKey(config.serviceConfig);

		// Instantiate clients
		UCentralClient client = new UCentralClient(
			config.serviceConfig.publicEndpoint,
			config.uCentralConfig.usePublicEndpoints,
			config.uCentralConfig.uCentralSecPublicEndpoint,
			config.uCentralConfig.username,
			config.uCentralConfig.password,
			config.uCentralConfig.uCentralSocketParams
		);
		UCentralKafkaConsumer consumer;
		UCentralKafkaProducer producer;
		if (config.kafkaConfig.bootstrapServer.isEmpty()) {
			logger.info("Kafka is disabled.");
			consumer = null;
			producer = null;
		} else {
			consumer = new UCentralKafkaConsumer(
				client,
				config.kafkaConfig.bootstrapServer,
				config.kafkaConfig.groupId,
				config.kafkaConfig.autoOffsetReset,
				config.kafkaConfig.stateTopic,
				config.kafkaConfig.wifiScanTopic,
				config.kafkaConfig.serviceEventsTopic
			);
			producer = new UCentralKafkaProducer(
				config.kafkaConfig.bootstrapServer,
				config.kafkaConfig.serviceEventsTopic,
				config.serviceConfig.name,
				VersionProvider.get(),
				config.serviceConfig.id,
				serviceKey,
				config.serviceConfig.privateEndpoint,
				config.serviceConfig.publicEndpoint
			);
		}
		DatabaseManager dbManager;
		if (config.databaseConfig.server.isEmpty()) {
			logger.info("Database manager is disabled.");
			dbManager = null;
		} else {
			dbManager = new DatabaseManager(
				config.databaseConfig.server,
				config.databaseConfig.user,
				config.databaseConfig.password,
				config.databaseConfig.dbName,
				config.databaseConfig.dataRetentionIntervalDays
			);
			dbManager.init();
		}

		// Start RRM service
		RRM rrm = new RRM();
		boolean success = rrm.start(
			config, deviceDataManager, client, consumer, producer, dbManager
		);
		if (dbManager != null) {
			dbManager.close();
		}
		return success ? 0 : 1;
	}

	@Command(
		name = "generate-rrm-config",
		description = "Generate the RRM config file.",
		mixinStandardHelpOptions = true
	)
	public Integer generateRRMConfig(
		@Option(
			names = { "-c", "--config-file" },
			paramLabel = "<FILE>",
			description = "RRM config file"
		)
		File configFile
	) throws Exception {
		if (configFile == null) {
			configFile = DEFAULT_CONFIG_FILE;
		}
		if (configFile.exists()) {
			logger.error(
				"File '{}' already exists, not overwriting...",
				configFile.getPath()
			);
			return 1;
		} else {
			logger.info("Writing config file to '{}'", configFile.getPath());
			Utils.writeJsonFile(configFile, new RRMConfig());
			return 0;
		}
	}

	@Command(
		name = "show-default-device-config",
		description = "Print the default device config.",
		mixinStandardHelpOptions = true
	)
	public Integer showDefaultDeviceConfig() throws Exception {
		Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.serializeNulls()  // for here only!!
			.create();
		logger.info(gson.toJson(DeviceConfig.createDefault()));
		return 0;
	}

	@Override
	public Integer call() {
		CommandLine.usage(this, System.out);
		return 1;
	}

	/** Main method. */
	public static void main(String[] args) throws Exception {
        int exitCode = new CommandLine(new Launcher()).execute(args);
        System.exit(exitCode);
	}
}
