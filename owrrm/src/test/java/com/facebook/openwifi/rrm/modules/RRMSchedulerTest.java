/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.modules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

public class RRMSchedulerTest {
	@Test
	void test_parseIntoQuartzCron() throws Exception {
		// missing one field (requires 6, only 5 given)
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * *"));

		// more than maximum number of fields (7 max, has 8)
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * * * * * *"));

		// correct (6 fields)
		assertArrayEquals(
			new String[] { "* * * ? * *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * *")
		);

		// correct (7 fields)
		assertArrayEquals(
			new String[] { "* * * ? * * *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * * *")
		);

		// correct value other than * for day of month
		assertArrayEquals(
			new String[] { "* * * 1 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1/2 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1/2 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1-2 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-2 * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1-2 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-2 * * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1,2 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1,2 * *")
		);
		assertArrayEquals(
			new String[] { "* * * 1,2 * ? *" },
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
		assertArrayEquals(
			new String[] { "* * * ? * 1" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/3" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/3")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/3 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/3 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-3" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-3")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-3 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-3 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1,3" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,3")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1,3 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,3 *")
		);

		// correct value other than * for day of month, make sure 0 turns into 7
		assertArrayEquals(
			new String[] { "* * * ? * 7" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 7 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1/0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7 *" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1-0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1,7" },
			RRMScheduler.parseIntoQuartzCron("* * * * * 1,0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1,7 *" },
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

		// correct value for both day of week and day of month
		assertArrayEquals(
			new String[] { "* * * ? * 7", "* * * 1 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 7 *", "* * * 1 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 0 *")
		);

		assertArrayEquals(
			new String[] { "* * * ? * 1/7", "* * * 1 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 1/0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1", "* * * 1/2 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * 1")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7", "* * * 1/2 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * 1/0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7 *", "* * * 1 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 1/0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1 *", "* * * 1/2 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * 1 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7 *", "* * * 1/2 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/2 * 1/0 *")
		);

		assertArrayEquals(
			new String[] { "* * * ? * 1-7", "* * * 1 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 1-0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1", "* * * 1-3 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7", "* * * 1-3 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1-0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7 *", "* * * 1 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1 * 1-0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1 *", "* * * 1-3 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7 *", "* * * 1-3 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1-0 *")
		);

		assertArrayEquals(
			new String[] { "* * * ? * 1-7", "* * * 1/3 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/3 * 1-0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7", "* * * 1-3 * ?" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1/0")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1-7 *", "* * * 1/3 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1/3 * 1-0 *")
		);
		assertArrayEquals(
			new String[] { "* * * ? * 1/7 *", "* * * 1-3 * ? *" },
			RRMScheduler.parseIntoQuartzCron("* * * 1-3 * 1/0 *")
		);

		// wrong value for either day of week or day of month
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7/8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7/8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7-8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7-8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7,8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0 * 7,8 *"));

		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7/8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7/8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7-8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7-8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7,8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0/1 * 7,8 *"));

		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7/8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7/8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7-8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7-8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7,8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0-1 * 7,8 *"));

		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7/8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7/8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7-8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7-8 *"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7,8"));
		assertNull(RRMScheduler.parseIntoQuartzCron("* * * 0,1 * 7,8 *"));
	}
}
