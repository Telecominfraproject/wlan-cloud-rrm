/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralConstants;

/**
 * Random tx power initializer.
 * <p>
 * Random picks a single tx power and assigns the value to all APs.
 */
public class RandomTxPowerInitializer extends TPC {
	private static final Logger logger =
		LoggerFactory.getLogger(RandomTxPowerInitializer.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "random";

	/** Default value for setDifferentTxPowerPerAp */
	public static final boolean DEFAULT_SET_DIFFERENT_TX_POWER_PER_AP =
		false;

	/** The PRNG instance. */
	private final Random rng;

	/** Whether to set a different value per AP or use a single value for all APs */
	private final boolean setDifferentTxPowerPerAp;

	/** Factory method to parse generic args map into the proper constructor */
	public static RandomTxPowerInitializer makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		boolean setDifferentTxPowerPerAp =
			DEFAULT_SET_DIFFERENT_TX_POWER_PER_AP;

		String arg;
		if ((arg = args.get("setDifferentTxPowerPerAp")) != null) {
			setDifferentTxPowerPerAp = Boolean.parseBoolean(arg);
		}

		return new RandomTxPowerInitializer(
			model,
			zone,
			deviceDataManager,
			setDifferentTxPowerPerAp
		);
	}

	/**
	 * Constructor (uses random tx power per AP and allows passing in a custom
	 * Random class to allow seeding).
	 */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this(
			model,
			zone,
			deviceDataManager,
			DEFAULT_SET_DIFFERENT_TX_POWER_PER_AP
		);
	}

	/** Constructor (uses random tx power per AP). */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		boolean setDifferentTxPowerPerAp
	) {
		this(
			model,
			zone,
			deviceDataManager,
			setDifferentTxPowerPerAp,
			new Random()
		);
	}

	/**
	 * Constructor (uses random tx power per AP and allows passing in a custom
	 * Random class to allow seeding).
	 */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		boolean setDifferentTxPowerPerAp,
		Random rng
	) {
		super(model, zone, deviceDataManager);
		this.setDifferentTxPowerPerAp = setDifferentTxPowerPerAp;
		this.rng = rng;
	}

	/** Get a random tx power in [MIN_TX_POWER, MAX_TX_POWER], both inclusive */
	public int getRandomTxPower() {
		return rng.nextInt(TPC.MAX_TX_POWER + 1 - TPC.MIN_TX_POWER) +
			TPC.MIN_TX_POWER;
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		int defaultTxPower = this.getRandomTxPower();
		logger.info("Default power: {}", defaultTxPower);
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();
		for (String serialNumber : model.latestState.keySet()) {
			int txPower =
				setDifferentTxPowerPerAp ? getRandomTxPower() : defaultTxPower;
			Map<String, Integer> radioMap = new TreeMap<>();
			for (String band : UCentralConstants.BANDS) {
				radioMap.put(band, txPower);
			}
			txPowerMap.put(serialNumber, radioMap);

			logger.info(
				"Device {}: Assigning tx power = {}",
				serialNumber,
				txPower
			);
		}

		return txPowerMap;
	}
}
