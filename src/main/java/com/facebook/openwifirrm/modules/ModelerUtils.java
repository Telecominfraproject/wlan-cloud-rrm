/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modeler utilities.
 */
public class ModelerUtils {
	private static final Logger logger = LoggerFactory.getLogger(ModelerUtils.class);

	/** The pathloss exponent for mapping the distance (in meters) to prop loss (in dB).*/
	private static final double PATHLOSS_EXPONENT = 2;

	/** The net loss/gain in the link budget, including all antenna gains/system loss. */
	private static final double LINKBUDGET_FACTOR = 20;

	/** The default noise power in dBm depending on the BW (should be adjusted later). */
	private static final double NOISE_POWER = -94;

	/** The guaranteed SINR threshold in dB */
	private static final double SINR_THRESHOLD = 20;

	/** The guaranteed RSL value in dBm */
	private static final double RX_THRESHOLD = -80;

	/** The pre defined target coverage in percentage. */
	private static final double COVERAGE_THRESHOLD = 70;

	// This class should not be instantiated.
	private ModelerUtils() {}

	/**
	 * Get the RX power over the map for all the APs
	 * @param sampleSpace the boundary of the space
	 * @param numOfAPs the number of APs
	 * @param apLocX the location x of the APs
	 * @param apLocY the location y of the APs
	 * @param txPower the TX power of the APs
	 * @return the RX power of location x, location y, and AP index
	 */
	public static double[][][] generateRxPower(
		int sampleSpace,
		int numOfAPs,
		List<Double> apLocX,
		List<Double> apLocY,
		List<Double> txPower
	) {
		if (
			apLocX == null ||
			apLocX.size() != numOfAPs ||
			apLocY == null ||
			apLocY.size() != numOfAPs ||
			txPower == null ||
			txPower.size() != numOfAPs
		) {
			throw new IllegalArgumentException("Invalid input data");
		}

		double[][][] rxPower = new double[sampleSpace][sampleSpace][numOfAPs];
		for (int xIndex = 0; xIndex < sampleSpace; xIndex++) {
			for (int yIndex = 0; yIndex < sampleSpace; yIndex++) {
				for (int apIndex = 0; apIndex < numOfAPs; apIndex++) {
					if (
						apLocX.get(apIndex) > sampleSpace ||
						apLocY.get(apIndex) > sampleSpace
					) {
						logger.error(
							"The location of the AP is out of range."
						);
						return null;
					}
					double distance = Math.sqrt(
						Math.pow((apLocX.get(apIndex) - xIndex), 2) +
						Math.pow((apLocY.get(apIndex) - yIndex), 2)
					);
					double rxPowerTmp = txPower.get(apIndex)
						- LINKBUDGET_FACTOR
						- 20*PATHLOSS_EXPONENT*Math.log10(distance + 0.01);
					rxPower[xIndex][yIndex][apIndex] = Math.min(rxPowerTmp, -30);
				}
			}
		}
		return rxPower;
	}

	/**
	 * Get the heatmap over the map
	 * @param sampleSpace the boundary of the space
	 * @param numOfAPs the number of APs
	 * @param rxPower the RX power of location x, location y, and AP index
	 * @return the max RX power of location x and location y
	 */
	public static double[][] generateHeatMap(
		int sampleSpace,
		int numOfAPs,
		double[][][] rxPower
	) {
		if (rxPower == null) {
			throw new IllegalArgumentException("Invalid input data");
		}

		double[][] rxPowerBest = new double[sampleSpace][sampleSpace];
		for (int xIndex = 0; xIndex < sampleSpace; xIndex++) {
			for (int yIndex = 0; yIndex < sampleSpace; yIndex++) {
				double rxPowerBestTmp = Double.NEGATIVE_INFINITY;
				for (int apIndex = 0; apIndex < numOfAPs; apIndex++) {
					rxPowerBestTmp = Math.max(
						rxPowerBestTmp,
						rxPower[xIndex][yIndex][apIndex]
					);
				}
				rxPowerBest[xIndex][yIndex] = rxPowerBestTmp;
			}
		}
		return rxPowerBest;
	}

	/**
	 * Get the max SINR over the map
	 * @param sampleSpace the boundary of the space
	 * @param numOfAPs the number of APs
	 * @param rxPower the RX power of location x, location y, and AP index
	 * @return the max SINR of location x and location y
	 */
	public static double[][] generateSinr(
		int sampleSpace,
		int numOfAPs,
		double[][][] rxPower
	) {
		if (rxPower == null) {
			throw new IllegalArgumentException("Invalid input data");
		}

		double[][] sinrDB = new double[sampleSpace][sampleSpace];
		for (int xIndex = 0; xIndex < sampleSpace; xIndex++) {
			for (int yIndex = 0; yIndex < sampleSpace; yIndex++) {
				double maxSinr = Double.NEGATIVE_INFINITY;
				for (int apIndex = 0; apIndex < numOfAPs; apIndex++) {
					double denominator = 0.0;
					for (int apIndex2 = 0; apIndex2 < numOfAPs; apIndex2++) {
						if (apIndex == apIndex2) {
							continue;
						}
						denominator += Math.pow(
							10,
							rxPower[xIndex][yIndex][apIndex2]/10.0
						);
					}
					denominator += Math.pow(10, NOISE_POWER/10.0);
					double sinrLinear = Math.pow(
						10,
						rxPower[xIndex][yIndex][apIndex]/10.0
					)/denominator;
					maxSinr = Math.max(maxSinr, 10.0*Math.log10(sinrLinear));
				}
				sinrDB[xIndex][yIndex] = maxSinr;
			}
		}
		return sinrDB;
	}

	/**
	 * Get the coverage metrics for the TPC algorithm design
	 * @param sampleSpace the boundary of the space
	 * @param rxPowerBest the max RX power of location x and location y
	 * @param sinrDB the max SINR of location x and location y
	 * @return the combined metric of over and under coverage, infinity if error
	 */
	public static double calculateTPCMetrics(
		int sampleSpace,
		double[][] rxPowerBest,
		double[][] sinrDB
	) {
		int rxPowerCount = 0;
		int sinrCount = 0;
		for (int xIndex = 0; xIndex < sampleSpace; xIndex++) {
			for (int yIndex = 0; yIndex < sampleSpace; yIndex++) {
				if (rxPowerBest[xIndex][yIndex] <= RX_THRESHOLD) {
					rxPowerCount++;
				}
				if (sinrDB[xIndex][yIndex] <= SINR_THRESHOLD) {
					sinrCount++;
				}
			}
		}
		double rxPowerPercentage = rxPowerCount/Math.pow(sampleSpace, 2);
		double sinrPercentage = sinrCount/Math.pow(sampleSpace, 2);
		if (rxPowerPercentage*100.0 < 100.0 - COVERAGE_THRESHOLD) {
			return sinrPercentage;
		} else {
			return Double.POSITIVE_INFINITY;
		}
	}
}
