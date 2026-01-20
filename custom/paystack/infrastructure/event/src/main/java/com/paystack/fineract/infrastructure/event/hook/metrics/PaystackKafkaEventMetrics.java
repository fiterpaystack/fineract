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
package com.paystack.fineract.infrastructure.event.hook.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Metrics service for Kafka hook events.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaystackKafkaEventMetrics {

    private static final String TAG_ENTITY_NAME = "entity_name";
    private static final String TAG_ACTION_NAME = "action_name";
    private static final String TAG_STATUS = "status";
    private static final String TAG_FAILURE_REASON = "failure_reason";

    private final MeterRegistry meterRegistry;

    /**
     * Record event production (success or failure).
     */
    public void recordEventProduced(String entityName, String actionName, boolean success, Duration duration) {
        Counter.builder("fineract.kafka.hook.events.produced.total").tag(TAG_ENTITY_NAME, entityName).tag(TAG_ACTION_NAME, actionName)
                .tag(TAG_STATUS, success ? "success" : "failure").register(meterRegistry).increment();

        if (success && duration != null) {
            Timer.builder("fineract.kafka.hook.events.produced.duration").tag(TAG_ENTITY_NAME, entityName).tag(TAG_ACTION_NAME, actionName)
                    .register(meterRegistry).record(duration);
        }
    }

    /**
     * Record event retry (success or failure).
     */
    public void recordEventRetry(String entityName, String actionName, boolean success) {
        Counter.builder("fineract.kafka.hook.events.retry.total").tag(TAG_ENTITY_NAME, entityName).tag(TAG_ACTION_NAME, actionName)
                .tag(TAG_STATUS, success ? "success" : "failure").register(meterRegistry).increment();
    }

    /**
     * Record DLQ event.
     */
    public void recordDLQEvent(String entityName, String actionName, String failureReason) {
        Counter.builder("fineract.kafka.hook.events.dlq.total").tag(TAG_ENTITY_NAME, entityName).tag(TAG_ACTION_NAME, actionName)
                .tag(TAG_FAILURE_REASON, failureReason != null ? failureReason : "unknown").register(meterRegistry).increment();

        log.warn("Event sent to DLQ: entity={}, action={}, reason={}", entityName, actionName, failureReason);
    }
}
