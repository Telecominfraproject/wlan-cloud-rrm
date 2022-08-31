/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.List;
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


	/** Factory method to parse generic args map into the proper constructor */
	public static RandomTxPowerInitializer makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		return new RandomTxPowerInitializer(
			model,
			zone,
			deviceDataManager
		);
	}


	/** Constructor. */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this(
			model,
			zone,
			deviceDataManager,
			new Random()
		);
	}

	/**
	 * Constructor (allows passing in a custom Random class to allow seeding).
	 */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Random rng
	) {
		super(model, zone, deviceDataManager);
		this.rng = rng;
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();
		for (String serialNumber : model.latestState.keySet()) {
			Map<String, Integer> radioMap = new TreeMap<>();
			for (String band : UCentralConstants.BANDS) {
				List<Integer> availableTxPowersList =
					updateTxPowerChoices(band, serialNumber, DEFAULT_TX_POWER_CHOICES);
				Integer txPower = availableTxPowersList
					.get(rng.nextInt(availableTxPowersList.size()));
				radioMap.put(band, txPower);
				logger.info(
					"Device {} band {}: Assigning tx power = {}",
					serialNumber,
					band,
					txPower
				);
			}
			txPowerMap.put(serialNumber, radioMap);
		}

		return txPowerMap;
	}
}
