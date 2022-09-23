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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.DeviceConfig;
import com.facebook.openwifirrm.DeviceDataManager;
import com.facebook.openwifirrm.RRMConfig.ModuleConfig.ConfigManagerParams;
import com.facebook.openwifirrm.ucentral.UCentralApConfiguration;
import com.facebook.openwifirrm.ucentral.UCentralClient;
import com.facebook.openwifirrm.ucentral.UCentralUtils;
import com.facebook.openwifirrm.ucentral.gw.models.DeviceWithStatus;

/**
 * Device configuration manager module.
 */
public class ConfigManager implements Runnable {
	private static final Logger logger =
		LoggerFactory.getLogger(ConfigManager.class);

	/** The module parameters. */
	private final ConfigManagerParams params;

	/** The device data manager. */
	private final DeviceDataManager deviceDataManager;

	/** The uCentral client. */
	private final UCentralClient client;

	/** Runtime per-device data. */
	private class DeviceData {
		/** Last received device config. */
		public UCentralApConfiguration config;

		/** Last config time (in monotonic ns). */
		public Long lastConfigTimeNs;
	}

	/** Map from device serial number to runtime data. */
	private Map<String, DeviceData> deviceDataMap = new TreeMap<>();

	/** The main thread reference (i.e. where {@link #run()} is invoked). */
	private Thread mainThread;

	/** Was the main thread interrupt generated by {@link #wakeUp()}? */
	private final AtomicBoolean wakeupFlag = new AtomicBoolean(false);

	/** Is the main thread sleeping? */
	private final AtomicBoolean sleepingFlag = new AtomicBoolean(false);

	/**
	 * Set of zones for which manual config updates have been requested.
	 * <p>
	 * This is a thread-safe (concurrent) set, since it is backed by a
	 * {@code ConcurrentHashMap}, but is still statically typed as
	 * {@code Set<String>} since there is nothing like ConcurrentHashSet in
	 * Java.
	 * <p>
	 * This set is "concurrent but not synchronized" meaning it is thread-
	 * safe, but simultaneous writes are allowed for different "hash buckets".
	 */
	private Set<String> zonesToUpdate = ConcurrentHashMap.newKeySet();

	/**
	 * This set is used as a buffer to track queued updates without actually
	 * writing them to {@link #zonesToUpdate}. This is done in order to prevent
	 * the possibility of trying to update zones in quick succession (which is
	 * not allowed by the debounce timer).
	 */
	private Set<String> temporaryZonesToUpdateBuffer =
		ConcurrentHashMap.newKeySet();

	/** Config listener interface. */
	public interface ConfigListener {
		/**
		 * Receive a new device config.
		 *
		 * The listener should NOT modify the "config" parameter.
		 */
		void receiveDeviceConfig(
			String serialNumber,
			UCentralApConfiguration config
		);

		/**
		 * Process a received device config.
		 *
		 * The listener should modify the "config" parameter as necessary and
		 * return true if any changes were made, otherwise return false.
		 */
		boolean processDeviceConfig(
			String serialNumber,
			UCentralApConfiguration config
		);
	}

	/** State listeners. */
	private Map<String, ConfigListener> configListeners = new TreeMap<>();

	/** Constructor. */
	public ConfigManager(
		ConfigManagerParams params,
		DeviceDataManager deviceDataManager,
		UCentralClient client
	) {
		this.params = params;
		this.deviceDataManager = deviceDataManager;
		this.client = client;

		// Apply RRM parameters
		addConfigListener(
			getClass().getSimpleName(),
			new ConfigListener() {
				@Override
				public void receiveDeviceConfig(
					String serialNumber,
					UCentralApConfiguration config
				) {
					// do nothing
				}

				@Override
				public boolean processDeviceConfig(
					String serialNumber,
					UCentralApConfiguration config
				) {
					return applyRRMConfig(serialNumber, config);
				}
			}
		);
	}

	@Override
	public void run() {
		this.mainThread = Thread.currentThread();

		// Run application logic in a periodic loop
		while (!Thread.currentThread().isInterrupted()) {
			try {
				runImpl();
				sleepingFlag.set(true);
				Thread.sleep(params.updateIntervalMs);
				wakeupFlag.set(false);
			} catch (InterruptedException e) {
				if (wakeupFlag.getAndSet(false)) {
					// Intentional interrupt, clear flag
					logger.debug("Interrupted by wakeup!");
					Thread.interrupted();
					continue;
				} else {
					logger.error("Interrupted!", e);
					break;
				}
			} finally {
				sleepingFlag.set(false);
			}
		}
		logger.error("Thread terminated!");
	}

	/** Run single iteration of application logic. */
	private void runImpl() {
		while (!client.isInitialized()) {
			logger.trace("Waiting for ucentral client");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}

		// Fetch device list
		List<DeviceWithStatus> devices = client.getDevices();
		if (devices == null) {
			logger.error("Failed to fetch devices!");
			return;
		}
		logger.debug("Received device list of size = {}", devices.size());

		long now = System.nanoTime();

		// Apply any config updates locally
		List<String> devicesNeedingUpdate = new ArrayList<>();
		final long CONFIG_DEBOUNCE_INTERVAL_NS =
			params.configDebounceIntervalSec * 1_000_000_000L;
		Set<String> zonesToUpdateCopy = new HashSet<>(zonesToUpdate);
		// use removeAll() instead of clear() in case items are added between
		// the previous line and the following line
		zonesToUpdate.removeAll(zonesToUpdateCopy);
		// used after the loop, calculated now before the set is emptied
		final boolean shouldUpdate = !zonesToUpdateCopy.isEmpty();
		for (DeviceWithStatus device : devices) {
			// Update config structure
			DeviceData data = deviceDataMap.computeIfAbsent(
				device.serialNumber,
				k -> new DeviceData()
			);
			// Update the device only when it is still connected
			if (!device.connected) {
				logger.info(
					"Device {} is disconnected, skipping...",
					device.serialNumber
				);
				continue;
			}
			data.config = new UCentralApConfiguration(device.configuration);

			// Call receive listeners
			for (ConfigListener listener : configListeners.values()) {
				listener.receiveDeviceConfig(device.serialNumber, data.config);
			}
			// Check if there are requested updates for this zone
			// And if so, remove this zone from the set of to-be-updated zones
			boolean isEvent = zonesToUpdateCopy.remove(device.venue);
			if (params.configOnEventOnly && !isEvent) {
				logger.debug(
					"Skipping config for {} (zone not marked for updates)",
					device.serialNumber
				);
				continue;
			}

			// Check if pushing config is enabled in device config
			if (!isDeviceConfigEnabled(device.serialNumber)) {
				logger.debug(
					"Skipping config for {} (disabled in device config)",
					device.serialNumber
				);
				continue;
			}

			// Call processing listeners
			boolean modified = false;
			for (ConfigListener listener : configListeners.values()) {
				boolean wasModified = listener.processDeviceConfig(
					device.serialNumber,
					data.config
				);
				if (wasModified) {
					modified = true;
				}
			}

			// Queue config update if past debounce interval
			if (modified) {
				if (
					data.lastConfigTimeNs != null &&
						now - data.lastConfigTimeNs <
							CONFIG_DEBOUNCE_INTERVAL_NS
				) {
					logger.debug(
						"Skipping config for {} (last configured {}s ago)",
						device.serialNumber,
						(now - data.lastConfigTimeNs) / 1_000_000_000L
					);
					continue;
				} else {
					devicesNeedingUpdate.add(device.serialNumber);
				}
			}
		}

		// Send config changes to devices
		if (!params.configEnabled) {
			logger.trace("Config changes are disabled.");
		} else if (devicesNeedingUpdate.isEmpty()) {
			logger.debug("No device configs to send.");
		} else if (params.configOnEventOnly && !shouldUpdate) {
			// shouldn't happen
			logger.error(
				"ERROR!! {} device(s) queued for config update, but set of zones to update is empty.",
				devicesNeedingUpdate.size()
			);
		} else {
			logger.info(
				"Sending config to {} device(s): {}",
				devicesNeedingUpdate.size(),
				String.join(", ", devicesNeedingUpdate)
			);
			for (String serialNumber : devicesNeedingUpdate) {
				DeviceData data = deviceDataMap.get(serialNumber);
				logger.info(
					"Device {}: sending new configuration...",
					serialNumber
				);
				data.lastConfigTimeNs = System.nanoTime();
				client.configure(serialNumber, data.config.toString());
			}
		}
	}

	/** Return whether the given device has pushing device config enabled. */
	private boolean isDeviceConfigEnabled(String serialNumber) {
		DeviceConfig deviceConfig =
			deviceDataManager.getDeviceConfig(serialNumber);
		if (deviceConfig == null) {
			return false;
		}
		return deviceConfig.enableConfig;
	}

	/**
	 * Apply RRM parameters to the device.
	 *
	 * If any changes are needed, modify the config and return true.
	 * Otherwise, return false.
	 */
	private boolean applyRRMConfig(
		String serialNumber,
		UCentralApConfiguration config
	) {
		DeviceConfig deviceConfig =
			deviceDataManager.getDeviceConfig(serialNumber);
		if (deviceConfig == null || !deviceConfig.enableRRM) {
			return false;
		}

		boolean modified = false;

		// Apply channel config
		Map<String, Integer> channelList = new HashMap<>();
		if (deviceConfig.autoChannels != null) {
			channelList.putAll(deviceConfig.autoChannels);
		}
		if (deviceConfig.userChannels != null) {
			channelList.putAll(deviceConfig.userChannels);
		}
		if (!channelList.isEmpty()) {
			modified |= UCentralUtils.setRadioConfigField(
				serialNumber,
				config,
				"channel",
				channelList
			);
		}

		// Apply tx power config
		Map<String, Integer> txPowerList = new HashMap<>();
		if (deviceConfig.autoTxPowers != null) {
			txPowerList.putAll(deviceConfig.autoTxPowers);
		}
		if (deviceConfig.userTxPowers != null) {
			txPowerList.putAll(deviceConfig.userTxPowers);
		}
		if (!txPowerList.isEmpty()) {
			modified |= UCentralUtils.setRadioConfigField(
				serialNumber,
				config,
				"tx-power",
				txPowerList
			);
		}

		return modified;
	}

	/**
	 * Add/overwrite a config listener with an arbitrary identifier.
	 *
	 * The "id" string determines the order in which listeners are called.
	 */
	public void addConfigListener(String id, ConfigListener listener) {
		logger.debug("Adding config listener: {}", id);
		configListeners.put(id, listener);
	}

	/**
	 * Remove a config listener with the given identifier, returning true if
	 * anything was actually removed.
	 */
	public boolean removeConfigListener(String id) {
		logger.debug("Removing config listener: {}", id);
		return (configListeners.remove(id) != null);
	}

	/**
	 * Track the zone to be updated in the future.
	 *
	 * @param zone zone (i.e., venue), or null, which indicates all zones
	 */
	public void queueForUpdate(String zone) {
		if (zone != null) {
			temporaryZonesToUpdateBuffer.add(zone);
		} else {
			/*
			 * Here, addAll is not atomic, so read operations during the addAll
			 * may read none, some, or all of the items passed in to addAll.
			 * But, it is ok if different zones are updated at different times.
			 */
			temporaryZonesToUpdateBuffer.addAll(deviceDataManager.getZones());
		}
	}

	/**
	 * Interrupt the main thread, possibly triggering an update immediately.
	 */
	public void wakeUp() {
		zonesToUpdate.addAll(temporaryZonesToUpdateBuffer);
		temporaryZonesToUpdateBuffer.clear();
		if (mainThread != null && mainThread.isAlive() && sleepingFlag.get()) {
			wakeupFlag.set(true);
			mainThread.interrupt();
		}
	}

	/**
	 * Track the zone to be updated, then interrupt the main thread to possibly
	 * trigger an update immediately.
	 *
	 * @param zone zone (i.e., venue), or null, which indicates all zones
	 */
	public void wakeUp(String zone) {
		queueForUpdate(zone);
		wakeUp();
	}
}
