/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral.prov.models;

import java.util.List;

public class RRMDetails {
	public static class RRMDetailsImpl {
		public String vendor;
		public String schedule;
		public List<RRMAlgorithmDetails> algorithms;
	}

	public RRMDetailsImpl rrm;
}
