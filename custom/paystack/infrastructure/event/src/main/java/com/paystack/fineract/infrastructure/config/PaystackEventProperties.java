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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Paystack event processing.
 */
@ConfigurationProperties(prefix = "paystack.events")
@Data
public class PaystackEventProperties {

    /**
     * External producer configuration.
     */
    private ExternalProducerProperties external = new ExternalProducerProperties();

    /**
     * Kafka hook configuration.
     */
    private KafkaProperties kafka = new KafkaProperties();

    @Data
    public static class ExternalProducerProperties {
        /**
         * Producer configuration.
         */
        private ProducerProperties producer = new ProducerProperties();
    }

    @Data
    public static class ProducerProperties {
        /**
         * Kafka async processing configuration.
         */
        private AsyncProperties kafka = new AsyncProperties();
    }

    @Data
    public static class KafkaProperties {
        /**
         * Enable stable idempotency key generation (deterministic keys).
         * This ensures idempotency keys remain unchanged across retries.
         */
        private boolean stableIdempotencyEnabled = true;

        /**
         * Kafka hook configuration.
         */
        private HookProperties hook = new HookProperties();
    }

    @Data
    public static class AsyncProperties {
        /**
         * Core pool size for async Kafka notification processing thread pool.
         */
        private int corePoolSize = 5;

        /**
         * Maximum pool size for async Kafka notification processing thread pool.
         */
        private int maxPoolSize = 20;

        /**
         * Queue capacity for pending async Kafka notification tasks.
         */
        private int queueCapacity = 100;

        /**
         * Keep-alive time in seconds for idle threads in the async pool.
         */
        private int keepAliveSeconds = 60;

        /**
         * Thread name prefix for async Kafka notification processing threads.
         */
        private String threadNamePrefix = "paystack-kafka-notification-";

        /**
         * Maximum number of retries for failed async Kafka notifications.
         */
        private int maxRetries = 3;
    }

    @Data
    public static class HookProperties {
        /**
         * Maximum number of retries for failed hook events.
         */
        private Integer maxRetries = 3;

        /**
         * Initial retry delay in milliseconds (exponential backoff starts here).
         */
        private Long retryInitialDelayMs = 1000L;

        /**
         * Maximum retry delay in milliseconds (caps exponential backoff).
         */
        private Long retryMaxDelayMs = 30000L;

        /**
         * Interval in milliseconds for scheduled retry job.
         */
        private Long retryIntervalMs = 60000L;

        /**
         * Dead Letter Queue topic name for failed events.
         */
        private String dlqTopic = "fineract-client-account-events-dlq";
    }
}
