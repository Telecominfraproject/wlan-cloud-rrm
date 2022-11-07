/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.rrm.modules.ConfigManager;
import com.facebook.openwifi.rrm.modules.Modeler;
import com.facebook.openwifi.rrm.optimizers.channel.ChannelOptimizer;
import com.facebook.openwifi.rrm.optimizers.channel.LeastUsedChannelOptimizer;
import com.facebook.openwifi.rrm.optimizers.channel.RandomChannelInitializer;
import com.facebook.openwifi.rrm.optimizers.channel.UnmanagedApAwareChannelOptimizer;
import com.facebook.openwifi.rrm.optimizers.clientsteering.ClientSteeringOptimizer;
import com.facebook.openwifi.rrm.optimizers.clientsteering.ClientSteeringState;
import com.facebook.openwifi.rrm.optimizers.clientsteering.SingleAPBandSteering;
import com.facebook.openwifi.rrm.optimizers.tpc.LocationBasedOptimalTPC;
import com.facebook.openwifi.rrm.optimizers.tpc.MeasurementBasedApApTPC;
import com.facebook.openwifi.rrm.optimizers.tpc.MeasurementBasedApClientTPC;
import com.facebook.openwifi.rrm.optimizers.tpc.RandomTxPowerInitializer;
import com.facebook.openwifi.rrm.optimizers.tpc.TPC;

/**
 * RRM algorithm model and utility methods.
 */
public class RRMAlgorithm {
	private static final Logger logger =
		LoggerFactory.getLogger(RRMAlgorithm.class);

	private static final ClientSteeringState clientSteeringState =
		new ClientSteeringState();

	/** RRM algorithm type enum. */
	public enum AlgorithmType {
		OptimizeChannel (
			"Optimize channel configuration",
			"Run channel optimization algorithm"
		),
		OptimizeTxPower (
			"Optimize tx power configuration",
			"Run transmit power control algorithm"
		),
		ClientSteering (
			"Steer clients onto the optimal AP and band",
			"Run client steering algorithm"
		);

		/** The long name. */
		public final String longName;
		/** The description. */
		public final String description;

		/** Constructor. */
		AlgorithmType(String longName, String description) {
			this.longName = longName;
			this.description = description;
		}
	}

	/** RRM algorithm result. */
	public static class AlgorithmResult {
		/** The error string, only set upon failure to execute the algorithm. */
		public String error;

		/**
		 * Computed channel assignments.
		 * @see ChannelOptimizer#computeChannelMap()
		 */
		public Map<String, Map<String, Integer>> channelMap;

		/**
		 * Computed tx power assignments.
		 * @see TPC#computeTxPowerMap()
		 */
		public Map<String, Map<String, Integer>> txPowerMap;

		/**
		 * Computed actions for each AP-client pair.
		 *
		 * @see ClientSteeringOptimizer#computeApClientActionMap()
		 */
		public Map<String, Map<String, String>> apClientActionMap;
	}

	/** The algorithm name (should be AlgorithmType enum string). */
	private final String name;

	/** The algorithm arguments. */
	private final Map<String, String> args;

	/** Constructor (with no arguments). */
	public RRMAlgorithm(String name) {
		this(name, null);
	}

	/** Constructor. */
	public RRMAlgorithm(String name, Map<String, String> args) {
		if (name == null) {
			throw new NullPointerException("name is null");
		}
		this.name = name;
		this.args = args != null ? args : new HashMap<>(0);
	}

	/**
	 * Construct an RRMAlgorithm with a raw arguments string.
	 *
	 * @param name the algorithm name, which must exist in {@link AlgorithmType}
	 * @param argsRaw the arguments as a comma-separated list of key=value
	 *                pairs, e.g. {@code key1=val1,key2=val2,key3=val3}
	 *
	 * @return the parsed object, or null if parsing failed
	 */
	public static RRMAlgorithm parse(String name, String argsRaw) {
		try {
			AlgorithmType.valueOf(name);
		} catch (IllegalArgumentException e) {
			logger.error("Parse error, unknown algorithm name: '{}'", name);
			return null; // unsupported algorithm
		}

		Map<String, String> args = new HashMap<>();
		for (String s : argsRaw.split(",")) {
			if (s.isEmpty()) {
				continue;
			}
			String[] kv = s.split("=", 2);
			if (kv.length != 2) {
				logger.error("Parse error, invalid key=value arg: '{}'", s);
				return null; // invalid key-value pair
			}
			if (args.putIfAbsent(kv[0], kv[1]) != null) {
				logger.error("Parse error, duplicate arg: '{}'", kv[0]);
				return null; // duplicate key
			}
		}

		return new RRMAlgorithm(name, args);
	}

	/** Return the algorithm name. */
	public String getName() { return name; }

	/**
	 * Run the algorithm.
	 *
	 * @param deviceDataManager the device data manager
	 * @param configManager the ConfigManager module instance
	 * @param modeler the Modeler module instance
	 * @param zone the RF zone
	 * @param dryRun if set, do not apply changes
	 * @param allowDefaultMode if false, "mode" argument must be present and
	 *                         valid (returns error if invalid)
	 * @param updateImmediately true if the method should queue the zone for
	 * 							update and interrupt the config manager thread
	 * 							to trigger immediate update
	 *
	 * @return the algorithm result, with exactly one field set ("error" upon
	 *         failure, any others upon success)
	 */
	public AlgorithmResult run(
		DeviceDataManager deviceDataManager,
		ConfigManager configManager,
		Modeler modeler,
		String zone,
		boolean dryRun,
		boolean allowDefaultMode,
		boolean updateImmediately
	) {
		AlgorithmResult result = new AlgorithmResult();
		if (name == null || args == null) {
			result.error = "Null algorithm name or arguments?"; // shouldn't happen
			return result;
		}

		// Get mode (i.e. specific algorithm)
		String mode = args.getOrDefault("mode", "");
		String modeErrorStr = String.format(
			"Unknown mode '%s' provided for algorithm '%s'",
			mode,
			name
		);

		// Find algorithm to run
		if (name.equals(RRMAlgorithm.AlgorithmType.OptimizeChannel.name())) {
			logger.info(
				"Zone '{}': Running channel optimizer (mode='{}')",
				zone,
				mode
			);
			ChannelOptimizer optimizer;
			switch (mode) {
			default:
				if (!allowDefaultMode || !mode.isEmpty()) {
					result.error = modeErrorStr;
					return result;
				}
				logger.info("Using default algorithm mode...");
				// fall through
			case UnmanagedApAwareChannelOptimizer.ALGORITHM_ID:
				optimizer = UnmanagedApAwareChannelOptimizer.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			case RandomChannelInitializer.ALGORITHM_ID:
				optimizer = RandomChannelInitializer.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			case LeastUsedChannelOptimizer.ALGORITHM_ID:
				optimizer = LeastUsedChannelOptimizer.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			}
			result.channelMap = optimizer.computeChannelMap();
			if (!dryRun) {
				optimizer.updateDeviceApConfig(
					deviceDataManager,
					configManager,
					result.channelMap
				);
				if (updateImmediately) {
					configManager.queueZoneAndWakeUp(zone);
				}
			}
		} else if (
			name.equals(RRMAlgorithm.AlgorithmType.OptimizeTxPower.name())
		) {
			logger.info(
				"Zone '{}': Running tx power optimizer (mode='{}')",
				zone,
				mode
			);
			TPC optimizer;
			switch (mode) {
			default:
				if (!allowDefaultMode || !mode.isEmpty()) {
					result.error = modeErrorStr;
					return result;
				}
				logger.info("Using default algorithm mode...");
				// fall through
			case MeasurementBasedApApTPC.ALGORITHM_ID:
				optimizer = MeasurementBasedApApTPC.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			case RandomTxPowerInitializer.ALGORITHM_ID:
				optimizer = RandomTxPowerInitializer.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			case MeasurementBasedApClientTPC.ALGORITHM_ID:
				optimizer = MeasurementBasedApClientTPC.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			case LocationBasedOptimalTPC.ALGORITHM_ID:
				optimizer = LocationBasedOptimalTPC.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					args
				);
				break;
			}
			result.txPowerMap = optimizer.computeTxPowerMap();
			if (!dryRun) {
				optimizer.updateDeviceApConfig(
					deviceDataManager,
					configManager,
					result.txPowerMap
				);
				if (updateImmediately) {
					configManager.queueZoneAndWakeUp(zone);
				}
			}
		} else if (
			name.equals(RRMAlgorithm.AlgorithmType.ClientSteering.name())
		) {
			logger.info(
				"Zone '{}': Running client steering optimizer (mode='{}')",
				zone,
				mode
			);
			ClientSteeringOptimizer optimizer;
			switch (mode) {
			default:
				if (!allowDefaultMode || !mode.isEmpty()) {
					result.error = modeErrorStr;
					return result;
				}
				logger.info("Using default algorithm mode...");
				// fall through
			case SingleAPBandSteering.ALGORITHM_ID:
				optimizer = SingleAPBandSteering.makeWithArgs(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager,
					clientSteeringState,
					args
				);
				break;
			}
			result.apClientActionMap = optimizer.computeApClientActionMap();
			if (!dryRun) {
				optimizer.steer(result.apClientActionMap);
				if (updateImmediately) {
					configManager.queueZoneAndWakeUp(zone);
				}
			}
		} else {
			result.error = String.format("Unknown algorithm: '%s'", name);
		}
		return result;
	}
}
