/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.modules;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class RRMSchedulerTest {
	@Test
	void test_parseIntoQuartzCron() throws Exception {
		// missing one field (requires 6, only 5 given)
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * *"));

		// more than maximum number of fields (7 max, has 8)
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * * * *"));

		// correct (6 fields)
		assertEquals(
			"* * * ? * *",
			RRMScheduler.parseIntoQuartzCron("* * * * * *")
		);

		// correct (7 fields)
		assertEquals(
			"* * * ? * * *",
			RRMScheduler.parseIntoQuartzCron("* * * * * * *")
		);

		// correct value other than * for day of month
		assertEquals(
			"* * * 1 * ?",
			RRMScheduler.parseIntoQuartzCron("* * * 1 * *")
		);
		assertEquals(
			"* * * 1 * ? *",
			RRMScheduler.parseIntoQuartzCron("* * * 1 * * *")
		);
		assertEquals(
			"* * * 1/2 * ?",
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * *")
		);
		assertEquals(
			"* * * 1/2 * ? *",
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * * *")
		);
		assertEquals(
			"* * * 1-2 * ?",
			RRMScheduler.parseIntoQuartzCron("* * * 1-2 * *")
		);
		assertEquals(
			"* * * 1-2 * ? *",
			RRMScheduler.parseIntoQuartzCron("* * * 1-2 * * *")
		);
		assertEquals(
			"* * * 1,2 * ?",
			RRMScheduler.parseIntoQuartzCron("* * * 1,2 * *")
		);
		assertEquals(
			"* * * 1,2 * ? *",
			RRMScheduler.parseIntoQuartzCron("* * * 1,2 * * *")
		);

		// wrong value other than * for day of month - 0 is not a valid day of month
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * * *"));

		// correct value other than * for day of month
		assertEquals(
			"* * * ? * 1",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1")
		);
		assertEquals(
			"* * * ? * 1 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1 *")
		);
		assertEquals(
			"* * * ? * 1/3",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/3")
		);
		assertEquals(
			"* * * ? * 1/3 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/3 *")
		);
		assertEquals(
			"* * * ? * 1-3",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-3")
		);
		assertEquals(
			"* * * ? * 1-3 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-3 *")
		);
		assertEquals(
			"* * * ? * 1,3",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,3")
		);
		assertEquals(
			"* * * ? * 1,3 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,3 *")
		);

		// correct value other than * for day of month, make sure 0 turns into 7
		assertEquals(
			"* * * ? * 7",
			RRMScheduler.parseIntoQuartzCron("* * * * * 0")
		);
		assertEquals(
			"* * * ? * 7 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 0 *")
		);
		assertEquals(
			"* * * ? * 1/7",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/0")
		);
		assertEquals(
			"* * * ? * 1/7 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/0 *")
		);
		assertEquals(
			"* * * ? * 1-7",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-0")
		);
		assertEquals(
			"* * * ? * 1-7 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-0 *")
		);
		assertEquals(
			"* * * ? * 1,7",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,0")
		);
		assertEquals(
			"* * * ? * 1,7 *",
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,0 *")
		);

		// wrong value other than * for day of month - 8 is not a valid day of week
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7/8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7/8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7-8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7-8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7,8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * 7,8 *"));
	}
}
