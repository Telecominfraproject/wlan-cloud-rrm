/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Device topology and config manager.
 */
public class DeviceDataManager {
	private static final Logger logger = LoggerFactory.getLogger(DeviceDataManager.class);

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/** The device topology file. */
	private final File topologyFile;

	/** The layered device config file. */
	private final File deviceLayeredConfigFile;

	/** Lock on {@link #topology}. */
	private final ReadWriteLock topologyLock = new ReentrantReadWriteLock();

	/** Lock on {@link #deviceLayeredConfig}. */
	private final ReadWriteLock deviceLayeredConfigLock =
		new ReentrantReadWriteLock();

	/** The current device topology. */
	private DeviceTopology topology;

	/** The current layered device config. */
	private DeviceLayeredConfig deviceLayeredConfig;

	/** The cached device configs (map of serial number to computed config). */
	private Map<String, DeviceConfig> cachedDeviceConfigs =
		new ConcurrentHashMap<>();

	/** Empty constructor without backing files (ex. for unit tests). */
	public DeviceDataManager() {
		this.topologyFile = null;
		this.deviceLayeredConfigFile = null;

		this.topology = new DeviceTopology();
		this.deviceLayeredConfig = new DeviceLayeredConfig();
	}

	/**
	 * Initialize from the given files.
	 * @param topologyFile the {@link DeviceTopology} file
	 * @param deviceLayeredConfigFile the {@link DeviceLayeredConfig} file
	 * @throws IOException
	 */
	public DeviceDataManager(File topologyFile, File deviceLayeredConfigFile)
		throws IOException {
		this.topologyFile = topologyFile;
		this.deviceLayeredConfigFile = deviceLayeredConfigFile;

		// TODO: should we catch exceptions when reading files?
		this.topology = readTopology(topologyFile);
		this.deviceLayeredConfig =
			readDeviceLayeredConfig(deviceLayeredConfigFile);
	}

	/**
	 * Read the input device topology file.
	 *
	 * If the file does not exist, try to create it.
	 *
	 * @throws IOException
	 */
	private DeviceTopology readTopology(File topologyFile) throws IOException {
		DeviceTopology topo;
		if (!topologyFile.isFile()) {
			// No file, write defaults to disk
			logger.info(
				"Topology file '{}' does not exist, creating it...",
				topologyFile.getPath()
			);
			topo = new DeviceTopology();
			Utils.writeJsonFile(topologyFile, topo);
		} else {
			// Read file
			logger.info("Reading topology file '{}'", topologyFile.getPath());
			String contents = Utils.readFile(topologyFile);
			topo = gson.fromJson(contents, DeviceTopology.class);
		}
		validateTopology(topo);
		return topo;
	}

	/**
	 * Read the input device topology file.
	 *
	 * If the file does not exist, try to create it.
	 *
	 * @throws IOException
	 */
	private DeviceLayeredConfig readDeviceLayeredConfig(File deviceConfigFile)
		throws IOException {
		DeviceLayeredConfig cfg;
		if (!deviceConfigFile.isFile()) {
			// No file, write defaults to disk
			logger.info(
				"Device config file '{}' does not exist, creating it...",
				deviceConfigFile.getPath()
			);
			cfg = new DeviceLayeredConfig();
			Utils.writeJsonFile(deviceConfigFile, cfg);
		} else {
			// Read file
			logger.info(
				"Reading device config file '{}'", deviceConfigFile.getPath()
			);
			String contents = Utils.readFile(deviceConfigFile);
			cfg = gson.fromJson(contents, DeviceLayeredConfig.class);

			// Sanitize config (NOTE: topology must be initialized!)
			boolean modified = sanitizeDeviceLayeredConfig(cfg);
			if (modified) {
				Utils.writeJsonFile(deviceConfigFile, cfg);
			}
		}
		return cfg;
	}

	/** Write the current topology to the current file (if any). */
	private void saveTopology() {
		if (topologyFile != null) {
			Lock l = topologyLock.readLock();
			l.lock();
			try {
				Utils.writeJsonFile(topologyFile, topology);
			} catch (FileNotFoundException e) {
				// Callers won't be able to deal with this, so just use an
				// unchecked exception to save code
				throw new RuntimeException(e);
			} finally {
				l.unlock();
			}
		}
	}

	/** Write the current device layered config to the current file (if any). */
	private void saveDeviceLayeredConfig() {
		if (deviceLayeredConfigFile != null) {
			Lock l = deviceLayeredConfigLock.readLock();
			l.lock();
			try {
				Utils.writeJsonFile(
					deviceLayeredConfigFile, deviceLayeredConfig
				);
			} catch (FileNotFoundException e) {
				// Callers won't be able to deal with this, so just use an
				// unchecked exception to save code
				throw new RuntimeException(e);
			} finally {
				l.unlock();
			}
		}
	}

	/** Validate the topology, throwing IllegalArgumentException upon error. */
	private void validateTopology(DeviceTopology topo) {
		if (topo == null) {
			throw new NullPointerException();
		}

		Map<String, String> deviceToZone = new HashMap<>();
		for (Map.Entry<String, Set<String>> entry : topo.entrySet()) {
			String zone = entry.getKey();
			if (zone.isEmpty()) {
				throw new IllegalArgumentException(
					"Empty zone name in topology"
				);
			}
			for (String serialNumber : entry.getValue()) {
				if (serialNumber.isEmpty()) {
					throw new IllegalArgumentException(
						String.format("Empty serial number in zone '%s'", zone)
					);
				}
				String existingZone = deviceToZone.get(serialNumber);
				if (existingZone != null) {
					throw new IllegalArgumentException(
						String.format(
							"Device '%s' in multiple zones ('%s', '%s')",
							serialNumber, existingZone, zone
						)
					);
				}
				deviceToZone.put(serialNumber, zone);
			}
		}
	}

	/**
	 * Sanitized the device layered config, ex. removing empty entries or
	 * unknown APs/zones.
	 *
	 * Returns true if the input was modified.
	 */
	private boolean sanitizeDeviceLayeredConfig(DeviceLayeredConfig cfg) {
		if (cfg == null) {
			throw new NullPointerException();
		}

		boolean modified = false;

		if (cfg.networkConfig == null) {
			cfg.networkConfig = new DeviceConfig();
			modified = true;
		}
		if (cfg.zoneConfig == null) {
			cfg.zoneConfig = new TreeMap<>();
			modified = true;
		} else {
			modified |= cfg.zoneConfig.entrySet().removeIf(entry ->
				!isZoneInTopology(entry.getKey()) /* zone doesn't exist */ ||
				entry.getValue().isEmpty()  /* config object is empty */
			);
		}
		if (cfg.apConfig == null) {
			cfg.apConfig = new TreeMap<>();
			modified = true;
		} else {
			modified |= cfg.apConfig.entrySet().removeIf(entry ->
				!isDeviceInTopology(entry.getKey()) /* AP doesn't exist */ ||
				entry.getValue().isEmpty()  /* config object is empty */
			);
		}

		return modified;
	}


	/** Set the topology. May throw unchecked exceptions upon error. */
	public void setTopology(DeviceTopology topo) {
		validateTopology(topo);

		Lock l = topologyLock.writeLock();
		l.lock();
		try {
			this.topology = topo;
		} finally {
			l.unlock();
		}
		saveTopology();

		// Sanitize device layered config in case devices/zones changed
		boolean modified = sanitizeDeviceLayeredConfig(deviceLayeredConfig);
		if (modified) {
			saveDeviceLayeredConfig();
		}

		// Clear cached device configs
		cachedDeviceConfigs.clear();
	}

	/** Return the topology as a JSON string. */
	public String getTopologyJson() {
		Lock l = topologyLock.readLock();
		l.lock();
		try {
			return gson.toJson(topology);
		} finally {
			l.unlock();
		}
	}

	/** Return a copy of the topology. */
	public DeviceTopology getTopologyCopy() {
		String contents = getTopologyJson();
		return gson.fromJson(contents, DeviceTopology.class);
	}

	/** Return all zones in the topology. */
	public List<String> getZones() {
		Lock l = topologyLock.readLock();
		l.lock();
		try {
			return new ArrayList<>(topology.keySet());
		} finally {
			l.unlock();
		}
	}

	/** Return the RF zone for the given device, or null if not found. */
	public String getDeviceZone(String serialNumber) {
		if (serialNumber == null || serialNumber.isEmpty()) {
			return null;
		}
		Lock l = topologyLock.readLock();
		l.lock();
		try {
			for (Map.Entry<String, Set<String>> e : topology.entrySet()) {
				if (e.getValue().contains(serialNumber)) {
					return e.getKey();
				}
			}
			return null;
		} finally {
			l.unlock();
		}
	}

	/** Return true if the given device is present in the topology. */
	public boolean isDeviceInTopology(String serialNumber) {
		return getDeviceZone(serialNumber) != null;
	}

	/** Return true if the given RF zone is present in the topology. */
	public boolean isZoneInTopology(String zone) {
		if (zone == null || zone.isEmpty()) {
			return false;
		}
		Lock l = topologyLock.readLock();
		l.lock();
		try {
			return topology.containsKey(zone);
		} finally {
			l.unlock();
		}
	}

	/**
	 * Set the device layered config. May throw unchecked exceptions upon error.
	 */
	public void setDeviceLayeredConfig(DeviceLayeredConfig cfg) {
		sanitizeDeviceLayeredConfig(cfg);

		Lock l = deviceLayeredConfigLock.writeLock();
		l.lock();
		try {
			this.deviceLayeredConfig = cfg;
		} finally {
			l.unlock();
		}
		saveDeviceLayeredConfig();

		// Clear cached device configs
		cachedDeviceConfigs.clear();
	}

	/** Return the device config layers as a JSON string. */
	public String getDeviceLayeredConfigJson() {
		Lock l = deviceLayeredConfigLock.readLock();
		l.lock();
		try {
			return gson.toJson(deviceLayeredConfig);
		} finally {
			l.unlock();
		}
	}

	/**
	 * Compute config for the given device by applying all config layers, or
	 * return null if not present in the topology.
	 */
	private DeviceConfig computeDeviceConfig(String serialNumber) {
		return computeDeviceConfig(serialNumber, getDeviceZone(serialNumber));
	}

	/**
	 * Compute config for the given device by applying all config layers, or
	 * return null if not present in the topology.
	 */
	private DeviceConfig computeDeviceConfig(String serialNumber, String zone) {
		if (zone == null) {
			return null;
		}

		Lock l = deviceLayeredConfigLock.readLock();
		l.lock();
		try {
			DeviceConfig config = DeviceConfig.createDefault();
			config.apply(deviceLayeredConfig.networkConfig);
			config.apply(deviceLayeredConfig.zoneConfig.get(zone));
			if (serialNumber != null) {
				config.apply(deviceLayeredConfig.apConfig.get(serialNumber));
			}
			return Utils.deepCopy(config, DeviceConfig.class);
		} finally {
			l.unlock();
		}
	}

	/**
	 * Return config for the given device with all config layers applied, or
	 * null if not present in the topology.
	 */
	public DeviceConfig getDeviceConfig(String serialNumber) {
		if (serialNumber == null) {
			throw new IllegalArgumentException("Null serialNumber");
		}
		return cachedDeviceConfigs.computeIfAbsent(
			serialNumber, k -> computeDeviceConfig(k)
		);
	}

	/**
	 * Return config for the given device with all config layers applied.
	 *
	 * This method will not check if the device is present in the topology, and
	 * uses the supplied zone directly.
	 */
	public DeviceConfig getDeviceConfig(String serialNumber, String zone) {
		if (serialNumber == null) {
			throw new IllegalArgumentException("Null serialNumber");
		}
		if (zone == null) {
			throw new IllegalArgumentException("Null zone");
		}
		return cachedDeviceConfigs.computeIfAbsent(
			serialNumber, k -> computeDeviceConfig(k, zone)
		);
	}

	/**
	 * Return config (with all config layers applied) for all devices in a given
	 * zone, or null if not present in the topology.
	 * @return map of serial number to computed config
	 */
	public Map<String, DeviceConfig> getAllDeviceConfigs(String zone) {
		// Get all devices in zone
		if (zone == null || zone.isEmpty()) {
			return null;
		}
		Set<String> devicesInZone;
		Lock l = topologyLock.readLock();
		l.lock();
		try {
			devicesInZone = topology.get(zone);
		} finally {
			l.unlock();
		}
		if (devicesInZone == null) {
			return null;
		}

		// Compute config for all devices
		Map<String, DeviceConfig> configMap = new HashMap<>();
		for (String serialNumber : devicesInZone) {
			configMap.put(serialNumber, getDeviceConfig(serialNumber, zone));
		}
		return configMap;
	}

	/** Set the device network config. */
	public void setDeviceNetworkConfig(DeviceConfig networkConfig) {
		Lock l = deviceLayeredConfigLock.writeLock();
		l.lock();
		try {
			deviceLayeredConfig.networkConfig = networkConfig;
			sanitizeDeviceLayeredConfig(deviceLayeredConfig);
		} finally {
			l.unlock();
		}
		cachedDeviceConfigs.clear();
		saveDeviceLayeredConfig();
	}

	/** Set the device zone config for the given zone, or erase it if null. */
	public void setDeviceZoneConfig(String zone, DeviceConfig zoneConfig) {
		if (zone == null || zone.isEmpty()) {
			throw new IllegalArgumentException("Empty zone");
		}
		if (zoneConfig != null && !isZoneInTopology(zone)) {
			throw new IllegalArgumentException("Unknown zone");
		}

		Lock l = deviceLayeredConfigLock.writeLock();
		l.lock();
		try {
			if (zoneConfig != null) {
				deviceLayeredConfig.zoneConfig.put(zone, zoneConfig);
			} else {
				deviceLayeredConfig.zoneConfig.remove(zone);
			}
			sanitizeDeviceLayeredConfig(deviceLayeredConfig);
		} finally {
			l.unlock();
		}
		cachedDeviceConfigs.clear();
		saveDeviceLayeredConfig();
	}

	/** Set the device AP config for the given AP, or erase it if null. */
	public void setDeviceApConfig(String serialNumber, DeviceConfig apConfig) {
		if (serialNumber == null || serialNumber.isEmpty()) {
			throw new IllegalArgumentException("Empty serialNumber");
		}
		if (apConfig != null && !isDeviceInTopology(serialNumber)) {
			throw new IllegalArgumentException("Unknown serialNumber");
		}

		Lock l = deviceLayeredConfigLock.writeLock();
		l.lock();
		try {
			if (apConfig != null) {
				deviceLayeredConfig.apConfig.put(serialNumber, apConfig);
			} else {
				deviceLayeredConfig.apConfig.remove(serialNumber);
			}
			sanitizeDeviceLayeredConfig(deviceLayeredConfig);
		} finally {
			l.unlock();
		}
		cachedDeviceConfigs.remove(serialNumber);
		saveDeviceLayeredConfig();
	}

	/** Device AP config layer update interface. */
	public interface DeviceApConfigFunction {
		/** Update the AP config layer. */
		void update(Map<String, DeviceConfig> apConfig);
	}

	/** Apply updates to the device AP config layer (under a write lock). */
	public void updateDeviceApConfig(DeviceApConfigFunction fn) {
		Lock l = deviceLayeredConfigLock.writeLock();
		l.lock();
		try {
			fn.update(deviceLayeredConfig.apConfig);
			sanitizeDeviceLayeredConfig(deviceLayeredConfig);
		} finally {
			l.unlock();
		}
		cachedDeviceConfigs.clear();
		saveDeviceLayeredConfig();
	}

	/**
	 * Return config for the given zone with all config layers applied. It is
	 * the caller's responsibility to ensure the zone exists in the topology.
	 */
	public DeviceConfig getZoneConfig(String zone) {
		if (zone == null) {
			throw new IllegalArgumentException("Null zone");
		}

		return computeDeviceConfig(null, zone);
	}
}
