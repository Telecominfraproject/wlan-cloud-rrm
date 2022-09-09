/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.facebook.openwifirrm.ucentral.models.State;
import com.facebook.openwifirrm.ucentral.models.State.Interface;
import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID;
import com.facebook.openwifirrm.ucentral.models.State.Interface.SSID.Association;
import com.facebook.openwifirrm.ucentral.operationelement.HTOperationElement;
import com.facebook.openwifirrm.ucentral.operationelement.VHTOperationElement;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.reflect.TypeToken;

/**
 * Modeler utilities.
 */
public class ModelerUtils {
	public enum AggregationType { LIST, MEAN };

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

	/** This method converts the agggregated map to a Json String.
	 *
	 * @param aggregatedMap 	Aggregated map
	 * @param aggType 			Determines how to aggregate the statistics.
	 * @return String			A Json String representation of the aggregated map.
	 */
	public static String aggregatedStatesToJson(
		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedMap,
		AggregationType aggType
	) {
		GsonBuilder gsonBuilder = new GsonBuilder();

		Type AggregationType = new TypeToken<MeanAggregator>() {}.getType();
		JsonSerializer<MeanAggregator> serializer;
		/**
		 * Custom the json serializer by overriding the serialize method.
		*/
		switch (aggType) {
		case LIST:
			serializer =
				new JsonSerializer<MeanAggregator>() {
					/**
					 * If the aggregation type is to take list of the values,
					 * return a JsonArray of the value list.
					 */
					@Override
					public JsonElement serialize(
						MeanAggregator agg,
						java.lang.reflect.Type typeOfSrc,
						JsonSerializationContext context
					) {
						JsonArray aggJson = new JsonArray();
						for (Double value : agg.getList()) {
							aggJson.add(value.toString());
						}
						return aggJson;
					}
				};
			break;
		default:
			serializer =
				new JsonSerializer<MeanAggregator>() {
					/**
					 * If the aggregation type is to take mean of the values,
					 * return a JsonPrimitive type of single value string.
					 */
					@Override
					public JsonElement serialize(
						MeanAggregator agg,
						java.lang.reflect.Type typeOfSrc,
						JsonSerializationContext context
					) {
						return new JsonPrimitive(agg.getAggregate().toString());
					}
				};
			break;
		}
		gsonBuilder.registerTypeAdapter(AggregationType, serializer);
		Gson customGson = gsonBuilder.create();
		String aggregatedJson = customGson.toJson(aggregatedMap);
		return aggregatedJson;
	}

	/** This method reports the agggregated statistics as a Json String.
	 *
	 * @see #getAggregatedStatesJson(DataModel, long, List, List, AggregationType)
	 *
	 * @param dataModel 			the data model which includes the latest recorded States.
	 * @param obsoletionPeriodMs	the maximum amount of time (in milliseconds) it
	 *                           	is worth aggregating over.
	 * @param aggType				Determines how to aggregate the statistics.
	 * @return String				A Json String of aggregation statistics.
	 */
	public static String getAggregatedStatesJson(
		Modeler.DataModel dataModel,
		long obsoletionPeriodMs,
		AggregationType aggType
	) {
		return getAggregatedStatesJson(
			dataModel,
			obsoletionPeriodMs,
			new ArrayList<>(
				Arrays.asList("channel", "channel_width", "tx_power")
			),
			new ArrayList<>(Arrays.asList("rssi")),
			aggType
		);
	}

	/** This method serializes the aggregated statistics as a Json String.
	 * How to aggregate the stats (mean/list) is defined by the input parameter.
	 *
	 * @see #getAggregatedStates(DataModel, long, long, List, List)
	 *
	 * @param dataModel 			the data model which includes the latest recorded States.
	 * @param obsoletionPeriodMs	the maximum amount of time (in milliseconds) it
	 *                           	is worth aggregating over
	 * @param aggregatedKeys 		In which conditions the stats results need to be aggregated.
	 * @param aggregatedFields		Which fields to be aggregated. RSSI, MCS for example.
	 * @param aggType				Determines how to aggregate the statistics.
	 * @return String				A Json String of aggregation statistics.
	 */
	public static String getAggregatedStatesJson(
		Modeler.DataModel dataModel,
		long obsoletionPeriodMs,
		List<String> aggregatedKeys,
		List<String> aggregatedFields,
		AggregationType aggType
	) {
		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedMap =
			new HashMap<>();
		try {
			aggregatedMap = getAggregatedStates(
				dataModel,
				obsoletionPeriodMs,
				System.currentTimeMillis(),
				aggregatedKeys,
				aggregatedFields
			);
		} catch (
			IllegalArgumentException | IllegalAccessException
			| NoSuchFieldException | SecurityException e
		) {
			e.printStackTrace();
		}
		return aggregatedStatesToJson(aggregatedMap, aggType);
	}

	/**
	 * This method aggregates States by given aggregated fields(RSSI, MCS...) and aggregated 
	 * keys(channel, channel width, tx power...). Only non-obsolete clufcenfhtghtivgrfcvijnlrbnukccgStates are aggregated.
	 *
	 * @param dataModel 		 the data model which includes the latest recorded States.
	 * @param obsoletionPeriodMs the maximum amount of time (in milliseconds) it
	 *                           is worth aggregating over, starting from the
	 *                           most recent States and working backwards in time.
	 * 							 A State exactly {@code obsoletionPeriodMs} ms earlier
	 * 							 than the most recent State is considered non-obsolete
	 *                           (i.e., the "non-obsolete" window is inclusive).
	 *                           Must be non-negative.
	 * @param refTimeMs			the reference time were separated to make testing easier
	 * @param aggregatedKeys  	In which conditions the stats results need to be aggregated.
	 * 							For example, when channel, channel width and tx_power of two stats
	 * 							matches, the two stats need to be aggregated.
	 * @param aggregatedFields	Which fields to be aggregated. RSSI, MCS for example.
	 * @return Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>>
	 * 							Aggregated map that the keys are serial number, bssid, station
	 * 							({@link State.Interface.SSID.Association#station}), aggregatedKey,
	 * 							aggregatedField respectively. The returned value of type {@code
	 * 							MeanAggregator} contains all the aggregation results in specified
	 * 							fields.
	 * @throws SecurityException
	 * @throws NoSuchFieldException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public static Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> getAggregatedStates(
		Modeler.DataModel dataModel,
		long obsoletionPeriodMs,
		long refTimeMs,
		List<String> aggregatedKeys,
		List<String> aggregatedFields
	) throws IllegalArgumentException, IllegalAccessException,
		NoSuchFieldException, SecurityException {
		if (obsoletionPeriodMs < 0) {
			throw new IllegalArgumentException(
				"obsoletionPeriodMs must be non-negative."
			);
		}
		Map<String, Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>>> aggregatedStates =
			new HashMap<>();

		for (
			Map.Entry<String, List<State>> deviceToStateList : dataModel.latestStates
				.entrySet()
		) {
			String serialNumber = deviceToStateList.getKey();
			List<State> states = deviceToStateList.getValue();

			if (states.isEmpty()) {
				continue;
			}

			Map<String, Map<String, Map<JsonNode, Map<String, MeanAggregator>>>> bssidToStats =
				aggregatedStates
					.computeIfAbsent(serialNumber, k -> new HashMap<>());

			/**
			 * Sort in reverse chronological order. Sorting is done just in case the
			 * States in the original list are not chronological already - although
			 * they are inserted chronologically, perhaps latency, synchronization, etc.
			 */
			states.sort(
				(state1, state2) -> -Long.compare(state1.unit.localtime, state2.unit.localtime)
			);
			for (State state : states) {
				if (refTimeMs - state.unit.localtime > obsoletionPeriodMs) {
					// discard obsolete entries
					continue;
				}

				for (Interface stateInterface : state.interfaces) {
					if (stateInterface.ssids == null) {
						continue;
					}

					for (SSID ssid : stateInterface.ssids) {
						/**
						 * clientsToStats is the mapping from the station to aggregated statistis.
						 */
						Map<String, Map<JsonNode, Map<String, MeanAggregator>>> clientToStats =
							bssidToStats.computeIfAbsent(
								ssid.bssid,
								k -> new HashMap<>()
							);

						/**
						 * aggregatedKey is used to Determine if two associations should be aggregated.
						 * It is a JsonNode type with a mapping between channel/channel width/txpower
						 * and the related values. The following condition is true then two stats results
						 * need to be aggregated: channel, channel width, and txpower under "radio" field
						 * match.
						*/

						Map<String, Integer> map = new HashMap<>();
						for (String aggKey : aggregatedKeys) {
							map.put(aggKey, ssid.radio.get(aggKey).getAsInt());
						}

						JsonNode aggregatedNode =
							new ObjectMapper().valueToTree(map);

						for (Association association : ssid.associations) {
							for (String aggField : aggregatedFields) {
								String station = association.station;
								Double value = Association.class
									.getDeclaredField(aggField)
									.getDouble(association);

								clientToStats.computeIfAbsent(
									station,
									k -> new HashMap<>()
								)
									.computeIfAbsent(
										aggregatedNode,
										k -> new HashMap<>()
									)
									.computeIfAbsent(
										aggField,
										k -> new MeanAggregator()
									)
									.addValue(value);

							}

						}
						bssidToStats.put(ssid.bssid, clientToStats);
					}
				}
				aggregatedStates.put(serialNumber, bssidToStats);
			}
		}
		return aggregatedStates;
	}
}
