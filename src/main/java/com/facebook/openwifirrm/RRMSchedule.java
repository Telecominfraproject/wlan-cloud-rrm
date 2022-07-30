/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.util.List;
import java.util.Map;

/**
 * RRM schedule config.
 */
public class RRMSchedule {
	/**
	 * The interval at which RRM should be run.
	 *
	 * This field expects a cron-like format as defined by the Quartz Job
	 * Scheduler (CronTrigger):
	 * https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html
	 */
	public String cron;

	/**
	 * The list of RRM algorithms to run.
	 *
	 * If empty, all algorithms will be run using default settings.
	 */
	public List<RRMAlgorithm> algorithms;

	/** RRM algorithm name and arguments. */
	public static class RRMAlgorithm {
		/** The algorithm name. */
		public String name;

		/** The algorithm arguments. */
		public Map<String, String> args;

		/** Constructor. */
		public RRMAlgorithm(String name, Map<String, String> args) {
			this.name = name;
			this.args = args;
		}
	}
}
