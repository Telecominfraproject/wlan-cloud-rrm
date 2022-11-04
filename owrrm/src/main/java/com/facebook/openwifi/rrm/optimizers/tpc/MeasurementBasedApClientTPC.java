/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.optimizers.tpc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.cloudsdk.models.ap.Capabilities;
import com.facebook.openwifi.cloudsdk.UCentralUtils;
import com.facebook.openwifi.cloudsdk.StateInfo;
import com.facebook.openwifi.cloudsdk.models.ap.State;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.modules.Modeler.DataModel;
import com.facebook.openwifi.rrm.modules.ModelerUtils;

/**
 * Measurement-based AP-client algorithm.
 * <p>
 * Assign tx power based on client RSSI and a fixed target MCS index.
 */
public class MeasurementBasedApClientTPC extends TPC {
	private static final Logger logger =
		LoggerFactory.getLogger(MeasurementBasedApClientTPC.class);

	/** The RRM algorithm ID. */
	public static final String ALGORITHM_ID = "measure_ap_client";

	/** Default target MCS index. */
	public static final int DEFAULT_TARGET_MCS = 8;

	/** Default tx power. */
	public static final int DEFAULT_TX_POWER = 10;

	/** Default channel width in HMz */
	public static final int DEFAULT_CHANNEL_WIDTH = 20;

	/** Mapping of MCS index to required SNR (dB) in 802.11ac. */
	private static final List<Double> MCS_TO_SNR = Collections.unmodifiableList(
		Arrays.asList(
			/* MCS 0 */ 5.0,
			/* MCS 1 */ 7.5,
			/* MCS 2 */ 10.0,
			/* MCS 3 */ 12.5,
			/* MCS 4 */ 15.0,
			/* MCS 5 */ 17.5,
			/* MCS 6 */ 20.0,
			/* MCS 7 */ 22.5,
			/* MCS 8 */ 25.0,
			/* MCS 9 */ 27.5
		)
	);

	/** The target MCS index. */
	private final int targetMcs;

	/** Factory method to parse generic args map into the proper constructor */
	public static MeasurementBasedApClientTPC makeWithArgs(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		Map<String, String> args
	) {
		int targetMcs = DEFAULT_TARGET_MCS;

		String arg;
		if ((arg = args.get("targetMcs")) != null) {

			try {
				int parsedTargetMcs = Integer.parseInt(arg);
				if (targetMcs < 0) {
					logger.error(
						"Invalid value passed for targetMcs - must be greater than 0. Using default value."
					);
				} else {
					targetMcs = parsedTargetMcs;
				}
			} catch (NumberFormatException e) {
				logger.error(
					"Invalid integer passed to parameter targetMcs, using default value",
					e
				);
			}
		}

		return new MeasurementBasedApClientTPC(
			model,
			zone,
			deviceDataManager,
			targetMcs
		);
	}

	/** Constructor (uses default target MCS index). */
	public MeasurementBasedApClientTPC(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager
	) {
		this(model, zone, deviceDataManager, DEFAULT_TARGET_MCS);
	}

	/** Constructor. */
	public MeasurementBasedApClientTPC(
		DataModel model,
		String zone,
		DeviceDataManager deviceDataManager,
		int targetMcs
	) {
		super(model, zone, deviceDataManager);

		if (targetMcs < 0 || targetMcs >= MCS_TO_SNR.size()) {
			throw new RuntimeException("Invalid target MCS " + targetMcs);
		}
		this.targetMcs = targetMcs;
	}

	/**
	 * Compute adjusted tx power (dBm) based on inputs.
	 * @param mcs the MCS index
	 * @param currentTxPower the current tx power (dBm)
	 * @param clientRssi the minimum client RSSI (dBm)
	 * @param bandwidth the channel bandwidth (Hz)
	 */
	private double computeTxPower(
		int mcs,
		int currentTxPower,
		int clientRssi,
		int bandwidth
	) {
		// Tx power adjusted [dBm] =
		// SNR_min [dB] + Tx power [dBm] - R [dBm] + NP [dBm] + NF [dB] - M [dB]
		double SNR_min = // Signal-to-noise ratio minimum [dB]
			MCS_TO_SNR.get(mcs);
		final double k = 1.38e-23; // Boltzmann's constant
		final double T = 290; // Temperature
		double B = bandwidth; // Bandwidth (Hz)
		double NP = // Noise power (dBm) => 10*log_10(K*T*B*1000)
			10.0 * Math.log10(k * T * B * 1000.0);
		final double NF = 6; // Noise floor (6dB)
		final double M = 2; // Margin (2dB)

		return SNR_min + currentTxPower - clientRssi + NP + NF - M;
	}

	/** Compute new tx power (dBm) for the given radio. */
	private int computeTxPowerForRadio(
		String serialNumber,
		State state,
		State.Radio radio,
		List<Integer> txPowerChoices
	) {
		// Find current tx power and bandwidth
		int currentTxPower = radio.tx_power;
		// treat as one 160MHz channel vs two 80MHz channels
		Integer channelWidthMHz =
			UCentralUtils.parseChannelWidth(radio.channel_width, false);
		int channelWidth = (channelWidthMHz != null
			? channelWidthMHz : DEFAULT_CHANNEL_WIDTH) * 1_000_000; // convert MHz to HZ
		Collections.sort(txPowerChoices);
		int minTxPower = txPowerChoices.get(0);
		int maxTxPower = txPowerChoices.get(txPowerChoices.size() - 1);

		// Find minimum client RSSI
		List<Integer> clientRssiList = new ArrayList<>();
		if (state.interfaces != null) {
			for (State.Interface iface : state.interfaces) {
				if (iface.ssids == null) {
					continue;
				}
				for (State.Interface.SSID ssid : iface.ssids) {
					if (ssid.associations == null) {
						continue;
					}
					for (
						State.Interface.SSID.Association client : ssid.associations
					) {
						logger.debug(
							"Device {}: SSID '{}' => client {} with rssi {}",
							serialNumber,
							ssid.ssid != null ? ssid.ssid : "",
							client.bssid != null ? client.bssid : "",
							client.rssi
						);
						clientRssiList.add(client.rssi);
					}
				}
			}
		}
		// TODO: revisit this part to have a better logic
		if (clientRssiList.isEmpty()) {
			logger.info(
				"Device {}: no clients, assigning minimal possible tx power {} (was {})",
				serialNumber,
				minTxPower,
				currentTxPower
			);
			return minTxPower; // no clients
		}
		int clientRssi = Collections.min(clientRssiList);

		// Compute new tx power
		int newTxPower;
		int mcs = targetMcs;
		do {
			double computedTxPower =
				computeTxPower(mcs, currentTxPower, clientRssi, channelWidth);

			// APs only accept integer tx power, so take ceiling
			newTxPower = (int) Math.ceil(computedTxPower);

			logger.info(
				"Device {}: computed tx power (for mcs={}, currentTxPower={}, rssi={}, bandwidth={}) = {}, ceil() = {}",
				serialNumber,
				mcs,
				currentTxPower,
				clientRssi,
				channelWidth,
				computedTxPower,
				newTxPower
			);

			// If this exceeds max possible tx power, repeat for (MCS - 1)
			if (newTxPower > maxTxPower) {
				logger.info(
					"Device {}: computed tx power > maximum {}, trying with mcs - 1",
					serialNumber,
					maxTxPower
				);
				if (--mcs >= 0) {
					continue;
				} else {
					logger.info(
						"Device {}: already at lowest MCS, setting to minimum tx power {}",
						serialNumber,
						minTxPower
					);
					newTxPower = minTxPower;
				}
			}

			// If this is below min tx power, set to min
			else if (newTxPower < minTxPower) {
				logger.info(
					"Device {}: computed tx power < minimum {}, using minimum",
					serialNumber,
					minTxPower
				);
				newTxPower = minTxPower;
			}

			break;
		} while (true);

		int txPowerIndex = 0;
		for (
			txPowerIndex = 0;
			txPowerIndex < txPowerChoices.size() &&
				txPowerChoices.get(txPowerIndex) < newTxPower;
			txPowerIndex++
		) {

		}
		if (
			txPowerIndex > 0 && Math.abs(
				txPowerChoices.get(txPowerIndex - 1) - newTxPower
			) < Math.abs(txPowerChoices.get(txPowerIndex) - newTxPower)
		) {
			txPowerIndex--;
		}
		newTxPower = txPowerChoices.get(txPowerIndex);
		logger.info(
			"Device {}: assigning tx power = {} (was {})",
			serialNumber,
			newTxPower,
			currentTxPower
		);
		return newTxPower;
	}

	@Override
	public Map<String, Map<String, Integer>> computeTxPowerMap() {
		Map<String, Map<String, Integer>> txPowerMap = new TreeMap<>();

		for (
			Map.Entry<String, List<StateInfo>> e : model.latestStates.entrySet()
		) {
			String serialNumber = e.getKey();
			List<StateInfo> states = e.getValue();
			StateInfo state = states.get(states.size() - 1);
			if (state.radios == null || state.radios.length == 0) {
				logger.debug(
					"Device {}: No radios found, skipping...",
					serialNumber
				);
				continue;
			}

			Map<String, Integer> radioMap = new TreeMap<>();
			for (StateInfo.Radio radio : state.radios) {
				Map<String, Capabilities.Phy> capabilityPhy =
					model.latestDeviceCapabilitiesPhy
						.get(serialNumber);
				if (capabilityPhy == null) {
					continue;
				}
				final String band = ModelerUtils.getBand(
					radio,
					capabilityPhy
				);
				if (band == null) {
					continue;
				}
				List<Integer> txPowerChoices = updateTxPowerChoices(
					band,
					serialNumber,
					DEFAULT_TX_POWER_CHOICES
				);
				int newTxPower =
					computeTxPowerForRadio(
						serialNumber,
						state,
						radio,
						txPowerChoices
					);

				radioMap.put(band, newTxPower);
			}
			txPowerMap.put(serialNumber, radioMap);
		}

		return txPowerMap;
	}
}
