/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.modules;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.rrm.DeviceConfig;
import com.facebook.openwifi.rrm.DeviceDataManager;
import com.facebook.openwifi.rrm.RRMAlgorithm;
import com.facebook.openwifi.rrm.RRMSchedule;
import com.facebook.openwifi.rrm.RRMConfig.ModuleConfig.RRMSchedulerParams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * RRM scheduler, implemented using Quartz.
 */
public class RRMScheduler {
	private static final Logger logger =
		LoggerFactory.getLogger(RRMScheduler.class);

	/** The gson instance. */
	private static final Gson gson =
		new GsonBuilder().setPrettyPrinting().create();

	/** SchedulerContext key holding the RRMScheduler instance. */
	private static final String SCHEDULER_CONTEXT_RRMSCHEDULER = "RRMScheduler";

	/** The single Quartz job instance. */
	private final JobDetail job = JobBuilder.newJob(RRMJob.class)
		.withIdentity("RRM")
		.storeDurably()
		.build();

	/** The module parameters. */
	private final RRMSchedulerParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The ConfigManager module instance. */
	private ConfigManager configManager;

	/** The Modeler module instance. */
	private Modeler modeler;

	/** The scheduler instance. */
	private Scheduler scheduler;

	/**
	 * The job keys with active triggers scheduled. Job keys take the format of
	 * {@code <zone>:<index>}
	 *
	 * @see #parseIntoQuartzCron(String)
	 * */
	private Set<String> scheduledJobKeys;

	/** RRM job. */
	public static class RRMJob implements Job {
		@Override
		public void execute(JobExecutionContext context)
			throws JobExecutionException {
			String jobKey = context.getTrigger().getKey().getName();
			String zone = jobKey.split(":")[0];
			logger.debug("Executing job for zone: {}", zone);
			try {
				SchedulerContext schedulerContext =
					context.getScheduler().getContext();
				RRMScheduler instance =
					(RRMScheduler) schedulerContext
						.get(SCHEDULER_CONTEXT_RRMSCHEDULER);
				instance.performRRM(zone);
			} catch (SchedulerException e) {
				throw new JobExecutionException(e);
			}
		}
	}

	/**
	 * Parses Linux cron spec (with seconds) into Quartz compatible cron spec.
	 * Quartz does not allow setting both day of week and day of month. If valid
	 * Quartz cron already we don't need to do anything. Otherwise we can't use
	 * Quartz to even parse out the components as it will just throw a parse
	 * error.
	 *
	 * @param linuxCron Linux cron with seconds
	 *        (seconds minutes hours day_of_month month day_of_week [year])
	 *
	 * @throws IllegalArgumentException when a linux cron cannot be parsed into a
	 *         valid Quartz spec
	 * @return String[] an array of length 1 or 2 of Quartz supported cron that's
	 *         equivalent to the original linux cron
	 */
	public static String[] parseIntoQuartzCron(String linuxCron) {
		if (CronExpression.isValidExpression(linuxCron)) {
			return new String[] { linuxCron };
		}

		String[] split = linuxCron.split(" ");
		if (split.length < 6 || split.length > 7) {
			// Quartz cron MUST have at least 6 and no more than 7 fields
			return null;
		}

		final int DAY_OF_MONTH_INDEX = 3;
		final int DAY_OF_WEEK_INDEX = 5;

		String dayOfMonth = split[DAY_OF_MONTH_INDEX];
		String dayOfWeek = split[DAY_OF_WEEK_INDEX];

		// Quartz uses 1-7, while standard cron expects 0-6 so replace all
		// 0s with 7s (Sunday)
		split[DAY_OF_WEEK_INDEX] =
			split[DAY_OF_WEEK_INDEX].replaceAll("0", "7");

		if (dayOfMonth.equals("*") && dayOfWeek.equals("*")) {
			// if both are * then it doesn't matter which one becomes ? since it's
			// semantically the same
			split[DAY_OF_MONTH_INDEX] = "?";
		} else if (dayOfMonth.equals("*")) {
			// if first case failed and only day of month is *, set to ?
			split[DAY_OF_MONTH_INDEX] = "?";
		} else if (dayOfWeek.equals("*")) {
			// if first case failed and only day of week is *, set to ?
			split[DAY_OF_WEEK_INDEX] = "?";
		} else {
			// Quartz does not support both values being set but the standard says that
			// if both are specified then it becomes OR of the two fields. Which means
			// that we can split it into two separate crons and have it work the same way
			split[DAY_OF_MONTH_INDEX] = "?";
			String dayOfWeekCron = String.join(" ", split);

			split[DAY_OF_MONTH_INDEX] = dayOfMonth;
			split[DAY_OF_WEEK_INDEX] = "?";
			String dayOfMonthCron = String.join(" ", split);

			if (
				!CronExpression.isValidExpression(dayOfWeekCron) ||
					!CronExpression.isValidExpression(dayOfMonthCron)
			) {
				logger.error(
					"Unable to parse cron {} into valid crons",
					linuxCron
				);
				return null;
			}

			return new String[] { dayOfWeekCron, dayOfMonthCron };
		}

		String quartzCron = String.join(" ", split);
		if (!CronExpression.isValidExpression(quartzCron)) {
			return null;
		}

		return new String[] { quartzCron };
	}

	/** Constructor. */
	public RRMScheduler(
		RRMSchedulerParams params,
		DeviceDataManager deviceDataManager
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
	}

	/** Build the Properties to pass to StdSchedulerFactory. */
	private Properties getSchedulerProperties() {
		Properties props = new Properties();
		props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
		props.setProperty(
			"org.quartz.threadPool.threadCount",
			Integer.toString(params.threadCount)
		);
		return props;
	}

	/** Start the scheduler, returning true if newly started. */
	public boolean start(ConfigManager configManager, Modeler modeler) {
		this.configManager = configManager;
		this.modeler = modeler;

		try {
			if (scheduler != null && scheduler.isStarted()) {
				return false;
			}

			// Create scheduler
			StdSchedulerFactory factory =
				new StdSchedulerFactory(getSchedulerProperties());
			this.scheduler = factory.getScheduler();
			scheduler.getContext().put(SCHEDULER_CONTEXT_RRMSCHEDULER, this);

			// Schedule job and triggers
			scheduler.addJob(job, false);
			syncTriggers();
			logger.info("Scheduled {} RRM trigger(s)", scheduledJobKeys.size());

			// Start scheduler
			scheduler.start();
			return true;
		} catch (SchedulerException e) {
			logger.error("Failed to start scheduler", e);
			return false;
		}
	}

	/** Shut down the scheduler. */
	public void shutdown() {
		try {
			if (scheduler != null && !scheduler.isShutdown()) {
				scheduler.shutdown();
			}
		} catch (SchedulerException e) {
			logger.error("Failed to shutdown scheduler", e);
		}
	}

	/**
	 * Synchronize triggers to the current topology, adding/updating/deleting
	 * them as necessary. This updates {@link #scheduledJobKeys}.
	 */
	public void syncTriggers() {
		Set<String> scheduled = ConcurrentHashMap.newKeySet();
		Set<String> prevScheduled = new HashSet<>();
		if (scheduledJobKeys != null) {
			prevScheduled.addAll(scheduledJobKeys);
		}

		// Add new triggers
		for (String zone : deviceDataManager.getZones()) {
			DeviceConfig config = deviceDataManager.getZoneConfig(zone);
			RRMSchedule schedule = config.schedule;
			if (
				schedule == null || schedule.crons == null ||
					schedule.crons.isEmpty()
			) {
				continue; // RRM not scheduled
			}

			for (int i = 0; i < schedule.crons.size(); i++) {
				String cron = schedule.crons.get(i);
				// if even one schedule has invalid cron, the whole thing is probably wrong
				if (cron == null || cron.isEmpty()) {
					logger.error("There was an invalid cron in the schedule");
					break;
				}

				try {
					CronExpression.validateExpression(cron);
				} catch (ParseException e) {
					logger.error(
						String.format(
							"Invalid cron expression (%s) for zone %s",
							cron,
							zone
						),
						e
					);
					continue;
				}

				// Create trigger
				String jobKey = String.format("%s:%d", zone, i);
				Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(jobKey)
					.forJob(job)
					.withSchedule(
						CronScheduleBuilder.cronSchedule(cron)
					)
					.build();

				try {
					if (!prevScheduled.contains(jobKey)) {
						scheduler.scheduleJob(trigger);
					} else {
						scheduler.rescheduleJob(trigger.getKey(), trigger);
					}
				} catch (SchedulerException e) {
					logger.error(
						"Failed to schedule RRM trigger for job key: " + jobKey,
						e
					);
					continue;
				}

				scheduled.add(jobKey);
				logger.debug(
					"Scheduled/updated RRM for job key '{}' at: < {} >",
					jobKey,
					cron
				);
			}

		}

		// Remove old triggers
		prevScheduled.removeAll(scheduled);
		for (String jobKey : prevScheduled) {
			try {
				scheduler.unscheduleJob(TriggerKey.triggerKey(jobKey));
			} catch (SchedulerException e) {
				logger.error(
					"Failed to remove RRM trigger for jobKey: " + jobKey,
					e
				);
				continue;
			}
			logger.debug("Removed RRM trigger for jobKey '{}'", jobKey);
		}

		this.scheduledJobKeys = scheduled;
	}

	/** Run RRM algorithms for the given zone. */
	protected void performRRM(String zone) {
		logger.info("Starting scheduled RRM for zone '{}'", zone);

		// Get algorithms from zone config
		DeviceConfig config = deviceDataManager.getZoneConfig(zone);
		RRMSchedule schedule = config.schedule;
		if (schedule == null) {
			logger.error("RRM schedule missing for zone '{}', aborting!", zone);
			return;
		}

		if (
			schedule.algorithms == null ||
				schedule.algorithms.isEmpty()
		) {
			logger
				.debug("Using default RRM algorithms for zone '{}'", zone);
			schedule.algorithms = Arrays.asList(
				new RRMAlgorithm(
					RRMAlgorithm.AlgorithmType.OptimizeChannel.name()
				),
				new RRMAlgorithm(
					RRMAlgorithm.AlgorithmType.OptimizeTxPower.name()
				)
			);
		}

		// Execute algorithms
		for (RRMAlgorithm algo : schedule.algorithms) {
			RRMAlgorithm.AlgorithmResult result = algo.run(
				deviceDataManager,
				configManager,
				modeler,
				zone,
				params.dryRun,
				true, /* allowDefaultMode */
				false /* updateImmediately */
			);
			logger.info(
				"'{}' result for zone '{}': {}",
				algo.getName(),
				zone,
				gson.toJson(result)
			);
		}
		configManager.queueZoneAndWakeUp(zone);
	}
}
