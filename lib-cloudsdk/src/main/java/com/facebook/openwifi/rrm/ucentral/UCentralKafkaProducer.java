/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm.ucentral;

import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.facebook.openwifi.rrm.ucentral.gw.models.ServiceEvent;
import com.google.gson.Gson;

/**
 * Kafka producer for uCentral.
 */
public class UCentralKafkaProducer {
	private static final Logger logger =
		LoggerFactory.getLogger(UCentralKafkaProducer.class);

	/** The producer instance. */
	private final Producer<String, String> producer;

	/** The uCentral system endpoints topic. */
	private final String serviceEventsTopic;

	/** The service name. */
	private final String serviceType;
	/** The service version. */
	private final String serviceVersion;
	/** The service ID. */
	private final long serviceId;
	/** The service key. */
	private final String serviceKey;
	/** The private service endpoint. */
	private final String privateEndpoint;
	/** The public service endpoint. */
	private final String publicEndpoint;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	/**
	 * Constructor.
	 * @param bootstrapServer the Kafka bootstrap server
	 * @param serviceEventsTopic the uCentral service_events topic (required)
	 * @param serviceType the service name
	 * @param serviceVersion the service version
	 * @param serviceId the service ID
	 * @param serviceKey the service key
	 * @param privateEndpoint the private service endpoint
	 * @param publicEndpoint the public service endpoint
	 */
	public UCentralKafkaProducer(
		String bootstrapServer,
		String serviceEventsTopic,
		String serviceType,
		String serviceVersion,
		long serviceId,
		String serviceKey,
		String privateEndpoint,
		String publicEndpoint
	) {
		this.serviceEventsTopic = serviceEventsTopic;
		this.serviceType = serviceType;
		this.serviceVersion = serviceVersion;
		this.serviceId = serviceId;
		this.serviceKey = serviceKey;
		this.privateEndpoint = privateEndpoint;
		this.publicEndpoint = publicEndpoint;

		// Set properties
		Properties props = new Properties();
		props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
		props.put(
			ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
			StringSerializer.class.getName()
		);
		props.put(
			ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
			StringSerializer.class.getName()
		);

		// Create producer instance
		this.producer = new KafkaProducer<>(props);
		logger.info("Using Kafka bootstrap server: {}", bootstrapServer);
	}

	/** Publish a service event. */
	public void publishSystemEvent(String event) {
		ServiceEvent serviceEvent = new ServiceEvent();
		serviceEvent.event = event;
		serviceEvent.type = serviceType;
		serviceEvent.version = serviceVersion;
		serviceEvent.id = serviceId;
		serviceEvent.key = serviceKey;
		serviceEvent.privateEndPoint = privateEndpoint;
		serviceEvent.publicEndPoint = publicEndpoint;

		logger.info(
			"Publishing system event ('{}') to Kafka '{}'",
			event,
			serviceEventsTopic
		);
		producer.send(
			new ProducerRecord<String, String>(
				serviceEventsTopic,
				serviceEvent.privateEndPoint,
				gson.toJson(serviceEvent)
			)
		);
		producer.flush();
	}

	/** Close the producer. */
	public void close() {
		producer.close();
	}
}
