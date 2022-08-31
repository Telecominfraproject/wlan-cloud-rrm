/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.aggregators.Aggregator;
import com.facebook.openwifirrm.aggregators.MeanAggregator;
import com.facebook.openwifirrm.modules.Modeler.DataModel;
import com.facebook.openwifirrm.ucentral.UCentralUtils.WifiScanEntry;
import com.facebook.openwifirrm.ucentral.operationelement.HTOperationElement;
import com.facebook.openwifirrm.ucentral.operationelement.VHTOperationElement;

/**
 * Modeler utilities.
 */
public class ModelerUtils {
	private static final Logger logger =
		LoggerFactory.getLogger(ModelerUtils.class);

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
					double rxPowerTmp = txPower.get(apIndex) -
						LINKBUDGET_FACTOR -
						20 * PATHLOSS_EXPONENT * Math.log10(distance + 0.01);
					rxPower[xIndex][yIndex][apIndex] =
						Math.min(rxPowerTmp, -30);
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
							rxPower[xIndex][yIndex][apIndex2] / 10.0
						);
					}
					denominator += Math.pow(10, NOISE_POWER / 10.0);
					double sinrLinear = Math.pow(
						10,
						rxPower[xIndex][yIndex][apIndex] / 10.0
					) / denominator;
					maxSinr = Math.max(maxSinr, 10.0 * Math.log10(sinrLinear));
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
		double rxPowerPercentage = rxPowerCount / Math.pow(sampleSpace, 2);
		double sinrPercentage = sinrCount / Math.pow(sampleSpace, 2);
		if (rxPowerPercentage * 100.0 < 100.0 - COVERAGE_THRESHOLD) {
			return sinrPercentage;
		} else {
			return Double.POSITIVE_INFINITY;
		}
	}

	/**
	 * Determines if two wifiscan entries should be aggregated without consideration
	 * for any kind of obsoletion period. This should handle APs that support
	 * pre-802.11n standards (no ht_oper and no vht_oper), 802.11n (supports ht_oper
	 * but no vht_oper), and 802.11ac (supports both ht_oper and vht_oper).
	 * Pre-802.11n, if two entries with the same bssid are in the same channel (and
	 * therefore frequency, since they share a one-to-one mapping), that is enough
	 * to aggregate them. However, 802.11n introduced channel bonding, so for
	 * 802.11n onwards, channel width must be checked.
	 *
	 * @return true if the entries should be aggregated
	 */
	private static boolean matchesForAggregation(
		WifiScanEntry entry1,
		WifiScanEntry entry2
	) {
		// TODO test on real pre-802.11n APs (which do not have ht_oper and vht_oper)
		// do not check SSID (other SSIDs can contribute to interference, and SSIDs can
		// change any time)
		return Objects.equals(entry1.bssid, entry2.bssid) &&
			entry1.frequency == entry2.frequency &&
			entry1.channel == entry2.channel &&
			HTOperationElement
				.matchesHtForAggregation(entry1.ht_oper, entry2.ht_oper) &&
			VHTOperationElement
				.matchesVhtForAggregation(entry1.vht_oper, entry2.vht_oper);
	}

	/**
	 * For each AP, for each other AP that sent a wifiscan entry to that AP,
	 * this method calculates an aggregate wifiscan entry with an aggregated
	 * RSSI. If no non-obsolete entry exists, the latest wifiscan entry is used
	 * instead.
	 *
	 * @param dataModel          the data model which includes the latest
	 *                           wifiscan entries
	 * @param obsoletionPeriodMs for each (scanning AP, responding AP) tuple,
	 *                           the maximum amount of time (in milliseconds) it
	 *                           is worth aggregating over, starting from the
	 *                           most recent scan entry for that tuple, and
	 *                           working backwards in time. An entry exactly
	 *                           {@code obsoletionPeriodMs} ms earlier than the
	 *                           most recent entry is considered non-obsolete
	 *                           (i.e., the "non-obsolete" window is inclusive).
	 *                           Must be non-negative.
	 * @param agg                an aggregator to calculate the aggregated RSSI
	 *                           given recent wifiscan entries' RSSIs.
	 * @return a map from AP serial number to a map from BSSID to a
	 *         {@code WifiScanEntry} object. This object is an "aggregated
	 *         wifiscan entry" unless there is no non-obsolete wifiscan entry,
	 *         in which case the latest wifiscan entry is used. An aggregated
	 *         entry is just the latest entry with its {@code signal} attribute
	 *         modified to be the aggregated signal value instead of the value
	 *         in just the most recent entry for that (AP serial number, BSSID)
	 *         tuple. The returned map will only map an (AP, BSSID) to an entry
	 *         if an least one entry from that BSSID to that AP exists in
	 *         {@link DataModel#latestWifiScans}
	 */
	public static Map<String, Map<String, WifiScanEntry>> getAggregatedWifiScans(
		DataModel dataModel,
		long obsoletionPeriodMs,
		Aggregator<Double> agg
	) {
		return getAggregatedWifiScans(
			dataModel,
			obsoletionPeriodMs,
			new MeanAggregator(),
			System.currentTimeMillis()
		);
	}

	/**
	 * Compute aggregated wifiscans using a given reference time.
	 *
	 * @see #getAggregatedWifiScans(com.facebook.openwifirrm.modules.Modeler.DataModel,
	 *      long, Aggregator)
	 */
	public static Map<String, Map<String, WifiScanEntry>> getAggregatedWifiScans(
		Modeler.DataModel dataModel,
		long obsoletionPeriodMs,
		Aggregator<Double> agg,
		long refTimeMs
	) {
		// this method and the getAggregatedWifiScans() which does not take in
		// the ref time were separated to make testing easier
		if (obsoletionPeriodMs < 0) {
			throw new IllegalArgumentException(
				"obsoletionPeriodMs must be non-negative."
			);
		}
		Map<String, Map<String, WifiScanEntry>> aggregatedWifiScans =
			new HashMap<>();
		for (
			Map.Entry<String, List<List<WifiScanEntry>>> apToScansMapEntry : dataModel.latestWifiScans
				.entrySet()
		) {
			String serialNumber = apToScansMapEntry.getKey();
			List<List<WifiScanEntry>> scans = apToScansMapEntry.getValue();
			if (scans.isEmpty()) {
				continue;
			}
			/*
			 * Flatten the wifiscan entries and sort in reverse chronological order. Sorting
			 * is done just in case the entries in the original list are not chronological
			 * already - although they are inserted chronologically, perhaps latency,
			 * synchronization, etc. could cause the actual unixTimeMs to be out-of-order.
			 */
			List<WifiScanEntry> mostRecentToOldestEntries =
				scans.stream().flatMap(List::stream)
					.sorted((entry1, entry2) -> {
						return -Long
							.compare(entry1.unixTimeMs, entry2.unixTimeMs);
					})
					.collect(Collectors.toUnmodifiableList());
			// Split mostRecentToOldest into separate lists for each BSSID
			// These lists will be in reverse chronological order also
			Map<String, List<WifiScanEntry>> bssidToEntriesMap =
				new HashMap<>();
			for (WifiScanEntry entry : mostRecentToOldestEntries) {
				if (entry.bssid == null) {
					continue;
				}
				bssidToEntriesMap
					.computeIfAbsent(entry.bssid, bssid -> new ArrayList<>())
					.add(entry);
			}

			// calculate the aggregate for each bssid for this AP
			for (
				Map.Entry<String, List<WifiScanEntry>> bssidToWifiScanEntriesMapEntry : bssidToEntriesMap
					.entrySet()
			) {
				String bssid = bssidToWifiScanEntriesMapEntry.getKey();
				List<WifiScanEntry> entries =
					bssidToWifiScanEntriesMapEntry.getValue();
				WifiScanEntry mostRecentEntry = entries.get(0);
				agg.reset();
				for (WifiScanEntry entry : entries) {
					if (refTimeMs - entry.unixTimeMs > obsoletionPeriodMs) {
						// discard obsolete entries
						break;
					}
					if (
						mostRecentEntry == entry ||
							matchesForAggregation(mostRecentEntry, entry)
					) {
						aggregatedWifiScans
							.computeIfAbsent(serialNumber, k -> new HashMap<>())
							.computeIfAbsent(bssid, k -> new WifiScanEntry(entry));
						agg.addValue((double) entry.signal);
					}
				}
				if (agg.getCount() > 0) {
					aggregatedWifiScans.get(serialNumber).get(bssid).signal =
						(int) Math.round(agg.getAggregate());
				} else {
					aggregatedWifiScans
						.computeIfAbsent(serialNumber, k -> new HashMap<>())
						.put(bssid, mostRecentEntry);
				}
			}
		}
		return aggregatedWifiScans;
	}
}
