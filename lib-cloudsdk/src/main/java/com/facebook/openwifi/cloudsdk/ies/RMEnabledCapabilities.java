/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.ies;

import java.util.Objects;

import com.facebook.openwifi.cloudsdk.IEUtils;
import com.google.gson.JsonObject;

/**
 * This information element (IE) appears in wifiscan entries. It's called "RM
 * Enabled Capabilities" in 802.11 specs (section 9.4.2.45). Refer to the
 * specification for more details. Language in javadocs is taken from the
 * specification.
 */
public class RMEnabledCapabilities {
	/** Defined in 802.11 table 9-92 */
	public static final int TYPE = 70;

	// Bit fields
	// @formatter:off
	public final boolean linkMeasurementCapabilityEnabled;
	public final boolean neighborReportCapabilityEnabled;
	public final boolean parallelMeasurementsCapabilityEnabled;
	public final boolean repeatedMeasurementsCapabilityEnabled;
	public final boolean beaconPassiveMeasurementCapabilityEnabled;
	public final boolean beaconActiveMeasurementCapabilityEnabled;
	public final boolean beaconTableMeasurementCapabilityEnabled;
	public final boolean beaconMeasurementReportingConditionsCapabilityEnabled;
	public final boolean frameMeasurementCapabilityEnabled;
	public final boolean channelLoadMeasurementCapabilityEnabled;
	public final boolean noiseHistogramMeasurementCapabilityEnabled;
	public final boolean statisticsMeasurementCapabilityEnabled;
	public final boolean lciMeasurementCapabilityEnabled;
	public final boolean lciAzimuthCapabilityEnabled;
	public final boolean transmitStreamCategoryMeasurementCapabilityEnabled;
	public final boolean triggeredTransmitStreamCategoryMeasurementCapabilityEnabled;
	public final boolean apChannelReportCapabilityEnabled;
	public final boolean rmMibCapabilityEnabled;
	public final int operatingChannelMaxMeasurementDuration;
	public final int nonoperatingChannelMaxMeasurementDuration;
	public final int measurementPilotCapability;
	public final boolean measurementPilotTransmissionInformationCapabilityEnabled;
	public final boolean neighborReportTsfOffsetCapabilityEnabled;
	public final boolean rcpiMeasurementCapabilityEnabled;
	public final boolean rsniMeasurementCapabilityEnabled;
	public final boolean bssAverageAccessDelayCapabilityEnabled;
	public final boolean bssAvailableAdmissionCapacityCapabilityEnabled;
	public final boolean antennaCapabilityEnabled;
	public final boolean ftmRangeReportCapabilityEnabled;
	public final boolean civicLocationMeasurementCapabilityEnabled;
	// @formatter:on

	/** Constructor */
	public RMEnabledCapabilities(
		boolean linkMeasurementCapabilityEnabled,
		boolean neighborReportCapabilityEnabled,
		boolean parallelMeasurementsCapabilityEnabled,
		boolean repeatedMeasurementsCapabilityEnabled,
		boolean beaconPassiveMeasurementCapabilityEnabled,
		boolean beaconActiveMeasurementCapabilityEnabled,
		boolean beaconTableMeasurementCapabilityEnabled,
		boolean beaconMeasurementReportingConditionsCapabilityEnabled,
		boolean frameMeasurementCapabilityEnabled,
		boolean channelLoadMeasurementCapabilityEnabled,
		boolean noiseHistogramMeasurementCapabilityEnabled,
		boolean statisticsMeasurementCapabilityEnabled,
		boolean lciMeasurementCapabilityEnabled,
		boolean lciAzimuthCapabilityEnabled,
		boolean transmitStreamCategoryMeasurementCapabilityEnabled,
		boolean triggeredTransmitStreamCategoryMeasurementCapabilityEnabled,
		boolean apChannelReportCapabilityEnabled,
		boolean rmMibCapabilityEnabled,
		int operatingChannelMaxMeasurementDuration,
		int nonoperatingChannelMaxMeasurementDuration,
		int measurementPilotCapability,
		boolean measurementPilotTransmissionInformationCapabilityEnabled,
		boolean neighborReportTsfOffsetCapabilityEnabled,
		boolean rcpiMeasurementCapabilityEnabled,
		boolean rsniMeasurementCapabilityEnabled,
		boolean bssAverageAccessDelayCapabilityEnabled,
		boolean bssAvailableAdmissionCapacityCapabilityEnabled,
		boolean antennaCapabilityEnabled,
		boolean ftmRangeReportCapabilityEnabled,
		boolean civicLocationMeasurementCapabilityEnabled
	) {
		// @formatter:off
		this.linkMeasurementCapabilityEnabled = linkMeasurementCapabilityEnabled;
		this.neighborReportCapabilityEnabled = neighborReportCapabilityEnabled;
		this.parallelMeasurementsCapabilityEnabled = parallelMeasurementsCapabilityEnabled;
		this.repeatedMeasurementsCapabilityEnabled = repeatedMeasurementsCapabilityEnabled;
		this.beaconPassiveMeasurementCapabilityEnabled = beaconPassiveMeasurementCapabilityEnabled;
		this.beaconActiveMeasurementCapabilityEnabled = beaconActiveMeasurementCapabilityEnabled;
		this.beaconTableMeasurementCapabilityEnabled = beaconTableMeasurementCapabilityEnabled;
		this.beaconMeasurementReportingConditionsCapabilityEnabled = beaconMeasurementReportingConditionsCapabilityEnabled;
		this.frameMeasurementCapabilityEnabled = frameMeasurementCapabilityEnabled;
		this.channelLoadMeasurementCapabilityEnabled = channelLoadMeasurementCapabilityEnabled;
		this.noiseHistogramMeasurementCapabilityEnabled = noiseHistogramMeasurementCapabilityEnabled;
		this.statisticsMeasurementCapabilityEnabled = statisticsMeasurementCapabilityEnabled;
		this.lciMeasurementCapabilityEnabled = lciMeasurementCapabilityEnabled;
		this.lciAzimuthCapabilityEnabled = lciAzimuthCapabilityEnabled;
		this.transmitStreamCategoryMeasurementCapabilityEnabled = transmitStreamCategoryMeasurementCapabilityEnabled;
		this.triggeredTransmitStreamCategoryMeasurementCapabilityEnabled = triggeredTransmitStreamCategoryMeasurementCapabilityEnabled;
		this.apChannelReportCapabilityEnabled = apChannelReportCapabilityEnabled;
		this.rmMibCapabilityEnabled = rmMibCapabilityEnabled;
		this.operatingChannelMaxMeasurementDuration = operatingChannelMaxMeasurementDuration;
		this.nonoperatingChannelMaxMeasurementDuration = nonoperatingChannelMaxMeasurementDuration;
		this.measurementPilotCapability = measurementPilotCapability;
		this.measurementPilotTransmissionInformationCapabilityEnabled = measurementPilotTransmissionInformationCapabilityEnabled;
		this.neighborReportTsfOffsetCapabilityEnabled = neighborReportTsfOffsetCapabilityEnabled;
		this.rcpiMeasurementCapabilityEnabled = rcpiMeasurementCapabilityEnabled;
		this.rsniMeasurementCapabilityEnabled = rsniMeasurementCapabilityEnabled;
		this.bssAverageAccessDelayCapabilityEnabled = bssAverageAccessDelayCapabilityEnabled;
		this.bssAvailableAdmissionCapacityCapabilityEnabled = bssAvailableAdmissionCapacityCapabilityEnabled;
		this.antennaCapabilityEnabled = antennaCapabilityEnabled;
		this.ftmRangeReportCapabilityEnabled = ftmRangeReportCapabilityEnabled;
		this.civicLocationMeasurementCapabilityEnabled = civicLocationMeasurementCapabilityEnabled;
		// @formatter:on
	}

	/** Parse RMEnabledCapabilities IE from appropriate Json object. */
	public static RMEnabledCapabilities parse(JsonObject contents) {
		JsonObject o = contents.get("RM Capabilities").getAsJsonObject();
		// @formatter:off
		return new RMEnabledCapabilities(
			/* bits 0-17 */
			IEUtils.parseBooleanNumberField(o, "Link Measurement"),
			IEUtils.parseBooleanNumberField(o, "Neighbor Report"),
			IEUtils.parseBooleanNumberField(o, "Parallel Measurements"),
			IEUtils.parseBooleanNumberField(o, "Repeated Measurements"),
			IEUtils.parseBooleanNumberField(o, "Beacon Passive Measurement"),
			IEUtils.parseBooleanNumberField(o, "Beacon Active Measurement"),
			IEUtils.parseBooleanNumberField(o, "Beacon Table Measurement"),
			IEUtils.parseBooleanNumberField(o, "Beacon Measurement Reporting Conditions"),
			IEUtils.parseBooleanNumberField(o, "Frame Measurement"),
			IEUtils.parseBooleanNumberField(o, "Channel Load Measurement"),
			IEUtils.parseBooleanNumberField(o, "Noise Histogram Measurement"),
			IEUtils.parseBooleanNumberField(o, "Statistics Measurement"),
			IEUtils.parseBooleanNumberField(o, "LCI Measurement"),
			IEUtils.parseBooleanNumberField(o, "LCI Azimuth capability"),
			IEUtils.parseBooleanNumberField(o, "Transmit Stream/Category Measurement"),
			IEUtils.parseBooleanNumberField(o, "Triggered Transmit Stream/Category Measurement"),
			IEUtils.parseBooleanNumberField(o, "AP Channel Report capability"),
			IEUtils.parseBooleanNumberField(o, "RM MIB capability"),
			/* bits 18-20 */
			IEUtils.parseIntField(o, "Operating Channel Max Measurement Duration"),
			/* bits 21-23 */
			IEUtils.parseIntField(o, "Nonoperating Channel Max Measurement Duration"),
			/* bits 24-26 */
			IEUtils.parseIntField(o, "Measurement Pilotcapability"),
			/* bits 27-35 */
			false /* TODO "Measurement Pilot Transmission Information Capability" */,
			IEUtils.parseBooleanNumberField(o, "Neighbor Report TSF Offset"),
			IEUtils.parseBooleanNumberField(o, "RCPI Measurement capability"),
			IEUtils.parseBooleanNumberField(o, "RSNI Measurement capability"),
			IEUtils.parseBooleanNumberField(o, "BSS Average Access Delay capability"),
			IEUtils.parseBooleanNumberField(o, "BSS Available Admission Capacity capability"),
			IEUtils.parseBooleanNumberField(o, "Antenna capability"),
			false /* TODO "FTM Range Report Capability" */,
			false /* TODO "Civic Location Measurement Capability" */
			/* bits 36-39 reserved */
		);
		// @formatter:on
	}

	@Override
	public int hashCode() {
		return Objects.hash(
			antennaCapabilityEnabled,
			apChannelReportCapabilityEnabled,
			beaconActiveMeasurementCapabilityEnabled,
			beaconMeasurementReportingConditionsCapabilityEnabled,
			beaconPassiveMeasurementCapabilityEnabled,
			beaconTableMeasurementCapabilityEnabled,
			bssAvailableAdmissionCapacityCapabilityEnabled,
			bssAverageAccessDelayCapabilityEnabled,
			channelLoadMeasurementCapabilityEnabled,
			civicLocationMeasurementCapabilityEnabled,
			frameMeasurementCapabilityEnabled,
			ftmRangeReportCapabilityEnabled,
			lciAzimuthCapabilityEnabled,
			lciMeasurementCapabilityEnabled,
			linkMeasurementCapabilityEnabled,
			measurementPilotCapability,
			measurementPilotTransmissionInformationCapabilityEnabled,
			neighborReportCapabilityEnabled,
			neighborReportTsfOffsetCapabilityEnabled,
			noiseHistogramMeasurementCapabilityEnabled,
			nonoperatingChannelMaxMeasurementDuration,
			operatingChannelMaxMeasurementDuration,
			parallelMeasurementsCapabilityEnabled,
			rcpiMeasurementCapabilityEnabled,
			repeatedMeasurementsCapabilityEnabled,
			rmMibCapabilityEnabled,
			rsniMeasurementCapabilityEnabled,
			statisticsMeasurementCapabilityEnabled,
			transmitStreamCategoryMeasurementCapabilityEnabled,
			triggeredTransmitStreamCategoryMeasurementCapabilityEnabled
		);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		RMEnabledCapabilities other = (RMEnabledCapabilities) obj;
		return antennaCapabilityEnabled == other.antennaCapabilityEnabled &&
			apChannelReportCapabilityEnabled ==
				other.apChannelReportCapabilityEnabled &&
			beaconActiveMeasurementCapabilityEnabled ==
				other.beaconActiveMeasurementCapabilityEnabled &&
			beaconMeasurementReportingConditionsCapabilityEnabled ==
				other.beaconMeasurementReportingConditionsCapabilityEnabled &&
			beaconPassiveMeasurementCapabilityEnabled ==
				other.beaconPassiveMeasurementCapabilityEnabled &&
			beaconTableMeasurementCapabilityEnabled ==
				other.beaconTableMeasurementCapabilityEnabled &&
			bssAvailableAdmissionCapacityCapabilityEnabled ==
				other.bssAvailableAdmissionCapacityCapabilityEnabled &&
			bssAverageAccessDelayCapabilityEnabled ==
				other.bssAverageAccessDelayCapabilityEnabled &&
			channelLoadMeasurementCapabilityEnabled ==
				other.channelLoadMeasurementCapabilityEnabled &&
			civicLocationMeasurementCapabilityEnabled ==
				other.civicLocationMeasurementCapabilityEnabled &&
			frameMeasurementCapabilityEnabled ==
				other.frameMeasurementCapabilityEnabled &&
			ftmRangeReportCapabilityEnabled ==
				other.ftmRangeReportCapabilityEnabled &&
			lciAzimuthCapabilityEnabled == other.lciAzimuthCapabilityEnabled &&
			lciMeasurementCapabilityEnabled ==
				other.lciMeasurementCapabilityEnabled &&
			linkMeasurementCapabilityEnabled ==
				other.linkMeasurementCapabilityEnabled &&
			measurementPilotCapability == other.measurementPilotCapability &&
			measurementPilotTransmissionInformationCapabilityEnabled ==
				other.measurementPilotTransmissionInformationCapabilityEnabled &&
			neighborReportCapabilityEnabled ==
				other.neighborReportCapabilityEnabled &&
			neighborReportTsfOffsetCapabilityEnabled ==
				other.neighborReportTsfOffsetCapabilityEnabled &&
			noiseHistogramMeasurementCapabilityEnabled ==
				other.noiseHistogramMeasurementCapabilityEnabled &&
			nonoperatingChannelMaxMeasurementDuration ==
				other.nonoperatingChannelMaxMeasurementDuration &&
			operatingChannelMaxMeasurementDuration ==
				other.operatingChannelMaxMeasurementDuration &&
			parallelMeasurementsCapabilityEnabled ==
				other.parallelMeasurementsCapabilityEnabled &&
			rcpiMeasurementCapabilityEnabled ==
				other.rcpiMeasurementCapabilityEnabled &&
			repeatedMeasurementsCapabilityEnabled ==
				other.repeatedMeasurementsCapabilityEnabled &&
			rmMibCapabilityEnabled == other.rmMibCapabilityEnabled &&
			rsniMeasurementCapabilityEnabled ==
				other.rsniMeasurementCapabilityEnabled &&
			statisticsMeasurementCapabilityEnabled ==
				other.statisticsMeasurementCapabilityEnabled &&
			transmitStreamCategoryMeasurementCapabilityEnabled ==
				other.transmitStreamCategoryMeasurementCapabilityEnabled &&
			triggeredTransmitStreamCategoryMeasurementCapabilityEnabled ==
				other.triggeredTransmitStreamCategoryMeasurementCapabilityEnabled;
	}
}
