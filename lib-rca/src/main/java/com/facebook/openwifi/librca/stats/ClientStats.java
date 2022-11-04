/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.librca.stats;

import java.util.List;

/** Aggregated statistics for each client. Maily handle KPI and metric calculations */
public class ClientStats {
	/** client MAC */
	String station;

	/** LinkStats that are of the same station(client). */
	List<LinkStats> connections;
}
