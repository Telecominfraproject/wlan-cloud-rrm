/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import com.facebook.openwifi.rrm.ucentral.UCentralConstants;

public class DeviceConfigTest {
	@Test
	void test_isEmpty() throws Exception {
		DeviceConfig config = new DeviceConfig();
		assertTrue(config.isEmpty());

		config.enableConfig = false;
		assertFalse(config.isEmpty());
		config.enableConfig = null;
		assertTrue(config.isEmpty());

		config.userChannels =
			Collections.singletonMap(UCentralConstants.BAND_2G, 1);
		assertFalse(config.isEmpty());
		config.userChannels = new HashMap<>();
		assertFalse(config.isEmpty());
		config.userChannels = null;
		assertTrue(config.isEmpty());
	}

	@Test
	void test_apply() throws Exception {
		DeviceConfig config1 = new DeviceConfig();
		DeviceConfig config2 = new DeviceConfig();
		config1.apply(config2);
		config2.apply(config1);
		assertTrue(config1.isEmpty());
		assertTrue(config2.isEmpty());

		config1.enableRRM = true;
		config1.apply(config2);
		assertEquals(true, config1.enableRRM);
		config2.enableRRM = true;
		config1.apply(config2);
		assertEquals(true, config1.enableRRM);
		config2.enableRRM = false;
		config1.apply(config2);
		assertEquals(false, config1.enableRRM);
		config1.enableRRM = null;
		config1.apply(config2);
		assertEquals(false, config1.enableRRM);
	}
}
