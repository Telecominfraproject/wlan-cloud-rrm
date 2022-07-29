package com.facebook.openwifirrm.ucentral.models;

import com.google.gson.JsonArray;

/** Represents a single entry in wifi scan results. */
public final class WifiScanEntry {
	public int channel;
	public long last_seen;
	/** Signal strength measured in dBm */
	public int signal;
	/** BSSID is the MAC address of the device */
	public String bssid;
	public String ssid;
	public long tsf;
	/** High throughput operator */
	public String ht_oper;
	/** Very high throughput operator */
	public String vht_oper;
	public int capability;
	public int frequency;
	public JsonArray ies;

	/** Default Constructor. */
	public WifiScanEntry() {
	}

	/** Copy constructor. */
	public WifiScanEntry(WifiScanEntry o) {
		this.channel = o.channel;
		this.last_seen = o.last_seen;
		this.signal = o.signal;
		this.bssid = o.bssid;
		this.ssid = o.ssid;
		this.tsf = o.tsf;
		this.ht_oper = o.ht_oper;
		this.vht_oper = o.vht_oper;
		this.capability = o.capability;
		this.frequency = o.frequency;
		this.ies = o.ies;
	}
}
