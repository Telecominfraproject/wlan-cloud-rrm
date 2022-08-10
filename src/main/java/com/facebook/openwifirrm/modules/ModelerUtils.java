/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.aggregators.Aggregator;
import com.facebook.openwifirrm.modules.operationelement.HTOperationElement;
import com.facebook.openwifirrm.modules.operationelement.VHTOperationElement;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;

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

	/**
	 * Determines if two wifiscan entries should be aggregated without consideration
	 * for any kind of obsoletion period. (Untested) should handle APs that support
	 * pre-802.11n standards (no ht_oper and no vht_oper), 802.11n (supports ht_oper
	 * but no vht_oper), and 802.11ac (supports both ht_oper and vht_oper).
	 * Pre-802.11n, if two entries with the same bssid are in the same channel (and
	 * therefore frequency, since they share a one-to-one mapping), that is enough
	 * to aggregate them. However, 802.11n introduced channel bonding, so for
	 * 802.11n onwards, channel width must be checked.
	 *
	 * @param entry1
	 * @param entry2
	 * @return true if the entries should be aggregated
	 */
	private static boolean matchesForAggregation(WifiScanEntry entry1, WifiScanEntry entry2) {
		// TODO should we check the entire ht_oper and vht_oper or just channel width?
		return Objects.equals(entry1.bssid, entry2.bssid) && Objects.equals(entry1.ssid, entry2.ssid)
				&& entry1.frequency == entry2.frequency && entry1.channel == entry2.channel
				&& HTOperationElement.matchesHtForAggregation(entry1.ht_oper, entry2.ht_oper)
				&& VHTOperationElement.matchesVhtForAggregation(entry1.vht_oper, entry2.vht_oper);
	}

	/**
	 * For each BSSID, calculates an aggregate wifiscan entry with an aggregated
	 * RSSI.
	 *
	 * @param dataModel
	 * @param obsoletionPeriodMs per-BSSID, the maximum amount of time (in
	 *                           milliseconds) it is worth aggregating over,
	 *                           starting from the most recent scan entry and
	 *                           working backwards in time on a per-BSSID basis
	 *                           (per-BSSID basis meaning that this method takes the
	 *                           most recent scan entry for a specific BSSID and
	 *                           looks back from there and repeats this for every
	 *                           BSSID). An entry exactly {@code obsoletionPeriodMs}
	 *                           ms earlier than the most recent entry is considered
	 *                           non-obsolete (i.e., the "non-obsolete" window is
	 *                           inclusive). Must be non-negative.
	 * @param agg                an aggregator to output a calculated "aggregated
	 *                           signal strength"
	 * @return a map from BSSID to an "aggregated wifiscan entry" (i.e., a single
	 *         entry with its {@code signal} attribute modified to be the aggregated
	 *         signal value instead of the value in just the most recent entry.
	 */
	public static Map<String, WifiScanEntry> getAggregatedWifiScans(Modeler.DataModel dataModel,
			long obsoletionPeriodMs,
			Aggregator<Double> agg) {
		if (obsoletionPeriodMs < 0) {
			throw new IllegalArgumentException("obsoletionPeriodMs must be non-negative.");
		}
		/*
		 * NOTE: if a BSSID does not have an entry, it will not be returned
		 * (i.e., it will not be a key in the returned map).
		 */
		Map<String, WifiScanEntry> aggregatedWifiScans = new HashMap<>();
		for (Map.Entry<String, List<List<WifiScanEntry>>> mapEntry : dataModel.latestWifiScans.entrySet()) {
			/*
			 * Flatten the wifiscan entries and sort in reverse chronological order. Sorting
			 * is done just in case the entries in the original list are not chronological
			 * already - although they are inserted chronologically, perhaps latency,
			 * synchronization, etc. could cause the actual unixTimeMs to be out-of-order.
			 */
			List<List<WifiScanEntry>> scans = mapEntry.getValue();
			if (scans.isEmpty()) {
				continue;
			}
			List<WifiScanEntry> mostRecentToOldest = scans.stream().flatMap(List::stream)
					.sorted((entry1, entry2) -> {
						return -Long.compare(entry1.unixTimeMs, entry2.unixTimeMs);
					}).collect(Collectors.toUnmodifiableList());

			String bssid = mapEntry.getKey();
			WifiScanEntry mostRecentEntry = mostRecentToOldest.get(0);
			agg.reset();
			for (WifiScanEntry entry : mostRecentToOldest) {
				if (mostRecentEntry.unixTimeMs - entry.unixTimeMs > obsoletionPeriodMs) {
					// discard obsolete entries
					break;
				}
				// mostRecentEntry should always match the first entry (they are the exact same
				// object)
				if (matchesForAggregation(mostRecentEntry, entry)) {
					aggregatedWifiScans.putIfAbsent(bssid, new WifiScanEntry(entry));
					agg.addValue((double) entry.signal);
				}
			}
			aggregatedWifiScans.get(bssid).signal = (int) Math.round(agg.getAggregate());
		}
		return aggregatedWifiScans;
	}
}
