/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.facebook.openwifirrm.mysql.StateRecord;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class DataCollectorTest {
	@Test
	void test_parseStateRecord() throws Exception {
		final String serialNumber = "112233445566";
		final String payloadJson =
			"{\"serial\":\"112233445566\",\"state\":{\"interfaces\":[" +
			"{\"clients\":[{\"ipv4_addresses\":[\"192.168.20.1\"]," +
			"\"ipv6_addresses\":[\"fe80:0:0:0:230:18ff:fe05:d0d0\"]," +
			"\"mac\":\"00:30:18:05:d0:d0\",\"ports\":[\"eth1\"]}," +
			"{\"ipv6_addresses\":[\"fe80:0:0:0:b54d:e239:114a:6292\"]," +
			"\"mac\":\"5c:3a:45:2d:34:d1\",\"ports\":[\"wlan0\"]}," +
			"{\"ipv6_addresses\":[\"fe80:0:0:0:d044:3aff:fe7e:1978\"]," +
			"\"mac\":\"aa:bb:cc:dd:ee:ff\",\"ports\":[\"eth1\"]}]," +
			"\"counters\":{\"collisions\":0,\"multicast\":34," +
			"\"rx_bytes\":10825,\"rx_dropped\":0,\"rx_errors\":0," +
			"\"rx_packets\":150,\"tx_bytes\":1931,\"tx_dropped\":0," +
			"\"tx_errors\":0,\"tx_packets\":6},\"dns_servers\":[\"8.8.8.8\"]," +
			"\"ipv4\":{\"addresses\":[\"192.168.16.105/20\"]," +
			"\"leasetime\":43200},\"location\":\"/interfaces/0\"," +
			"\"name\":\"up0v0\",\"ssids\":[{\"associations\":[" +
			"{\"bssid\":\"5c:3a:45:2d:34:d1\",\"connected\":2061," +
			"\"inactive\":0,\"rssi\":-73,\"rx_bytes\":225426," +
			"\"rx_packets\":1119,\"rx_rate\":{\"bitrate\":263300," +
			"\"chwidth\":80,\"mcs\":6,\"nss\":1,\"vht\":true}," +
			"\"station\":\"aa:00:00:00:00:01\",\"tx_bytes\":341611," +
			"\"tx_duration\":3243,\"tx_failed\":0,\"tx_offset\":0," +
			"\"tx_packets\":1304,\"tx_rate\":{\"bitrate\":526600," +
			"\"chwidth\":80,\"mcs\":6,\"nss\":2,\"sgi\":true,\"vht\":true}," +
			"\"tx_retries\":0}],\"bssid\":\"bb:00:00:00:00:01\"," +
			"\"counters\":{\"collisions\":0,\"multicast\":0," +
			"\"rx_bytes\":202281,\"rx_dropped\":0,\"rx_errors\":0," +
			"\"rx_packets\":1123,\"tx_bytes\":352404,\"tx_dropped\":0," +
			"\"tx_errors\":0,\"tx_packets\":1442},\"iface\":\"wlan0\"," +
			"\"mode\":\"ap\",\"phy\":\"platform/soc/c000000.wifi\"," +
			"\"radio\":{\"$ref\":\"#/radios/0\"},\"ssid\":\"test1\"}," +
			"{\"bssid\":\"cc:00:00:00:00:01\",\"counters\":{\"collisions\":0," +
			"\"multicast\":0,\"rx_bytes\":0,\"rx_dropped\":0,\"rx_errors\":0," +
			"\"rx_packets\":0,\"tx_bytes\":10056,\"tx_dropped\":0," +
			"\"tx_errors\":0,\"tx_packets\":132},\"iface\":\"wlan1\"," +
			"\"mode\":\"ap\",\"phy\":\"platform/soc/c000000.wifi+1\"," +
			"\"radio\":{\"$ref\":\"#/radios/1\"},\"ssid\":\"test1\"}]," +
			"\"uptime\":73067},{\"counters\":{\"collisions\":0," +
			"\"multicast\":0,\"rx_bytes\":0,\"rx_dropped\":0,\"rx_errors\":0," +
			"\"rx_packets\":0,\"tx_bytes\":0,\"tx_dropped\":0," +
			"\"tx_errors\":0,\"tx_packets\":0},\"ipv4\":{\"addresses\":[" +
			"\"192.168.1.1/24\"]},\"location\":\"/interfaces/1\"," +
			"\"name\":\"down1v0\",\"uptime\":73074}],\"link-state\":" +
			"{\"lan\":{\"eth1\":{\"carrier\":0},\"eth2\":{\"carrier\":0}}," +
			"\"wan\":{\"eth0\":{\"carrier\":1,\"duplex\":\"full\"," +
			"\"speed\":1000}}},\"radios\":[{\"active_ms\":72987829," +
			"\"busy_ms\":1881608,\"channel\":52,\"channel_width\":\"80\"," +
			"\"noise\":-105,\"phy\":\"platform/soc/c000000.wifi\"," +
			"\"receive_ms\":28277,\"temperature\":61,\"transmit_ms\":381608," +
			"\"tx_power\":24},{\"active_ms\":73049815,\"busy_ms\":7237038," +
			"\"channel\":11,\"channel_width\":\"20\",\"noise\":-101," +
			"\"phy\":\"platform/soc/c000000.wifi+1\",\"receive_ms\":8180," +
			"\"temperature\":61,\"transmit_ms\":316158,\"tx_power\":30}]," +
			"\"unit\":{\"load\":[0,0,0],\"localtime\":1649306810,\"memory\":" +
			"{\"buffered\":9961472,\"cached\":27217920,\"free\":757035008," +
			"\"total\":973139968},\"uptime\":73107}},\"uuid\":1648808043}";
		JsonObject payload = new Gson().fromJson(payloadJson, JsonObject.class);

		// Parse into records
		List<StateRecord> results = new ArrayList<>();
		DataCollector.parseStateRecord(serialNumber, payload, results);
		assertEquals(51, results.size());
		assertEquals(1649306810L, results.get(0).timestamp);
		assertEquals(serialNumber, results.get(0).serial);

		// Convert to map and check individual metrics
		Map<String, StateRecord> resultMap = new HashMap<>();
		for (StateRecord record : results) {
			resultMap.put(record.metric, record);
		}

		// Interface counters
		StateRecord record = resultMap.get("interface.up0v0.rx_bytes");
		assertNotNull(record);
		assertEquals(10825L, record.value);

		// Interface association counters
		// - primitive field
		record = resultMap.get(
			"interface.up0v0.bssid.bb:00:00:00:00:01.client.5c:3a:45:2d:34:d1.tx_bytes"
		);
		assertNotNull(record);
		assertEquals(341611L, record.value);
		// - rate key (number)
		record = resultMap.get(
			"interface.up0v0.bssid.bb:00:00:00:00:01.client.5c:3a:45:2d:34:d1.rx_rate.bitrate"
		);
		assertNotNull(record);
		assertEquals(263300L, record.value);
		// - rate key (boolean)
		record = resultMap.get(
			"interface.up0v0.bssid.bb:00:00:00:00:01.client.5c:3a:45:2d:34:d1.rx_rate.vht"
		);
		assertNotNull(record);
		assertEquals(1L, record.value);

		// Radio stats
		record = resultMap.get("radio.1.noise");
		assertNotNull(record);
		assertEquals(-101L, record.value);

		// Unit stats
		record = resultMap.get("unit.uptime");
		assertNotNull(record);
		assertEquals(73107L, record.value);
	}
}
