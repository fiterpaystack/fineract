/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.paystack.fineract.infrastructure.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.core.config.FineractProperties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
@ConditionalOnProperty(value = "fineract.events.external.producer.kafka.enabled", havingValue = "true")
public class PaystackExternalEventKafkaConfiguration {

    @Autowired
    private FineractProperties fineractProperties;

    @Bean
    public ProducerFactory<String, String> paystackExternalEventsProducerFactory() {
        FineractProperties.FineractExternalEventsProducerKafkaProperties kafkaProp = fineractProperties.getEvents().getExternal()
                .getProducer().getKafka();
        Map<String, Object> props = new HashMap<>(kafkaProp.getProducer().getExtraPropertiesMap());

        // Basic configuration
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProp.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // Enhanced producer configuration for real-time notifications
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all"); // Wait for all replicas to acknowledge
        props.putIfAbsent(ProducerConfig.RETRIES_CONFIG, 3); // Retry failed sends
        props.putIfAbsent(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100); // Backoff between retries
        props.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // Request timeout
        props.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // Total delivery timeout

        // Performance tuning for real-time notifications
        props.putIfAbsent(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // Batch size for better throughput
        props.putIfAbsent(ProducerConfig.LINGER_MS_CONFIG, 5); // Wait up to 5ms to batch messages
        props.putIfAbsent(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer

        // Compression for better network utilization
        props.putIfAbsent(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        // Idempotence for exactly-once semantics
        props.putIfAbsent(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // Note: TRANSACTIONAL_ID_CONFIG is NOT set because:
        // 1. Hooks are invoked from event listeners without transaction context
        // 2. Idempotence (ENABLE_IDEMPOTENCE_CONFIG) provides exactly-once semantics
        // 3. We have our own retry mechanism and DLQ for reliability
        // 4. Kafka transactions are not needed for this use case

        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> paystackExternalEventsKafkaTemplate(
            ProducerFactory<String, String> paystackExternalEventsProducerFactory) {
        KafkaTemplate<String, String> template = new KafkaTemplate<>(paystackExternalEventsProducerFactory);

        // Set default topic for convenience
        FineractProperties.FineractExternalEventsProducerKafkaProperties kafkaProp = fineractProperties.getEvents().getExternal()
                .getProducer().getKafka();
        template.setDefaultTopic(kafkaProp.getTopic().getName());

        return template;
    }
}
