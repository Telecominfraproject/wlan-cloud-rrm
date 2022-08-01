/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifirrm.Utils;
import com.facebook.openwifirrm.ucentral.UCentralUtils.ProcessedWifiScanEntry;
import com.facebook.openwifirrm.ucentral.models.State;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Database connection manager.
 */
public class DatabaseManager {
	private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

	/** The database host:port. */
	private final String server;

	/** The database user. */
	private final String user;

	/** The database password. */
	private final String password;

	/** The database name. */
	private final String dbName;

	/** The data retention interval in days (0 to disable). */
	private final int dataRetentionIntervalDays;

	/** The pooled data source. */
	private HikariDataSource ds;

	/**
	 * Constructor.
	 * @param server the database host:port (ex. "localhost:3306")
	 * @param user the database user
	 * @param password the database password
	 * @param dbName the database name
	 * @param dataRetentionIntervalDays the data retention interval in days (0 to disable)
	 */
	public DatabaseManager(
		String server, String user, String password, String dbName, int dataRetentionIntervalDays
	) {
		this.server = server;
		this.user = user;
		this.password = password;
		this.dbName = dbName;
		this.dataRetentionIntervalDays = dataRetentionIntervalDays;
	}

	/** Run database initialization. */
	public void init() throws
		InstantiationException,
		IllegalAccessException,
		ClassNotFoundException,
		SQLException {
		// Load database drivers
		Class.forName("com.mysql.cj.jdbc.Driver");

		// Create database (only place using non-pooled connection)
		try (
			Connection conn = DriverManager.getConnection(
				getConnectionUrl(""), user, password
			);
			Statement stmt = conn.createStatement()
		) {
			String sql = String.format(
				"CREATE DATABASE IF NOT EXISTS `%s`", dbName
			);
			stmt.executeUpdate(sql);
		}

		// Configure connection pooling
		initConnectionPool();

		try (
			Connection conn = getConnection();
			Statement stmt = conn.createStatement()
		) {
			// Create tables
			String sql =
				"CREATE TABLE IF NOT EXISTS `state` (" +
					"`id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, " +
					"`time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
					"`metric` VARCHAR(255) NOT NULL, " +
					"`value` BIGINT NOT NULL, " +
					"`serial` VARCHAR(63) NOT NULL" +
				") ENGINE = InnoDB DEFAULT CHARSET = UTF8";
			stmt.executeUpdate(sql);
			sql =
				"CREATE TABLE IF NOT EXISTS `wifiscan` (" +
					"`id` BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, " +
					"`time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
					"`serial` VARCHAR(63) NOT NULL" +
				") ENGINE = InnoDB DEFAULT CHARSET = UTF8";
			stmt.executeUpdate(sql);
			// TODO : add newer wifiscan fields?
			sql =
				"CREATE TABLE IF NOT EXISTS `wifiscan_results` (" +
					"`scan_id` BIGINT NOT NULL, " +
					"`bssid` BIGINT NOT NULL, " +
					"`ssid` VARCHAR(32), " +
					"`lastseen` BIGINT NOT NULL, " +
					"`rssi` INT NOT NULL, " +
					"`channel` INT NOT NULL" +
				") ENGINE = InnoDB DEFAULT CHARSET = UTF8";
			stmt.executeUpdate(sql);

			// Create clean-up event to run daily at midnight
			// TODO: do we need partitioning?
			final String EVENT_NAME = "RRM_DeleteOldRecords";
			if (dataRetentionIntervalDays > 0) {
				// Enable the event scheduler
				stmt.executeUpdate("SET GLOBAL event_scheduler = ON");

				// To handle both cases (where the event exists or doesn't yet),
				// send a no-op "CREATE EVENT" with the schedule followed by
				// "ALTER EVENT" containing the actual event body
				sql =
					"CREATE EVENT IF NOT EXISTS " + EVENT_NAME + " " +
						"ON SCHEDULE EVERY 1 DAY " +
						"STARTS (CURRENT_DATE + INTERVAL 1 DAY) " +
					"DO SELECT 1";  // no-op
				stmt.executeUpdate(sql);

				final String oldDate = "DATE_SUB(NOW(), INTERVAL " + dataRetentionIntervalDays + " DAY)";
				sql =
					"ALTER EVENT " + EVENT_NAME + " " +
					"DO BEGIN " +
						"DELETE FROM state WHERE DATE(time) < " + oldDate + "; " +
						"DELETE FROM wifiscan WHERE DATE(time) < " + oldDate + "; " +
						"DELETE wifiscan_results FROM wifiscan_results " +
							"INNER JOIN wifiscan ON wifiscan_results.scan_id = wifiscan.id " +
							"WHERE DATE(wifiscan.time) < " + oldDate + "; " +
					"END;";
				stmt.executeUpdate(sql);
			} else {
				sql = "DROP EVENT IF EXISTS " + EVENT_NAME;
				stmt.executeUpdate(sql);
			}
		}
	}

	/** Initialize database connection pooling. */
	private void initConnectionPool() {
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(getConnectionUrl(dbName));
		config.setUsername(user);
		config.setPassword(password);
		config.addDataSourceProperty("cachePrepStmts", "true");
		config.addDataSourceProperty("prepStmtCacheSize", "250");
		config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		config.addDataSourceProperty("useServerPrepStmts", "true");
		config.addDataSourceProperty("useLocalSessionState", "true");
		config.addDataSourceProperty("rewriteBatchedStatements", "true");
		config.addDataSourceProperty("cacheResultSetMetadata", "true");
		config.addDataSourceProperty("cacheServerConfiguration", "true");
		config.addDataSourceProperty("elideSetAutoCommits", "true");
		config.addDataSourceProperty("maintainTimeStats", "false");
		config.addDataSourceProperty("connectionTimeZone", "+00:00");
		ds = new HikariDataSource(config);
	}

	/** Return a pooled database connection. */
	private Connection getConnection() throws SQLException {
		return ds.getConnection();
	}

	/** Return a JDBC URL for the given database. */
	private String getConnectionUrl(String database) {
		return String.format("jdbc:mysql://%s/%s", server, database);
	}

	/** Close all database resources. */
	public void close() throws SQLException {
		if (ds != null) {
			ds.close();
			ds = null;
		}
	}

	/** Insert state record(s) into the database. */
	public void addStateRecords(List<StateRecord> records) throws SQLException {
		if (ds == null) {
			return;
		}
		if (records.isEmpty()) {
			return;
		}

		long startTime = System.nanoTime();
		try (Connection conn = getConnection()) {
			PreparedStatement stmt = conn.prepareStatement(
				"INSERT INTO `state` (`time`, `metric`, `value`, `serial`) " +
				"VALUES (?, ?, ?, ?)"
			);

			// Disable auto-commit
			boolean autoCommit = conn.getAutoCommit();
			conn.setAutoCommit(false);

			try {
				// Insert records
				for (StateRecord record : records) {
					Timestamp timestamp =
						new Timestamp(record.timestamp * 1000);
					stmt.setTimestamp(1, timestamp);
					stmt.setString(2, record.metric);
					stmt.setLong(3, record.value);
					stmt.setString(4, record.serial);
					stmt.addBatch();
				}
				stmt.executeBatch();

				// Commit changes
				conn.commit();

				logger.debug(
					"Inserted {} state row(s) in {} ms",
					records.size(),
					(System.nanoTime() - startTime) / 1_000_000L
				);
			} finally {
				// Restore auto-commit state
				conn.setAutoCommit(autoCommit);
			}
		}
	}

	/** Return the latest state records for each unique device. */
	public Map<String, State> getLatestState() throws SQLException {
		if (ds == null) {
			return null;
		}

		Map<String, State> ret = new HashMap<>();
		try (Connection conn = getConnection()) {
			// Fetch latest (device, timestamp) records
			Map<String, Timestamp> deviceToTs = new HashMap<>();
			try (Statement stmt = conn.createStatement()) {
				String sql =
					"SELECT `serial`, `time` FROM `state` " +
					"WHERE `id` IN (SELECT MAX(`id`) FROM `state` GROUP BY `serial`)";
				try (ResultSet rs = stmt.executeQuery(sql)) {
					while (rs.next()) {
						String serial = rs.getString(1);
						Timestamp time = rs.getTimestamp(2);
						deviceToTs.put(serial, time);
					}
				}
			}

			if (deviceToTs.isEmpty()) {
				return ret;  // empty database
			}

			// For each device, query all records at latest timestamp
			PreparedStatement stmt = conn.prepareStatement(
				"SELECT `metric`, `value` FROM `state` WHERE `serial` = ? AND `time` = ?"
			);
			for (Map.Entry<String, Timestamp> e : deviceToTs.entrySet()) {
				String serial = e.getKey();
				Timestamp time = e.getValue();
				stmt.setString(1, serial);
				stmt.setTimestamp(2, time);

				List<StateRecord> records = new ArrayList<>();
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						String metric = rs.getString(1);
						long value = rs.getLong(2);
						records.add(
							new StateRecord(0 /*unused*/, time.getTime(), metric, value, serial)
						);
					}
				}
				ret.put(serial, toState(records, time.getTime()));
			}
		}
		return ret;
	}

	/**
	 * Find and return a JsonObject from a JsonArray by key (matching a given
	 * string value), or insert a new JsonObject with this key-value entry if
	 * not found.
	 */
	private JsonObject getOrAddObjectFromArray(
		JsonArray a, String key, String value
	) {
		JsonObject ret = null;
		for (int i = 0, n = a.size(); i < n; i++) {
			JsonObject o = a.get(i).getAsJsonObject();
			if (o.get(key).getAsString().equals(value)) {
				ret = o;
				break;
			}
		}
		if (ret == null) {
			ret = new JsonObject();
			ret.addProperty(key, value);
			a.add(ret);
		}
		return ret;
	}

	/** Convert a list of state records to a State object. */
	private State toState(List<StateRecord> records, long ts) {
		State state = new State();
		state.unit = state.new Unit();
		state.unit.localtime = ts;

		// Parse each record
		Map<String, JsonObject> interfaces = new TreeMap<>();
		TreeMap<Integer, JsonObject> radios = new TreeMap<>();
		for (StateRecord record : records) {
			String[] tokens = record.metric.split(Pattern.quote("."));
			switch (tokens[0]) {
			case "interface":
				JsonObject iface = interfaces.computeIfAbsent(
					tokens[1], k -> {
						JsonObject o = new JsonObject();
						o.addProperty("name", k);
						return o;
					}
				);
				if (tokens.length == 3) {
					// counters
					if (!iface.has("counters")) {
						iface.add("counters", new JsonObject());
					}
					JsonObject counters = iface.getAsJsonObject("counters");
					counters.addProperty(tokens[2], record.value);
				} else if (tokens.length == 7 || tokens.length == 8) {
					// ssids.<N>.associations.<M>
					String bssid = tokens[3];
					String clientBssid = tokens[5];
					if (!iface.has("ssids")) {
						iface.add("ssids", new JsonArray());
					}
					JsonArray ssids = iface.getAsJsonArray("ssids");
					JsonObject ssid =
						getOrAddObjectFromArray(ssids, "bssid", bssid);
					if (!ssid.has("associations")) {
						ssid.add("associations", new JsonArray());
					}
					JsonArray associations = ssid.getAsJsonArray("associations");
					JsonObject association =
						getOrAddObjectFromArray(associations, "bssid", clientBssid);
					String associationKey = tokens[6];
					if (tokens.length == 7) {
						// primitive field
						association.addProperty(associationKey, record.value);
					} else {
						// object (rate key)
						if (!association.has(associationKey)) {
							association.add(associationKey, new JsonObject());
						}
						String rateKey = tokens[7];
						if (
							rateKey.equals("sgi") || rateKey.equals("ht") ||
							rateKey.equals("vht") || rateKey.equals("he")
						) {
							// boolean field
							association.getAsJsonObject(associationKey)
								.addProperty(rateKey, record.value != 0);
						} else {
							// number field
							association.getAsJsonObject(associationKey)
								.addProperty(rateKey, record.value);
						}
					}
				}
				break;
			case "radio":
				JsonObject radio = radios.computeIfAbsent(
					Integer.parseInt(tokens[1]), k -> new JsonObject()
				);
				radio.addProperty(tokens[2], record.value);
				break;
			case "unit":
				switch (tokens[1]) {
				case "uptime":
					state.unit.uptime = record.value;
					break;
				}
				break;
			}
		}

		Gson gson = new Gson();
		state.interfaces = interfaces.values().stream()
			.map(o -> gson.fromJson(o, State.Interface.class))
			.collect(Collectors.toList())
			.toArray(new State.Interface[0]);
		state.radios = new JsonObject[radios.lastKey() + 1];
		for (Map.Entry<Integer, JsonObject> entry : radios.entrySet()) {
			state.radios[entry.getKey()] = entry.getValue();
		}
		return state;
	}

	/** Insert wifi scan results into the database. */
	public void addWifiScan(
		String serialNumber, long ts, List<ProcessedWifiScanEntry> entries
	) throws SQLException {
		if (ds == null) {
			return;
		}

		long startTime = System.nanoTime();
		try (Connection conn = getConnection()) {
			// Insert scan entry to "wifiscan"
			PreparedStatement stmt = conn.prepareStatement(
				"INSERT INTO `wifiscan` (`time`, `serial`) VALUES (?, ?)",
				Statement.RETURN_GENERATED_KEYS
			);
			stmt.setTimestamp(1, new Timestamp(ts * 1000));
			stmt.setString(2, serialNumber);
			int rows = stmt.executeUpdate();
			if (rows == 0) {
				throw new SQLException(
					"Adding wifiscan entry failed (insert returned no rows)"
				);
			}

			// Retrieve generated "id" column
			long scanId;
			try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
				if (!generatedKeys.next()) {
					throw new SQLException(
						"Adding wifiscan entry failed (missing generated ID)"
					);
				}
				scanId = generatedKeys.getLong(1);
			}
			stmt.close();

			// Insert scan result entries to "wifiscan_results"
			stmt = conn.prepareStatement(
				"INSERT INTO `wifiscan_results` (" +
				  "`scan_id`, `bssid`, `ssid`, `lastseen`, `rssi`, `channel`" +
				") VALUES (?, ?, ?, ?, ?, ?)"
			);
			for (ProcessedWifiScanEntry entry : entries) {
				long bssid = 0;
				try {
					bssid = Utils.macToLong(entry.bssid);
				} catch (IllegalArgumentException e) { /* ignore */ }
				stmt.setLong(1, scanId);
				stmt.setLong(2, bssid);
				stmt.setString(3, entry.ssid);
				stmt.setLong(4, entry.last_seen);
				stmt.setInt(5, entry.signal);
				stmt.setInt(6, entry.channel);
				stmt.addBatch();
			}
			stmt.executeBatch();

			logger.debug(
				"Inserted wifi scan id {} with {} result(s) in {} ms",
				scanId,
				entries.size(),
				(System.nanoTime() - startTime) / 1_000_000L
			);
		}
	}

	/**
	 * Return up to the N latest wifiscan results for the given device as a map
	 * of timestamp to scan results.
	 */
	public Map<Long, List<ProcessedWifiScanEntry>> getLatestWifiScans(
		String serialNumber, int count
	) throws SQLException {
		if (serialNumber == null || serialNumber.isEmpty()) {
			throw new IllegalArgumentException("Invalid serialNumber");
		}
		if (count < 1) {
			throw new IllegalArgumentException("Invalid count");
		}

		if (ds == null) {
			return null;
		}

		Map<Long, List<ProcessedWifiScanEntry>> ret = new TreeMap<>();
		try (Connection conn = getConnection()) {
			// Fetch latest N scan IDs
			Map<Long, Long> scanIdToTs = new HashMap<>();
			PreparedStatement stmt1 = conn.prepareStatement(
				"SELECT `id`, `time` FROM `wifiscan` WHERE `serial` = ? " +
				"ORDER BY `id` DESC LIMIT " + count
			);
			stmt1.setString(1, serialNumber);
			try (ResultSet rs = stmt1.executeQuery()) {
				while (rs.next()) {
					long id = rs.getLong(1);
					Timestamp time = rs.getTimestamp(2);
					scanIdToTs.put(id, time.getTime());
				}
			}
			stmt1.close();
			if (scanIdToTs.isEmpty()) {
				return ret;  // no results
			}

			// Query all scan results
			try (Statement stmt2 = conn.createStatement()) {
				List<String> scanIds = scanIdToTs.keySet().stream()
					.map(i -> Long.toString(i))
					.collect(Collectors.toList());
				String sql = String.format(
					"SELECT * FROM `wifiscan_results` WHERE `scan_id` IN (%s)",
					String.join(",", scanIds)
				);
				try (ResultSet rs = stmt2.executeQuery(sql)) {
					while (rs.next()) {
						long scanId = rs.getLong("scan_id");

						ProcessedWifiScanEntry entry = new ProcessedWifiScanEntry();
						entry.channel = rs.getInt("channel");
						entry.last_seen = rs.getLong("lastseen");
						entry.signal = rs.getInt("rssi");
						entry.bssid = Utils.longToMac(rs.getLong("bssid"));
						entry.ssid = rs.getString("ssid");
						entry.tsf = scanIdToTs.getOrDefault(scanId, 0L);

						ret.computeIfAbsent(scanId, i -> new ArrayList<>())
							.add(entry);
					}
				}
			}
		}
		return ret;
	}
}
