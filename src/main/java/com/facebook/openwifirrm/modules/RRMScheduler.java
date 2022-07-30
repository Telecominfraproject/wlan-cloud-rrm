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

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.RRMSchedulerParams;
import com.facebook.openwifirrm.RRMSchedule.RRMAlgorithm;
import com.facebook.openwifirrm.optimizers.ChannelOptimizer;
import com.facebook.openwifirrm.optimizers.LeastUsedChannelOptimizer;
import com.facebook.openwifirrm.optimizers.LocationBasedOptimalTPC;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApApTPC;
import com.facebook.openwifirrm.optimizers.MeasurementBasedApClientTPC;
import com.facebook.openwifirrm.optimizers.RandomChannelInitializer;
import com.facebook.openwifirrm.optimizers.RandomTxPowerInitializer;
import com.facebook.openwifirrm.optimizers.TPC;
import com.facebook.openwifirrm.optimizers.UnmanagedApAwareChannelOptimizer;

/**
 * RRM scheduler, implemented using Quartz.
 */
public class RRMScheduler {
	private static final Logger logger = LoggerFactory.getLogger(RRMScheduler.class);

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

	/** RRM job. */
	public static class RRMJob implements Job {
		@Override
		public void execute(JobExecutionContext context) throws JobExecutionException {
			String zone = context.getTrigger().getKey().getName();
			logger.debug("Executing job for zone: {}", zone);
			try {
				SchedulerContext schedulerContext = context.getScheduler().getContext();
				RRMScheduler instance =
					(RRMScheduler) schedulerContext.get(SCHEDULER_CONTEXT_RRMSCHEDULER);
				instance.performRRM(zone);
			} catch (SchedulerException e) {
				throw new JobExecutionException(e);
			}
		}
	}

	/** Constructor. */
	public RRMScheduler(
		RRMSchedulerParams params, DeviceDataManager deviceDataManager
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
			logger.info("Scheduled {} RRM trigger(s)", scheduledZones.size());

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
	 * them as necessary. This updates {@link #scheduledZones}.
	 */
	public void syncTriggers() {
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
				continue;  // RRM not scheduled
			}

			// Create trigger
			Trigger trigger = TriggerBuilder.newTrigger()
				.withIdentity(zone)
				.forJob(job)
				.withSchedule(
					CronScheduleBuilder.cronSchedule(config.schedule.cron)
				).build();
			try {
				if (!prevScheduled.contains(zone)) {
					scheduler.scheduleJob(trigger);
				} else {
					scheduler.rescheduleJob(trigger.getKey(), trigger);
				}
			} catch (SchedulerException e) {
				logger.error(
					"Failed to schedule RRM trigger for zone: " + zone, e
				);
				continue;
			}
			scheduled.add(zone);
			logger.debug(
				"Scheduled/updated RRM for zone '{}' at: < {} >",
				zone, config.schedule.cron
			);
		}

		// Remove old triggers
		prevScheduled.removeAll(scheduled);
		for (String zone : prevScheduled) {
			try {
				scheduler.unscheduleJob(TriggerKey.triggerKey(zone));
			} catch (SchedulerException e) {
				logger.error(
					"Failed to remove RRM trigger for zone: " + zone, e
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

		// TODO better place for these definitions
		final String RRM_ALGORITHM_CHANNEL = "optimizeChannel";
		final String RRM_ALGORITHM_TPC = "optimizeTxPower";

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
				new RRMAlgorithm(RRM_ALGORITHM_CHANNEL, null),
				new RRMAlgorithm(RRM_ALGORITHM_TPC, null)
			);
		}

		// Execute algorithms
		for (RRMAlgorithm algo : config.schedule.algorithms) {
			if (algo.name == null) {
				continue;
			}

			String mode = (algo.args != null)
				? algo.args.getOrDefault("mode", "")
				: "";

			// TODO de-dupe with ApiServer code
			switch (algo.name) {
				case RRM_ALGORITHM_CHANNEL: {
					logger.info(
						"> Zone '{}': Running channel optimizer...", zone
					);
					ChannelOptimizer optimizer;
					switch (mode) {
					case "random":
						optimizer = new RandomChannelInitializer(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					case "least_used":
						optimizer = new LeastUsedChannelOptimizer(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					case "unmanaged_aware":
					default:
						optimizer = new UnmanagedApAwareChannelOptimizer(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					}
					Map<String, Map<String, Integer>> channelMap =
						optimizer.computeChannelMap();
					optimizer.applyConfig(
						deviceDataManager, configManager, channelMap
					);
					break;
				}

				case RRM_ALGORITHM_TPC: {
					logger.info(
						"> Zone '{}': Running tx power optimizer...", zone
					);
					TPC optimizer;
					switch (mode) {
					case "random":
						optimizer = new RandomTxPowerInitializer(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					case "measure_ap_client":
						optimizer = new MeasurementBasedApClientTPC(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					case "measure_ap_ap":
					default:
						optimizer = new MeasurementBasedApApTPC(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					case "location_optimal":
						optimizer = new LocationBasedOptimalTPC(
							modeler.getDataModelCopy(), zone, deviceDataManager
						);
						break;
					}
					Map<String, Map<String, Integer>> txPowerMap =
						optimizer.computeTxPowerMap();
					optimizer.applyConfig(
						deviceDataManager, configManager, txPowerMap
					);
					break;
				}
			}
		}
	}
}
