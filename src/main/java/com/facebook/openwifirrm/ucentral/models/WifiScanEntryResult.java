package com.facebook.openwifirrm.ucentral.models;

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
	 * ht_oper is short for "high throughput operator". This field contains some
	 * information already present in other fields. This is because this field was
	 * added later in order to capture some new information but also includes some
	 * redundant information. 802.11 defines the HT operator and vendors may define
	 * additional fields. HT is supported on both the 2.4 GHz and 5 GHz bands.
	 *
	 * This field is specified as 24 bytes, but it is encoded in base64. 24 bytes is
	 * 192 bits which is 32 base64 characters, so this field would be a 32-character
	 * string. However, it is likely the case that the first byte (the Element ID,
	 * which should be 61 for ht_oper) and the second byte (Length) are omitted when
	 * the wifi scan results are sent to rrm. Typically in base64, the input has
	 * bytes padded until its length is divisible by 6, so the 22 bytes would be
	 * padded by two bytes to reach 24 bytes. Typically, bytes of all zeros are
	 * padded, and when translated to base64, they are written as equals signs. This
	 * explains why ht_oper values always end with two equals signs.
	 */
	public String ht_oper;
	/**
	 * vht_oper is short for "very high throughput operator". This field contains
	 * some information already present in other fields. This is because this field
	 * was added later in order to capture some new information but also includes
	 * some redundant information. 802.11 defines the VHT operator and vendors may
	 * define additional fields. VHT is supported only on the 5 GHz band.
	 *
	 * For information about about the contents of this field, its encoding, etc.,
	 * please see the javadoc for ht_oper first. The vht_oper likely operates
	 * similarly, except instead of starting at 24 bytes, losing the last 2 bytes
	 * resulting in only 22 bytes, then appending two bytes to reach 24 bytes (24 is
	 * divisible by 6), vht_oper starts at 7 bytes, loses the last 2 bytes resulting
	 * in only 5 bytes, and appends only one byte to reach 6 bytes (6 is divisible
	 * by 6).
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
}
