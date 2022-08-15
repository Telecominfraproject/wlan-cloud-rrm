/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

/**
 * Constants related to uCentral.
 */
public final class UCentralConstants {

	// eventually, we may add more bands (e.g., 6G)
	/** String of the 2.4 GHz band */
	public static final String BAND_2G = "2G";
	/** String of the 5 GHz band */
	public static final String BAND_5G = "5G";
	/** List of all bands */
	public static final String[] BANDS = new String[] { BAND_2G, BAND_5G };

	// This class should not be instantiated.
	private UCentralConstants() {}

}
