/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides version information by reading a hardcoded resource file.
 */
public class VersionProvider implements IVersionProvider {
	/** The version file/resource. */
	private static final String VERSION_FILE = "version.txt";

	/** The cached version string. */
	private static String version;

	/** Read the version file, returning an empty string upon error. */
	public static final String get() {
		if (version == null) {
			version = Utils.readResourceToString(VERSION_FILE).strip();
		}
		return version == null ? "" : version;
	}

	@Override
	public String[] getVersion() throws Exception {
		String ver = get();
		if (ver.isEmpty()) {
			ver = "ERROR: failed to read version file";
		}
		return new String[] { ver };
	}
}
