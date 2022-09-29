/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.util.Objects;

import com.facebook.openwifirrm.ucentral.informationelement.Country;
import com.facebook.openwifirrm.ucentral.informationelement.LocalPowerConstraint;
import com.facebook.openwifirrm.ucentral.informationelement.QbssLoad;
import com.facebook.openwifirrm.ucentral.informationelement.TxPwrInfo;
import com.facebook.openwifirrm.ucentral.models.WifiScanEntryResult;

/**
 * Extends {@link WifiScanEntryResult} to track the response time of the entry.
 */
public class WifiScanEntry extends WifiScanEntryResult {
	/**
	 * Unix time in milliseconds (ms). This field is not defined in the uCentral
	 * API. This is added it because {@link WifiScanEntryResult#tsf} is an unknown
	 * time reference.
	 */
	public long unixTimeMs;
	public Country country;
	public QbssLoad qbssLoad;
	public LocalPowerConstraint localPowerConstraint;
	public TxPwrInfo txPwrInfo;

	/** Default Constructor. */
	public WifiScanEntry() {}

	/** Copy Constructor. */
	public WifiScanEntry(WifiScanEntry o) {
		super(o);
		this.unixTimeMs = o.unixTimeMs;
		this.country = o.country;
		this.qbssLoad = o.qbssLoad;
		this.localPowerConstraint = o.localPowerConstraint;
		this.txPwrInfo = o.txPwrInfo;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(
			country,
			localPowerConstraint,
			qbssLoad,
			txPwrInfo,
			unixTimeMs
		);
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!super.equals(obj)) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		WifiScanEntry other = (WifiScanEntry) obj;
		return Objects.equals(country, other.country) && Objects.equals(
			localPowerConstraint,
			other.localPowerConstraint
		) && Objects.equals(qbssLoad, other.qbssLoad) && Objects
			.equals(txPwrInfo, other.txPwrInfo) &&
			unixTimeMs == other.unixTimeMs;
	}

	@Override
	public String toString() {
		return String.format(
			"WifiScanEntry[signal=%d, bssid=%s, unixTimeMs=%d]",
			signal,
			bssid,
			unixTimeMs
		);
	}

}