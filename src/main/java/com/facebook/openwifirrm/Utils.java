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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONObject;

import com.facebook.openwifirrm.Constants.CHANNEL_WIDTH;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Generic utility methods.
 */
public class Utils {
	/** Hex value array for use in {@link #longToMac(long)}. */
	private static final char[] HEX_VALUES = "0123456789abcdef".toCharArray();

	// This class should not be instantiated.
	private Utils() {}

	/** Read a file to a UTF-8 string. */
	public static String readFile(File f) throws IOException {
		byte[] b = Files.readAllBytes(f.toPath());
		return new String(b, StandardCharsets.UTF_8);
	}

	/** Write a string to a file. */
	public static void writeFile(File f, String s)
		throws FileNotFoundException {
		try (PrintStream out = new PrintStream(new FileOutputStream(f))) {
			out.println(s);
		}
	}

	/** Write an object to a file as pretty-printed JSON. */
	public static void writeJsonFile(File f, Object o)
		throws FileNotFoundException {
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		writeFile(f, gson.toJson(o));
	}

	/** Recursively merge JSONObject 'b' into 'a'. */
	public static void jsonMerge(JSONObject a, JSONObject b) {
		for (String k : b.keySet()) {
			Object aVal = a.has(k) ? a.get(k) : null;
			Object bVal = b.get(k);
			if (aVal instanceof JSONObject && bVal instanceof JSONObject) {
				jsonMerge((JSONObject) aVal, (JSONObject) bVal);
			} else {
				a.put(k, bVal);
			}
		}
	}

	/**
	 * Convert a MAC address to an integer (6-byte) representation.
	 *
	 * If the MAC address could not be parsed, throws IllegalArgumentException.
	 */
	public static long macToLong(String addr) throws IllegalArgumentException {
		String s = addr.replace("-", "").replace(":", "").replace(".", "");
		if (s.length() != 12) {
			throw new IllegalArgumentException("Invalid MAC address format");
		}
		try {
			return Long.parseLong(s, 16);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(e);
		}
	}

	/**
	 * Convert a MAC address in integer (6-byte) representation to string
	 * notation.
	 */
	public static String longToMac(long addr) {
		char[] c = new char[17];
		c[0] = HEX_VALUES[(byte)((addr >> 44) & 0xf)];
		c[1] = HEX_VALUES[(byte)((addr >> 40) & 0xf)];
		c[2] = ':';
		c[3] = HEX_VALUES[(byte)((addr >> 36) & 0xf)];
		c[4] = HEX_VALUES[(byte)((addr >> 32) & 0xf)];
		c[5] = ':';
		c[6] = HEX_VALUES[(byte)((addr >> 28) & 0xf)];
		c[7] = HEX_VALUES[(byte)((addr >> 24) & 0xf)];
		c[8] = ':';
		c[9] = HEX_VALUES[(byte)((addr >> 20) & 0xf)];
		c[10] = HEX_VALUES[(byte)((addr >> 16) & 0xf)];
		c[11] = ':';
		c[12] = HEX_VALUES[(byte)((addr >> 12) & 0xf)];
		c[13] = HEX_VALUES[(byte)((addr >> 8) & 0xf)];
		c[14] = ':';
		c[15] = HEX_VALUES[(byte)((addr >> 4) & 0xf)];
		c[16] = HEX_VALUES[(byte)(addr & 0xf)];
		return new String(c);
	}

	/** Return a hex representation of the given byte array. */
	public static String bytesToHex(byte[] b) {
		char[] c = new char[b.length * 2];
		for (int i = 0; i < b.length; i++) {
			int v = b[i] & 0xff;
			c[i*2] = HEX_VALUES[(v >> 4) & 0xf];
			c[i*2 + 1] = HEX_VALUES[v & 0xf];
		}
		return new String(c);
	}

	public static int boolToInt(boolean b) {
		return b ? 1 : 0;
	}

	/**
	 *
	 * NOTE: some combinations of channelWidth, channel, channel2, and vhtMcsAtNss
	 * are invalid as defined by 802.11, but this is not checked here. If fidelity
	 * to 802.11 is required, the caller of this method must make sure to pass in
	 * valid parameters.
	 *
	 * @param channelWidth
	 * @param channel1     If the channel is 20 MHz, 40 MHz, or 80 MHz wide, this
	 *                     parameter should be the channel index. E.g., channel 36
	 *                     is the channel centered at 5180 MHz. For a 160 MHz wide
	 *                     channel, this parameter should be the channel index of
	 *                     the 80MHz channel that contains the primary channel. For
	 *                     a 80+80 MHz wide channel, this parameter should be the
	 *                     channel index of the primary channel.
	 * @param channel2     This should be zero unless the channel is 160MHz or 80+80
	 *                     MHz wide. If the channel is 160 MHz wide, this parameter
	 *                     should contain the channel index of the 160 MHz wide
	 *                     channel. If the channel is 80+80 MHz wide, it should be
	 *                     the channel index of the secondary 80 MHz wide channel.
	 * @param vhtMcsForNss An 8-element array where each element is between 0 and 4
	 *                     inclusive. MCS means Modulation and Coding Scheme. NSS
	 *                     means Number of Spatial Streams. There can be 1, 2, ...,
	 *                     or 8 spatial streams. For each NSS, the corresponding
	 *                     element in the array should specify which MCSs are
	 *                     supported for that NSS in the following manner: 0
	 *                     indicates support for VHT-MCS 0-7, 1 indicates support
	 *                     for VHT-MCS 0-8, 2 indicates support for VHT-MCS 0-9, and
	 *                     3 indicates that no VHT-MCS is supported for that NSS.
	 *                     For the specifics of what each VHT-MCS is, see IEEE
	 *                     802.11 2020 edition, Table "21-29" through Table "21-60".
	 * @return base64 encoded vht operator as a String
	 */
	public static String get_vht_oper(CHANNEL_WIDTH channelWidth, byte channel1, byte channel2,
			byte[] vhtMcsForNss) {
		byte[] vht_oper = new byte[5];
		boolean channelWidthByte = !(channelWidth == CHANNEL_WIDTH.MHz_20 || channelWidth == CHANNEL_WIDTH.MHz_40);
		// overflow shouldn't matter, we only care about the raw bit representation
		byte channelCenterFrequencySegment0 = channel1;
		byte channelCenterFrequencySegment1 = channel2;

		vht_oper[0] = (byte) (boolToInt(channelWidthByte));
		vht_oper[1] = channelCenterFrequencySegment0;
		vht_oper[2] = channelCenterFrequencySegment1;
		vht_oper[3] = (byte) (vhtMcsForNss[0] << 6 | vhtMcsForNss[1] << 4 | vhtMcsForNss[2] << 2 | vhtMcsForNss[3]);
		vht_oper[4] = (byte) (vhtMcsForNss[4] << 6 | vhtMcsForNss[5] << 4 | vhtMcsForNss[6] << 2 | vhtMcsForNss[7]);
		return Base64.encodeBase64String(vht_oper);
	}

	public static String get_vht_oper() {
		return get_vht_oper(CHANNEL_WIDTH.MHz_20, (byte) 36, (byte) 0, new byte[] { 0, 0, 0, 0, 0, 0, 0, 0 });
	}

	/**
	 * NOTE: some combinations of these parameters may be invalid as defined by
	 * 802.11, but this is not checked here. If fidelity to 802.11 is required, the
	 * caller of this method must make sure to pass in valid parameters. The 802.11
	 * specification has more details about the parameters.
	 *
	 * @param primaryChannel                 channel index
	 * @param secondaryChannelOffset
	 * @param staChannelWidth
	 * @param rifsMode
	 * @param htProtection
	 * @param nongreenfieldHtStasPresent
	 * @param obssNonHtStasPresent
	 * @param channelCenterFrequencySegment2
	 * @param dualBeacon
	 * @param dualCtsProtection
	 * @param stbcBeacon
	 * @return base64 encoded ht operator as a String
	 */
	public static String get_ht_oper(byte primaryChannel, byte secondaryChannelOffset, boolean staChannelWidth,
			boolean rifsMode, byte htProtection, boolean nongreenfieldHtStasPresent, boolean obssNonHtStasPresent,
			byte channelCenterFrequencySegment2, boolean dualBeacon, boolean dualCtsProtection, boolean stbcBeacon) {
		byte[] ht_oper = new byte[22];
		ht_oper[0] = primaryChannel;
		ht_oper[1] = (byte) (secondaryChannelOffset << 6 | boolToInt(staChannelWidth) << 5 | boolToInt(rifsMode) << 4);
		ht_oper[2] = (byte) (htProtection << 6 | boolToInt(nongreenfieldHtStasPresent) << 5
				| boolToInt(obssNonHtStasPresent) << 3 | channelCenterFrequencySegment2 >>> 5);
		ht_oper[3] = (byte) (channelCenterFrequencySegment2 << 5);
		ht_oper[4] = (byte) (boolToInt(dualBeacon) << 1 | boolToInt(dualCtsProtection));
		ht_oper[5] = (byte) (boolToInt(stbcBeacon) << 7);
		// the next 16 bytes are for the basic HT-MCS set
		// a default is chosen; if needed, we can add a parameter to set these
		ht_oper[6] = 0;
		ht_oper[7] = 0;
		ht_oper[8] = 0;
		ht_oper[9] = 0;
		ht_oper[10] = 0;
		ht_oper[11] = 0;
		ht_oper[12] = 0;
		ht_oper[13] = 0;
		ht_oper[14] = 0;
		ht_oper[15] = 0;
		ht_oper[16] = 0;
		ht_oper[17] = 0;

		ht_oper[18] = 0;
		ht_oper[19] = 0;
		ht_oper[20] = 0;
		ht_oper[21] = 0;

		return Base64.encodeBase64String(ht_oper);
	}

	public static String get_ht_oper() {
		return get_ht_oper((byte) 1, (byte) 0, false, false, (byte) 0, true, false, (byte) 0, false, false, false);
	}
}
