/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk;

import java.util.Objects;

import com.facebook.openwifi.cloudsdk.ies.Country;
import com.facebook.openwifi.cloudsdk.ies.LocalPowerConstraint;
import com.facebook.openwifi.cloudsdk.ies.QbssLoad;
import com.facebook.openwifi.cloudsdk.ies.TxPwrInfo;

/** Wrapper class containing information elements */
public final class InformationElements {

	public Country country;
	public QbssLoad qbssLoad;
	public LocalPowerConstraint localPowerConstraint;
	public TxPwrInfo txPwrInfo;

	@Override
	public int hashCode() {
		return Objects.hash(country, localPowerConstraint, qbssLoad, txPwrInfo);
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
		InformationElements other = (InformationElements) obj;
		return Objects.equals(country, other.country) && Objects.equals(
			localPowerConstraint,
			other.localPowerConstraint
		) && Objects.equals(qbssLoad, other.qbssLoad) &&
			Objects.equals(txPwrInfo, other.txPwrInfo);
	}

}
