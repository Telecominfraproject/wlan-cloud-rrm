/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.common.errors.WakeupException;

import com.facebook.openwifi.rrm.ucentral.gw.models.ServiceEvent;

/**
 * Kafka runner.
 */
public class KafkaRunner implements Runnable {
	/** Interrupt hook. */
	private final AtomicBoolean closed = new AtomicBoolean(false);

	/** The Kafka consumer instance. */
	private final UCentralKafkaConsumer consumer;

	/** The Kafka producer instance. */
	private final UCentralKafkaProducer producer;

	/** Run with the given consumer/producer instances. */
	public KafkaRunner(
		UCentralKafkaConsumer consumer,
		UCentralKafkaProducer producer
	) {
		this.consumer = consumer;
		this.producer = producer;
	}

	@Override
	public void run() {
		try {
			// On startup:
			// - Consumer subscribes to all configured topics
			if (consumer != null) {
				consumer.subscribe();
			}
			// - Producer emits "join" service event
			if (producer != null) {
				producer.publishSystemEvent(ServiceEvent.EVENT_JOIN);
			}

			// Loop:
			long lastKeepAliveTs = System.nanoTime();
			final long KEEPALIVE_INTERVAL_NS =
				ServiceEvent.KEEPALIVE_INTERVAL_S * 1_000_000_000L;
			while (!closed.get()) {
				// - Consumer polls records
				if (consumer != null) {
					consumer.poll();
				}
				// - Producer emits "keep-alive" service event periodically
				if (producer != null) {
					long now = System.nanoTime();
					if (now - lastKeepAliveTs >= KEEPALIVE_INTERVAL_NS) {
						producer
							.publishSystemEvent(ServiceEvent.EVENT_KEEPALIVE);
						lastKeepAliveTs = now;
					}
				}
			}
		} catch (WakeupException e) {
			// Ignore exception if closing
			if (!closed.get()) {
				throw e;
			}
		} finally {
			// On shutdown:
			if (consumer != null) {
				consumer.close();
			}
			// - Producer emits "leave" service event
			if (producer != null) {
				producer.publishSystemEvent(ServiceEvent.EVENT_LEAVE);
				producer.close();
			}
		}
	}

	/** Shutdown hook which can be called from a separate thread. */
	public void shutdown() {
		closed.set(true);
		if (consumer != null) {
			consumer.wakeup();
		}
	}
}
