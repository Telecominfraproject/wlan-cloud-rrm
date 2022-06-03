/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.errors.WakeupException;

/**
 * Kafka consumer runner.
 */
public class KafkaConsumerRunner implements Runnable {
	/** Interrupt hook. */
	private final AtomicBoolean closed = new AtomicBoolean(false);

	/** The Kafka consumer instance. */
	private final UCentralKafkaConsumer consumer;

	/** Run with the given consumer instance. */
	public KafkaConsumerRunner(UCentralKafkaConsumer consumer) {
		this.consumer = consumer;
	}

	@Override
	public void run() {
		try {
			consumer.subscribe();
			while (!closed.get()) {
				consumer.poll();
			}
		} catch (WakeupException e) {
			// Ignore exception if closing
			if (!closed.get()) {
				throw e;
			}
		} finally {
			consumer.close();
		}
	}

	/** Shutdown hook which can be called from a separate thread. */
	public void shutdown() {
		closed.set(true);
		consumer.wakeup();
	}
}
