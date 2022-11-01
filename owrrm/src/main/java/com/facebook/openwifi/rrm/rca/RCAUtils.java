/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.rca;

import com.facebook.openwifi.cloudsdk.UCentralClient;
import com.facebook.openwifi.cloudsdk.UCentralUtils;
import com.facebook.openwifi.cloudsdk.models.gw.CommandInfo;

/**
 * Utilities for root cause analysis.
 */
public class RCAUtils {
	/** Ping result, only containing a data summary (not individual pings). */
	public static class PingResult {
		// NOTE: fields are taken directly from ping output
		/** Minimum ping RTT (ms) */
		public double min;
		/** Average ping RTT (ms) */
		public double avg;
		/** Maximum ping RTT (ms) */
		public double max;
		/** Standard deviation of ping RTT measurements (ms) */
		public double mdev;
		// TODO other stats? (ex. tx/rx packets, % packet loss)

		@Override
		public String toString() {
			return String.format("%.3f/%.3f/%.3f/%.3f ms", min, avg, max, mdev);
		}
	}

	/**
	 * Parse raw ping output, returning null upon error.
	 *
	 * This only supports the busybox ping format.
	 */
	private static PingResult parsePingOutput(String output) {
		// Only parse summary line (should be last line in output).
		// Code below is optimized for minimal string operations.
		//
		// Examples of supported formats:
		//   round-trip min/avg/max = 4.126/42.470/84.081 ms
		//   rtt min/avg/max/mdev = 16.853/20.114/23.375/3.261 ms
		final String SUMMARY_TEXT_3 = "min/avg/max";
		int idx = output.lastIndexOf(SUMMARY_TEXT_3);
		if (idx != -1) {
			idx += SUMMARY_TEXT_3.length();
		} else {
			final String SUMMARY_TEXT_4 = "min/avg/max/mdev";
			idx = output.lastIndexOf(SUMMARY_TEXT_4);
			if (idx != -1) {
				idx += SUMMARY_TEXT_4.length();
			} else {
				return null;
			}
		}
		PingResult result = null;
		for (; idx < output.length(); idx++) {
			if (Character.isDigit(output.charAt(idx))) {
				break;
			}
		}
		if (idx < output.length()) {
			int endIdx = output.indexOf(' ', idx);
			if (endIdx != -1) {
				String s = output.substring(idx, endIdx);
				String[] tokens = s.split("/");
				if (tokens.length == 3) {
					result = new PingResult();
					result.min = Double.parseDouble(tokens[0]);
					result.avg = Double.parseDouble(tokens[1]);
					result.max = Double.parseDouble(tokens[2]);
				} else if (tokens.length == 4) {
					result = new PingResult();
					result.min = Double.parseDouble(tokens[0]);
					result.avg = Double.parseDouble(tokens[1]);
					result.max = Double.parseDouble(tokens[2]);
					result.mdev = Double.parseDouble(tokens[3]);
				}
			}
		}
		return result;
	}

	/**
	 * Instruct a device (AP) to ping a given destination (IP/hostname),
	 * returning the raw ping output or null upon error.
	 *
	 * @param client the UCentralClient instance
	 * @param serialNumber the device (AP) serial number
	 * @param host the ping destination
	 * @param pingCount the number of pings to send
	 * @return the ping output, or null upon error
	 */
	public static PingResult pingFromDevice(
		UCentralClient client,
		String serialNumber,
		String host,
		int pingCount
	) {
		if (pingCount < 1) {
			throw new IllegalArgumentException("Invalid pingCount < 1");
		}
		String script = String.format("ping -c %d %s", pingCount, host);
		int timeoutSec = pingCount /* time buffer as follows: */ * 2 + 10;
		CommandInfo info = client.runScript(serialNumber, script, timeoutSec);
		String output = UCentralUtils.getScriptOutput(info);
		return output != null ? parsePingOutput(output) : null;
	}
}
