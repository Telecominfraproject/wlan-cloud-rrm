/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.cloudsdk.models.gw;

import java.util.List;

public class SystemInfoResults {
	public static class Certificate {
		public String filename;
		public long expires;
	}

	public String version;
	public long uptime;
	public long start;
	public String os;
	public int processors;
	public String hostname;
	public List<Certificate> certificates;
}
