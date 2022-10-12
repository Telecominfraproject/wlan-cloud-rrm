/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import com.facebook.openwifirrm.ucentral.UCentralUtils;
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

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Integer defaultTxPower = MAX_TX_POWER;
		if (!setDifferentTxPowerPerAp) {
			List<Integer> txPowerChoices =
				new ArrayList<>(DEFAULT_TX_POWER_CHOICES);
			for (String serialNumber : model.latestStates.keySet()) {
				for (String band : UCentralConstants.BANDS) {
					txPowerChoices = updateTxPowerChoices(
						band,
						serialNumber,
						txPowerChoices
					);
				}
			}

			defaultTxPower =
				txPowerChoices.get(rng.nextInt(txPowerChoices.size()));
			logger.info("Default power: {}", defaultTxPower);
		}

		Map<Integer, List<String>> apsPerChannel = getApsPerChannel();
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();

		for (Map.Entry<Integer, List<String>> e : apsPerChannel.entrySet()) {
			int channel = e.getKey();
			List<String> serialNumbers = e.getValue();

			String band = UCentralUtils.getBandFromChannel(channel);
			for (String serialNumber : serialNumbers) {
				int txPower = defaultTxPower;
				if (setDifferentTxPowerPerAp) {
					List<Integer> curTxPowerChoices = updateTxPowerChoices(
						band,
						serialNumber,
						DEFAULT_TX_POWER_CHOICES
					);
					txPower = curTxPowerChoices
						.get(rng.nextInt(curTxPowerChoices.size()));
				}
				txPowerMap.computeIfAbsent(serialNumber, k -> new TreeMap<>())
					.put(band, txPower);
				logger.info(
					"Device {} band {}: Assigning tx power = {}",
					serialNumber,
					band,
					txPower
				);
			}
		}

		return txPowerMap;
	}
}
