/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca.inputs;

import java.util.ArrayList;
import java.util.List;

/** Define root cause analysis configuration parameters */
public final class RCAParams {

	/** Look-back window in minutes */
	public final int detectionWindowMin;

	// KPI calculation parameters
	/** Minimum acceptable estimated throughput (Mbps) */
	public final double minEstimatedThroughputMbps;
	/** Percentile (units are %) of estimated throughputs to use as the KPI */
	public final double throughputAggregationPercentile;
	/** Maximum acceptable latency (ms) */
	public final int maxLatencyThresholdMs;
	/** Maximum acceptable jitter (ms) */
	public final int maxJitterThresholdMs;
	/**
	 * Maximum acceptable disconnection rate (disconnetions per hour). Note that
	 * this signifies a rate and the units happen to be per hour - this does not
	 * signify that every contiguous one-hour period be checked.
	 */
	public final int maxDisconnectionRatePerHour;

	// High Level metrics parameters
	/** Minimum acceptable tx rate (Mbps) */
	public final double minTxRateMbps;
	/** Maximum acceptable Packet Error Rate (PER) (units are %) */
	public final double maxPERPercent;
	/** Minimum acceptable idle airtime (units are %) */
	public final double minIdleAirtimePercent;
	/** Maximum acceptable number of clients for one radio */
	public final int maxNumClients;

	// Low Level metrics parameters
	/** Minimum acceptable RSSI (dBm) */
	public final int minRssidBm;
	/** Maximum acceptable noise (dBm) */
	public final int maxNoisedBm;
	/** Maximum acceptable intf airtime (units are %) */
	public final double maxIntfAirtimePercent;
	/** Maximum acceptable number of neighbors */
	public final int maxNumNeighbors;
	/** Minimum acceptable client bandwidth (MHz) for non-2G bands / */
	public final int minClientBandwidthMHz;
	/** Minimum acceptable Access Point (AP) bandwidth (MHz) for non-2G bands */
	public final int minApBandwidthMHz;
	/** Minimum acceptable self airtime ratio (units are %) */
	public final double minSelfAirtimeRatioPercent;
	/** Maximum acceptable tx dropped ratio (units are %) */
	public final double maxTxDroppedRatioPercent;

	/** Default constructor */
	public RCAParams() {
		this.detectionWindowMin = 360;

		this.minEstimatedThroughputMbps = 10;
		this.throughputAggregationPercentile = 10.0;
		this.maxLatencyThresholdMs = 50;
		this.maxJitterThresholdMs = 20;
		this.maxDisconnectionRatePerHour = 20;

		this.minTxRateMbps = 50;
		this.maxPERPercent = 10.0;
		this.minIdleAirtimePercent = 10.0;
		this.maxNumClients = 10;

		this.minRssidBm = -70;
		this.maxNoisedBm = -95;
		this.maxIntfAirtimePercent = 75.0;
		this.maxNumNeighbors = 10;
		this.minClientBandwidthMHz = 80;
		this.minApBandwidthMHz = 80;
		this.minSelfAirtimeRatioPercent = 25.0;
		this.maxTxDroppedRatioPercent = 0.1;
	}

	/**
	 * Confirm that the given value is positive. If it is not, add a String
	 * describing the problem to {@code errors}.
	 */
	private static void validatePositive(
		String varName,
		int value,
		List<String> errors
	) {
		if (value <= 0) {
			errors.add(varName + " must be positive.");
		}
	}

	/**
	 * Confirm that the given value is positive. If it is not, add a String
	 * describing the problem to {@code errors}.
	 */
	private static void validatePositive(
		String varName,
		double value,
		List<String> errors
	) {
		if (value <= 0) {
			errors.add(varName + " must be positive.");
		}
	}

	/**
	 * Confirm that the given value is a valid percentile (between 0 and 100
	 * inclusive). If it is not, add a String describing the problem to
	 * {@code errors}.
	 */
	private static void validatePercentile(
		String varName,
		double value,
		List<String> errors
	) {
		if (value < 0 || value > 100) {
			errors.add(varName + " must be between 0 and 100 inclusive.");
		}
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		validatePositive("Detection window", detectionWindowMin, errors);

		validatePositive(
			"Minimum estimated throughput",
			minEstimatedThroughputMbps,
			errors
		);
		validatePercentile(
			"Thoughput aggregation percentile",
			throughputAggregationPercentile,
			errors
		);
		validatePositive(
			"Maximum latency threshold",
			maxLatencyThresholdMs,
			errors
		);
		validatePositive(
			"Maximum jitter threshold",
			maxJitterThresholdMs,
			errors
		);
		validatePositive(
			"Maximum disconnection rate",
			maxDisconnectionRatePerHour,
			errors
		);

		validatePositive("Minimum tx rate", minTxRateMbps, errors);
		validatePercentile(
			"Maximum Packet Error Rate (PER)",
			maxPERPercent,
			errors
		);
		validatePercentile(
			"Minimum idle airtime",
			minIdleAirtimePercent,
			errors
		);
		validatePositive("Maximum number of clients", maxNumClients, errors);

		validatePercentile(
			"Maximum intf airtime",
			maxIntfAirtimePercent,
			errors
		);
		validatePositive(
			"Maximum number of neighbors",
			maxNumNeighbors,
			errors
		);
		validatePositive(
			"Minimum client bandwidth",
			minClientBandwidthMHz,
			errors
		);
		validatePositive(
			"Minimum Access Point (AP) bandwidth",
			minApBandwidthMHz,
			errors
		);
		validatePercentile(
			"Minimum self airtime ratio",
			minSelfAirtimeRatioPercent,
			errors
		);
		validatePercentile(
			"Maximum tx dropped ratio",
			maxTxDroppedRatioPercent,
			errors
		);

		return errors;
	}
}
