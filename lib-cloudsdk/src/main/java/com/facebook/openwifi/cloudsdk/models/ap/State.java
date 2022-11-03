/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.ap;

import java.util.Arrays;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

public class State {
	public static class Interface {
		public static class Client {
			public String mac;
			public String[] ipv4_addresses;
			public String[] ipv6_addresses;
			public String[] ports;
			// TODO last_seen

			/** Default Constructor with no args */
			private Client() {}

			/** Copy Constructor */
			private Client(Client client) {
				this.mac = client.mac;
				this.ipv4_addresses = new String[client.ipv4_addresses.length];
				for (int i = 0; i < client.ipv4_addresses.length; i++) {
					this.ipv4_addresses[i] = client.ipv4_addresses[i];
				}
				this.ipv6_addresses = new String[client.ipv6_addresses.length];
				for (int i = 0; i < client.ipv6_addresses.length; i++) {
					this.ipv6_addresses[i] = client.ipv6_addresses[i];
				}
				this.ports = new String[client.ports.length];
				for (int i = 0; i < client.ports.length; i++) {
					this.ports[i] = client.ports[i];
				}
			}
		}

		public static class SSID {
			public static class Association {
				public static class Rate {
					public long bitrate;
					public int chwidth;
					public boolean sgi;
					public boolean ht;
					public boolean vht;
					public boolean he;
					public int mcs;
					public int nss;
					public int he_gi;
					public int he_dcm;

					/** Default Constructor with no args */
					private Rate() {}

					/** Copy Constructor */
					private Rate(Rate rate) {
						this.bitrate = rate.bitrate;
						this.chwidth = rate.chwidth;
						this.sgi = rate.sgi;
						this.ht = rate.ht;
						this.vht = rate.vht;
						this.he = rate.he;
						this.mcs = rate.mcs;
						this.nss = rate.nss;
						this.he_gi = rate.he_gi;
						this.he_dcm = rate.he_dcm;
					}
				}

				public String bssid;
				public String station;
				public long connected;
				public long inactive;
				public int rssi;
				public long rx_bytes;
				public long rx_packets;
				public Rate rx_rate;
				public long tx_bytes;
				public long tx_duration;
				public long tx_failed;
				public long tx_offset;
				public long tx_packets;
				public Rate tx_rate;
				public long tx_retries;
				public int ack_signal;
				public int ack_signal_avg;
				public JsonObject[] tid_stats; // TODO: see cfg80211_tid_stats

				/** Default Constructor with no args */
				public Association() {}

				/** Copy Constructor */
				private Association(Association association) {
					this.bssid = association.bssid;
					this.station = association.station;
					this.connected = association.connected;
					this.inactive = association.inactive;
					this.rssi = association.rssi;
					this.rx_bytes = association.rx_bytes;
					this.rx_packets = association.rx_bytes;
					this.rx_rate = new Rate(association.rx_rate);
					this.tx_bytes = association.tx_bytes;
					this.tx_duration = association.tx_duration;
					this.tx_failed = association.tx_failed;
					this.tx_offset = association.tx_offset;
					this.tx_packets = association.tx_packets;
					this.tx_rate = new Rate(association.tx_rate);
					this.tx_retries = association.tx_retries;
					this.ack_signal = association.ack_signal;
					this.ack_signal_avg = association.ack_signal_avg;
					this.tid_stats =
						new JsonObject[association.tid_stats.length];
					for (int i = 0; i < association.tid_stats.length; i++) {
						this.tid_stats[i] = association.tid_stats[i].deepCopy();
					}
				}

				// TODO ipaddr_v4 - either string or object (ip4leases), but duplicated in "clients"
			}

			public Association[] associations;
			public String bssid;
			public String ssid;
			public Counters counters;
			public String iface;
			public String mode;
			public String phy;
			public JsonObject radio;

			/** Default Constructor with no args */
			private SSID() {}

			/** Copy Constructor */
			private SSID(SSID ssid) {
				this.associations = new Association[ssid.associations.length];
				for (int i = 0; i < ssid.associations.length; i++) {
					this.associations[i] =
						new Association(ssid.associations[i]);
				}
				this.bssid = ssid.bssid;
				this.ssid = ssid.ssid;
				this.counters = new Counters(ssid.counters);
				this.iface = ssid.iface;
				this.mode = ssid.mode;
				this.phy = ssid.phy;
				this.radio = ssid.radio.deepCopy();
			}
		}

		public static class Counters {
			public long collisions;
			public long multicast;
			public long rx_bytes;
			public long rx_packets;
			public long rx_errors;
			public long rx_dropped;
			public long tx_bytes;
			public long tx_packets;
			public long tx_errors;
			public long tx_dropped;

			/** Default Constructor with no args */
			private Counters() {}

			/** Copy Constructor */
			private Counters(Counters counters) {
				this.collisions = counters.collisions;
				this.multicast = counters.multicast;
				this.rx_bytes = counters.rx_bytes;
				this.rx_packets = counters.rx_packets;
				this.rx_errors = counters.rx_errors;
				this.rx_dropped = counters.rx_dropped;
				this.tx_bytes = counters.tx_bytes;
				this.tx_packets = counters.tx_packets;
				this.tx_errors = counters.tx_errors;
				this.tx_dropped = counters.tx_dropped;
			}
		}

		public Client[] clients;
		public SSID[] ssids;
		public Counters counters;
		public String location;
		public String name;
		public String ntp_server;
		public String[] dns_servers;
		public long uptime;
		// TODO
		public JsonObject ipv4;
		public JsonObject ipv6;
		public JsonObject[] lldp;
		// TODO ports ?

		/** Default Constructor with no args */
		private Interface() {};

		/** Copy Constructor */
		private Interface(Interface i) {
			this.clients = new Client[i.clients.length];
			for (int ind = 0; ind < i.clients.length; ind++) {
				this.clients[ind] = new Client(i.clients[ind]);
			}
			this.ssids = new SSID[i.ssids.length];
			for (int ind = 0; ind < i.ssids.length; ind++) {
				this.ssids[ind] = new SSID(i.ssids[ind]);
			}
			this.counters = new Counters(i.counters);
			this.location = i.location;
			this.name = i.name;
			this.ntp_server = i.ntp_server;
			this.dns_servers =
				Arrays.copyOf(i.dns_servers, i.dns_servers.length);
			this.uptime = i.uptime;
			this.ipv4 = i.ipv4.deepCopy();
			this.ipv6 = i.ipv6.deepCopy();
			this.lldp = new JsonObject[i.lldp.length];
			for (int ind = 0; ind < i.lldp.length; ind++) {
				this.lldp[ind] = i.lldp[ind].deepCopy();
			}
		}
	}

	public Interface[] interfaces;

	public static class Unit {
		public static class Memory {
			public long buffered;
			public long cached;
			public long free;
			public long total;

			/** Default Constructor with no args */
			private Memory() {}

			/** Copy Constructor */
			private Memory(Memory memory) {
				this.buffered = memory.buffered;
				this.cached = memory.cached;
				this.free = memory.free;
				this.total = memory.total;
			}
		}

		public double[] load;
		public long localtime;
		public Memory memory;
		public long uptime;

		/** Default Constructor with no args */
		public Unit() {};

		/** Copy Constructor */
		private Unit(Unit unit) {
			this.load = Arrays.copyOf(unit.load, unit.load.length);
			this.localtime = unit.localtime;
			this.memory = new Memory(unit.memory);
			this.uptime = unit.uptime;
		}
	}

	public Unit unit;

	public static class Radio {
		public long active_ms;
		public long busy_ms;
		public int channel;
		public String channel_width;
		public long noise;
		public String phy;
		public long receive_ms;
		public long transmit_ms;
		public int tx_power;

		/** Default Constructor with no args */
		public Radio() {}

		/** Copy Constructor */
		private Radio(Radio radio) {
			this.active_ms = radio.active_ms;
			this.busy_ms = radio.busy_ms;
			this.channel = radio.channel;
			this.channel_width = radio.channel_width;
			this.noise = radio.noise;
			this.phy = radio.phy;
			this.receive_ms = radio.receive_ms;
			this.transmit_ms = radio.transmit_ms;
			this.tx_power = radio.tx_power;
		}
	}

	public Radio[] radios;

	// TODO
	@SerializedName("link-state") public JsonObject linkState;
	public JsonObject gps;
	public JsonObject poe;

	/** Default Constructor with no args */
	public State() {}

	/** Copy Constructor */
	public State(State state) {
		this.radios = new Radio[state.radios.length];
		for (int i = 0; i < radios.length; i++) {
			radios[i] = new Radio(state.radios[i]);
		}
		this.interfaces = new Interface[state.interfaces.length];
		for (int i = 0; i < radios.length; i++) {
			interfaces[i] = new Interface(state.interfaces[i]);
		}
		this.linkState = state.linkState.deepCopy();
		this.gps = state.gps.deepCopy();
		this.poe = state.poe.deepCopy();
		this.unit = new Unit(state.unit);

	}
}
