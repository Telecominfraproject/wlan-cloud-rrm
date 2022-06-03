/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifirrm.ucentral;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.facebook.openwifirrm.ucentral.gw.models.ServiceEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Kafka consumer for uCentral.
 */
public class UCentralKafkaConsumer {
	private static final Logger logger = LoggerFactory.getLogger(UCentralKafkaConsumer.class);

	/** Poll timeout duration. */
	private static final Duration POLL_TIMEOUT = Duration.ofMillis(10000);

	/** The consumer instance. */
	private final KafkaConsumer<String, String> consumer;

	/** The uCentral state topic. */
	private final String stateTopic;

	/** The uCentral wifi scan results topic. */
	private final String wifiScanTopic;

	/** The uCentral system endpoints topic. */
	private final String serviceEventsTopic;

	/** The Gson instance. */
	private final Gson gson = new Gson();

	private final UCentralClient client;

	/** Representation of Kafka record. */
	public static class KafkaRecord {
		/** The device serial number. */
		public final String serialNumber;

		/** The state payload JSON. */
		public final JsonObject payload;

		/** Constructor. */
		public KafkaRecord(String serialNumber, JsonObject payload) {
			this.serialNumber = serialNumber;
			this.payload = payload;
		}
	}

	/** Kafka record listener interface. */
	public interface KafkaListener {
		/** Handle a list of state records. */
		void handleStateRecords(List<KafkaRecord> records);

		/** Handle a list of wifi scan records. */
		void handleWifiScanRecords(List<KafkaRecord> records);

		void handleServiceEventRecords(List<ServiceEvent> serviceEventRecords);
	}

	/** Kafka record listeners. */
	private Map<String, KafkaListener> kafkaListeners = new TreeMap<>();

	/**
	 * Constructor.
	 * @param bootstrapServer the Kafka bootstrap server
	 * @param groupId the Kafka consumer group ID
	 * @param autoOffsetReset the "auto.offset.reset" config
	 * @param stateTopic the uCentral state topic (or empty/null to skip)
	 * @param wifiScanTopic the uCentral wifiscan topic (or empty/null to skip)
	 */
	public UCentralKafkaConsumer(
		UCentralClient client,
		String bootstrapServer,
		String groupId,
		String autoOffsetReset,
		String stateTopic,
		String wifiScanTopic,
		String serviceEventsTopic
	) {
		this.client = client;
		this.stateTopic = stateTopic;
		this.wifiScanTopic = wifiScanTopic;
		this.serviceEventsTopic = serviceEventsTopic;

		// Set properties
		Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServer);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
		props.put(
			ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
			StringDeserializer.class.getName()
		);
		props.put(
			ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
			StringDeserializer.class.getName()
		);
		props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1000");

		// Create consumer instance
		this.consumer = new KafkaConsumer<>(props);
		this.subscribeApiKeyListener();
		logger.info("Using Kafka bootstrap server: {}", bootstrapServer);
	}

	/** Subscribe to topic(s). */
	public void subscribe() {
		Map<String, List<PartitionInfo>> topics =
			consumer.listTopics(POLL_TIMEOUT);
		logger.info("Found topics: {}", String.join(", ", topics.keySet()));
		List<String> subscribeTopics = Arrays.asList(stateTopic, wifiScanTopic, serviceEventsTopic)
			.stream()
			.filter(t -> t != null && !t.isEmpty())
			.collect(Collectors.toList());
		for (String topic : subscribeTopics) {
			if (!topics.containsKey(topic)) {
				throw new RuntimeException("Topic not found: " + topic);
			}
		}
		consumer.subscribe(subscribeTopics, new ConsumerRebalanceListener() {
			@Override
			public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
				// ignore
			}

			@Override
			public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
				logger.info(
					"Received {} partition assignment(s): {}",
					partitions.size(),
					partitions.stream()
						.map(
							p ->
							String.format("%s=%d", p.topic(), p.partition())
						)
						.collect(Collectors.joining(", "))
				);
				if (partitions.size() != subscribeTopics.size()) {
					// As of Kafka v2.8.0, we see multi-topic subscribe() calls
					// often failing to assign partitions to some topics.
					//
					// Keep trying to unsubscribe/resubscribe until it works...
					// TODO a better solution?
					logger.error(
						"Missing topics in partition assignment! " +
						"Resubscribing..."
					);
					consumer.unsubscribe();
					subscribe();
				}
			}
		});
	}

	/** Poll for data. */
	public void poll() {
		ConsumerRecords<String, String> records = consumer.poll(POLL_TIMEOUT);
		logger.debug("Poll returned with {} record(s)", records.count());

		List<KafkaRecord> stateRecords = new ArrayList<>();
		List<KafkaRecord> wifiScanRecords = new ArrayList<>();
		List<ServiceEvent> serviceEventRecords = new ArrayList<>();
		for (ConsumerRecord<String, String> record : records) {
			if(record.topic().equals(serviceEventsTopic)){
				ServiceEvent event = gson.fromJson(record.value(), ServiceEvent.class);
				serviceEventRecords.add(event);
			}else{
				// Parse payload JSON
				JsonObject payload = null;
				try {
					JsonObject o =
							gson.fromJson(record.value(), JsonObject.class);
					payload = o.getAsJsonObject("payload");
				} catch (Exception e) {
					// uCentralGw pushes invalid JSON for empty messages
					logger.trace(
							"Offset {}: Invalid payload JSON", record.offset()
					);
					continue;
				}
				if (payload == null) {
					logger.trace("Offset {}: No payload", record.offset());
					continue;
				}
				if (!payload.isJsonObject()) {
					logger.trace(
							"Offset {}: Payload not an object", record.offset()
					);
					continue;
				}

				// Process records by topic
				String serialNumber = record.key();
				logger.trace(
						"Offset {}: {} => {}",
						record.offset(), serialNumber, payload.toString()
				);

				if (record.topic().equals(stateTopic)) {
					stateRecords.add(new KafkaRecord(serialNumber, payload));
				} else if (record.topic().equals(wifiScanTopic)) {
					wifiScanRecords.add(new KafkaRecord(serialNumber, payload));
				}
			}
		}

		// Call listeners
		if (!stateRecords.isEmpty()) {
			for (KafkaListener listener : kafkaListeners.values()) {
				listener.handleStateRecords(stateRecords);
			}
		}
		if (!wifiScanRecords.isEmpty()) {
			for (KafkaListener listener : kafkaListeners.values()) {
				listener.handleWifiScanRecords(wifiScanRecords);
			}
		}

		if (!serviceEventRecords.isEmpty()) {
			for (KafkaListener listener : kafkaListeners.values()) {
				listener.handleServiceEventRecords(serviceEventRecords);
			}
		}

		// Commit offset
		consumer.commitAsync();
	}

	/**
	 * Add/overwrite a Kafka listener with an arbitrary identifier.
	 *
	 * The "id" string determines the order in which listeners are called.
	 */
	public void addKafkaListener(String id, KafkaListener listener) {
		logger.debug("Adding Kafka listener: {}", id);
		kafkaListeners.put(id, listener);
	}

	/**
	 * Remove a Kafka listener with the given identifier, returning true if
	 * anything was actually removed.
	 */
	public boolean removeKafkaListener(String id) {
		logger.debug("Removing Kafka listener: {}", id);
		return (kafkaListeners.remove(id) != null);
	}

	/** Wakeup the consumer. */
	public void wakeup() {
		consumer.wakeup();
	}

	/** Close the consumer. */
	public void close() {
		consumer.close();
	}

	private void subscribeApiKeyListener() {

		this.addKafkaListener("APIKey", new UCentralKafkaConsumer.KafkaListener() {
			@Override
			public void handleStateRecords(List<UCentralKafkaConsumer.KafkaRecord> records) {
				//ignored
			}

			@Override
			public void handleWifiScanRecords(List<UCentralKafkaConsumer.KafkaRecord> records) {
				//ignored
			}

			@Override
			public void handleServiceEventRecords(List<ServiceEvent> serviceEventRecords) {
				for(ServiceEvent record : serviceEventRecords){
					if(record.event.equals("keep-alive")){
						client.setServiceEndpoint(record.type, record);
					}
				}
			}
		});
	}
}
