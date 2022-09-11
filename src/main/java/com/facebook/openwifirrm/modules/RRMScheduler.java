/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.text.ParseException;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronExpression;
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

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMAlgorithm;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.RRMSchedulerParams;
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

	/** The zones with active triggers scheduled. */
	private Set<String> scheduledZones;

	/**
	 * TODO [ZoneBasedRrmScheduling] remove this in favor of zones.
	 * The devices with active triggers scheduled.
	 */
	private Set<String> scheduledDevices;

	/** RRM job. */
	public static class RRMJob implements Job {
		@Override
		public void execute(JobExecutionContext context)
			throws JobExecutionException {
			String zone = context.getTrigger().getKey().getName();
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
			// TODO [ZoneBasedRrmScheduling] move this to syncTriggersForZones once
			// that API is available and change it to be named just `syncTriggers`
			// syncTriggers();
			// logger.info("Scheduled {} RRM trigger(s)", scheduledZones.size());
			syncTriggersForDevices();
			logger.info("Scheduled {} RRM trigger(s)", scheduledDevices.size());

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
	 * TODO [ZoneBasedRrmScheduling] remove this and used venue based config.
	 * Synchronize triggers to the current topology, adding/updating/deleting
	 * them as necessary. This updates {@link #scheduledDevices}.
	 */
	public void syncTriggersForDevices() {
		Set<String> scheduled = ConcurrentHashMap.newKeySet();
		Set<String> prevScheduled = new HashSet<>();
		if (scheduledDevices != null) {
			prevScheduled.addAll(scheduledDevices);
		}

		Map<String, Set<String>> topology = deviceDataManager.getTopologyCopy();

		// Add new triggers
		for (Map.Entry<String, Set<String>> entry : topology.entrySet()) {
			for (String serialNumber : entry.getValue()) {
				DeviceConfig config =
					deviceDataManager.getDeviceConfig(serialNumber);
				if (
					config.schedule == null ||
						config.schedule.cron == null ||
						config.schedule.cron.isEmpty()
				) {
					continue; // RRM not scheduled
				}

				try {
					CronExpression.validateExpression(config.schedule.cron);
				} catch (ParseException e) {
					logger.error(
						String.format(
							"Invalid cron expression (%s) for device %s",
							config.schedule.cron,
							serialNumber
						),
						e
					);
					continue;
				}

				// Create trigger
				Trigger trigger = TriggerBuilder.newTrigger()
					.withIdentity(serialNumber)
					.forJob(job)
					.withSchedule(
						CronScheduleBuilder.cronSchedule(config.schedule.cron)
					)
					.build();
				try {
					if (!prevScheduled.contains(serialNumber)) {
						scheduler.scheduleJob(trigger);
					} else {
						scheduler.rescheduleJob(trigger.getKey(), trigger);
					}
				} catch (SchedulerException e) {
					logger.error(
						"Failed to schedule RRM trigger for device: " +
							serialNumber,
						e
					);
					continue;
				}
				scheduled.add(serialNumber);
				logger.debug(
					"Scheduled/updated RRM for device '{}' at: < {} >",
					serialNumber,
					config.schedule.cron
				);
			}
		}

		// Remove old triggers
		prevScheduled.removeAll(scheduled);
		for (String serialNumber : prevScheduled) {
			try {
				scheduler.unscheduleJob(TriggerKey.triggerKey(serialNumber));
			} catch (SchedulerException e) {
				logger.error(
					"Failed to remove RRM trigger for device: " + serialNumber,
					e
				);
				continue;
			}
			logger.debug("Removed RRM trigger for device '{}'", serialNumber);
		}

		this.scheduledDevices = scheduled;
	}

	/**
	 * Synchronize triggers to the current topology, adding/updating/deleting
	 * them as necessary. This updates {@link #scheduledZones}.
	 */
	public void syncTriggersForZones() {
		Set<String> scheduled = ConcurrentHashMap.newKeySet();
		Set<String> prevScheduled = new HashSet<>();
		if (scheduledZones != null) {
			prevScheduled.addAll(scheduledZones);
		}

		// Add new triggers
		for (String zone : deviceDataManager.getZones()) {
			DeviceConfig config = deviceDataManager.getZoneConfig(zone);
			if (
				config.schedule == null ||
					config.schedule.cron == null ||
					config.schedule.cron.isEmpty()
			) {
				continue; // RRM not scheduled
			}

			try {
				CronExpression.validateExpression(config.schedule.cron);
			} catch (ParseException e) {
				logger.error(
					String.format(
						"Invalid cron expression (%s) for zone %s",
						config.schedule.cron,
						zone
					) + zone,
					e
				);
				continue;
			}

			// Create trigger
			Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity(zone)
				.forJob(job)
				.withSchedule(
					CronScheduleBuilder.cronSchedule(config.schedule.cron)
				)
				.build();
			try {
				if (!prevScheduled.contains(zone)) {
					scheduler.scheduleJob(trigger);
				} else {
					scheduler.rescheduleJob(trigger.getKey(), trigger);
				}
			} catch (SchedulerException e) {
				logger.error(
					"Failed to schedule RRM trigger for zone: " + zone,
					e
				);
				continue;
			}
			scheduled.add(zone);
			logger.debug(
				"Scheduled/updated RRM for zone '{}' at: < {} >",
				zone,
				config.schedule.cron
			);
		}

		// Remove old triggers
		prevScheduled.removeAll(scheduled);
		for (String zone : prevScheduled) {
			try {
				scheduler.unscheduleJob(TriggerKey.triggerKey(zone));
			} catch (SchedulerException e) {
				logger.error(
					"Failed to remove RRM trigger for zone: " + zone,
					e
				);
				continue;
			}
			logger.debug("Removed RRM trigger for zone '{}'", zone);
		}

		this.scheduledZones = scheduled;
	}

	/** Run RRM algorithms for the given zone. */
	protected void performRRM(String zone) {
		logger.info("Starting scheduled RRM for zone '{}'", zone);

		// Get algorithms from zone config
		DeviceConfig config = deviceDataManager.getZoneConfig(zone);
		if (config.schedule == null) {
			logger.error("RRM schedule missing for zone '{}', aborting!", zone);
			return;
		}
		if (
			config.schedule.algorithms == null ||
				config.schedule.algorithms.isEmpty()
		) {
			logger.debug("Using default RRM algorithms for zone '{}'", zone);
			config.schedule.algorithms = Arrays.asList(
				new RRMAlgorithm(
					RRMAlgorithm.AlgorithmType.OptimizeChannel.name()
				),
				new RRMAlgorithm(
					RRMAlgorithm.AlgorithmType.OptimizeTxPower.name()
				)
			);
		}

		// Execute algorithms
		for (RRMAlgorithm algo : config.schedule.algorithms) {
			RRMAlgorithm.AlgorithmResult result = algo.run(
				deviceDataManager,
				configManager,
				modeler,
				zone,
				params.dryRun,
				true /* allowDefaultMode */
			);
			logger.info(
				"'{}' result for zone '{}': {}",
				algo.getName(),
				zone,
				gson.toJson(result)
			);
		}
	}
}
