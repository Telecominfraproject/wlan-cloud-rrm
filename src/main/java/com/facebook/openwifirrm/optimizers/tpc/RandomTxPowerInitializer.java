/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.optimizers.tpc;

import java.util.Map;
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
	private static final Logger logger = LoggerFactory.getLogger(RandomTxPowerInitializer.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "random";

	/** Default tx power. */
	public static final int DEFAULT_TX_POWER = 23;

	/** The fixed tx power (dBm). */
	private final int txPower;

	/** Constructor (uses default tx power). */
	public RandomTxPowerInitializer(
		DataModel model, String zone, DeviceDataManager deviceDataManager
	) {
		this(model, zone, deviceDataManager, DEFAULT_TX_POWER);
	}

	/** Constructor. */
	public RandomTxPowerInitializer(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		int txPower
	) {
		super(model, zone, deviceDataManager);
		this.txPower = txPower;
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();
		for (String serialNumber : model.latestState.keySet()) {
			Map<String, Integer> radioMap = new TreeMap<>();
			radioMap.put(UCentralConstants.BAND_5G, txPower);
			txPowerMap.put(serialNumber, radioMap);
		}
		if (!txPowerMap.isEmpty()) {
			logger.info(
				"Device {}: Assigning tx power = {}",
				String.join(", ", txPowerMap.keySet()),
				txPower
			);
		}
		return txPowerMap;
	}
}
