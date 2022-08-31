/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.modules.ConfigManager;
import com.facebook.openwifirrm.modules.Modeler;
import com.facebook.openwifirrm.optimizers.channel.ChannelOptimizer;
import com.facebook.openwifirrm.optimizers.channel.LeastUsedChannelOptimizer;
import com.facebook.openwifirrm.optimizers.channel.RandomChannelInitializer;
import com.facebook.openwifirrm.optimizers.channel.UnmanagedApAwareChannelOptimizer;
import com.facebook.openwifirrm.optimizers.tpc.LocationBasedOptimalTPC;
import com.facebook.openwifirrm.optimizers.tpc.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.tpc.MeasurementBasedApClientTPC;
import com.facebook.openwifirrm.optimizers.tpc.RandomTxPowerInitializer;
import com.facebook.openwifirrm.optimizers.tpc.TPC;

/**
 * RRM algorithm model and utility methods.
 */
public class RRMAlgorithm {
	private static final Logger logger =
		LoggerFactory.getLogger(RRMAlgorithm.class);

	/** RRM algorithm type enum. */
	public enum AlgorithmType {
		OptimizeChannel (
			"Optimize channel configuration",
			"Run channel optimization algorithm"
		),
		OptimizeTxPower (
			"Optimize tx power configuration",
			"Run transmit power control algorithm"
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
		boolean allowDefaultMode
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
				optimizer = new UnmanagedApAwareChannelOptimizer(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			case RandomChannelInitializer.ALGORITHM_ID:
				optimizer = new RandomChannelInitializer(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			case LeastUsedChannelOptimizer.ALGORITHM_ID:
				optimizer = new LeastUsedChannelOptimizer(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			}
			result.channelMap = optimizer.computeChannelMap();
			if (!dryRun) {
				optimizer.applyConfig(
					deviceDataManager,
					configManager,
					result.channelMap
				);
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
				optimizer = new MeasurementBasedApApTPC(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			case RandomTxPowerInitializer.ALGORITHM_ID:
				optimizer = new RandomTxPowerInitializer(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			case MeasurementBasedApClientTPC.ALGORITHM_ID:
				optimizer = new MeasurementBasedApClientTPC(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			case LocationBasedOptimalTPC.ALGORITHM_ID:
				optimizer = new LocationBasedOptimalTPC(
					modeler.getDataModelCopy(),
					zone,
					deviceDataManager
				);
				break;
			}
			result.txPowerMap = optimizer.computeTxPowerMap();
			if (!dryRun) {
				optimizer.applyConfig(
					deviceDataManager,
					configManager,
					result.txPowerMap
				);
			}
		} else {
			result.error = String.format("Unknown algorithm: '%s'", name);
		}
		return result;
	}
}
