/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.models;

import java.util.Objects;

import com.google.gson.JsonArray;

/** Represents a single entry in wifi scan results. */
public class WifiScanEntryResult {
	public int channel;
	public long last_seen;
	/** Signal strength measured in dBm */
	public int signal;
	/** BSSID is the MAC address of the device */
	public String bssid;
	public String ssid;
	public long tsf;
	/**
	 * ht_oper is short for "high throughput operation element". Note that this
	 * field, when non-null, contains some information already present in other
	 * fields. This field may be null, however, since pre-802.11n BSSs do not
	 * support HT. For BSSs that do support HT, HT is supported on the 2G and 5G
	 * bands.
	 *
	 * This field is specified as 24 bytes, but it is encoded in base64. It is
	 * likely the case that the first byte (the Element ID, which should be 61 for
	 * ht_oper) and the second byte (Length) are omitted in the wifi scan results,
	 * resulting in 22 bytes, which translates to a 32 byte base64 encoded String.
	 */
	public String ht_oper;
	/**
	 * vht_oper is short for "very high throughput operation element". Note that
	 * this field, when non-null, contains some information already present in
	 * other fields. This field may be null, however, since pre-802.11ac BSSs do
	 * not support HT. For BSSs that do support VHT, VHT is supported on the 5G
	 * band. VHT operation is controlled by both the HT operation element and
	 * the VHT operation element.
	 *
	 * For information about about the contents of this field, its encoding,
	 * etc., please see the javadoc for {@link #ht_oper} first. The vht_oper
	 * likely operates similarly.
	 */
	public String vht_oper;
	public int capability;
	public int frequency;
	/** IE = information element */
	public JsonArray ies;

	/** Default Constructor. */
	public WifiScanEntryResult() {}

	/** Copy Constructor. */
	public WifiScanEntryResult(WifiScanEntryResult o) {
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

	@Override
	public int hashCode() {
		return Objects.hash(bssid, capability, channel, frequency, ht_oper, ies, last_seen, signal, ssid, tsf,
				vht_oper);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WifiScanEntryResult other = (WifiScanEntryResult) obj;
		return Objects.equals(bssid, other.bssid) && capability == other.capability && channel == other.channel
				&& frequency == other.frequency && Objects.equals(ht_oper, other.ht_oper)
				&& Objects.equals(ies, other.ies) && last_seen == other.last_seen && signal == other.signal
				&& Objects.equals(ssid, other.ssid) && tsf == other.tsf && Objects.equals(vht_oper, other.vht_oper);
	}

	@Override
	public String toString() {
		return String.format("WifiScanEntryResult[signal=%d, bssid=%s]", signal, bssid);
	}
}
