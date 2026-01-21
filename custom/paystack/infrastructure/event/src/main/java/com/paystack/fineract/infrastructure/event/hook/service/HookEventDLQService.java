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
package com.paystack.fineract.infrastructure.event.hook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.infrastructure.config.PaystackEventProperties;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for sending failed events to Dead Letter Queue (DLQ).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HookEventDLQService {

    private final KafkaTemplate<String, String> paystackExternalEventsKafkaTemplate;
    private final PaystackEventProperties eventProperties;
    private final PaystackKafkaEventMetrics metrics;
    private final HookEventRecordRepository eventRecordRepository;
    private final ObjectMapper objectMapper;

    /**
     * Send event to DLQ topic.
     */
    public void sendToDLQ(HookEventRecord eventRecord) {
        try {
            // Create DLQ message with all required fields
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", parseJson(eventRecord.getPayload()));
            dlqMessage.put("eventId", eventRecord.getEventId());
            dlqMessage.put("failureReason", eventRecord.getErrorMessage());
            dlqMessage.put("retryCount", eventRecord.getRetryCount());
            dlqMessage.put("maxRetries", eventRecord.getMaxRetries());
            // Timestamps
            dlqMessage.put("createdAt", eventRecord.getCreatedAt() != null ? eventRecord.getCreatedAt().toString()
                    : DateUtils.getAuditLocalDateTime().toString());
            dlqMessage.put("failedAt", eventRecord.getFailedAt() != null ? eventRecord.getFailedAt().toString()
                    : DateUtils.getAuditLocalDateTime().toString());
            dlqMessage.put("lastRetryAt", eventRecord.getLastRetryAt() != null ? eventRecord.getLastRetryAt().toString() : null);
            // Additional context
            dlqMessage.put("hookId", eventRecord.getHookId());
            dlqMessage.put("entityName", eventRecord.getEntityName());
            dlqMessage.put("actionName", eventRecord.getActionName());
            dlqMessage.put("tenantIdentifier", eventRecord.getTenantIdentifier());
            dlqMessage.put("topicName", eventRecord.getTopicName());
            dlqMessage.put("partitionKey", eventRecord.getPartitionKey());

            String dlqTopic = eventProperties.getKafka().getHook().getDlqTopic();
            String dlqPayload = objectMapper.writeValueAsString(dlqMessage);

            // Send to DLQ topic (synchronous send)
            paystackExternalEventsKafkaTemplate.send(dlqTopic, eventRecord.getEventId(), dlqPayload).get();

            // Update status and save (only if send succeeds)
            eventRecord.setStatus(HookEventStatus.DLQ);
            eventRecordRepository.save(eventRecord);

            log.warn("Event sent to DLQ: eventId={}, topic={}, entity={}, action={}", eventRecord.getEventId(), dlqTopic,
                    eventRecord.getEntityName(), eventRecord.getActionName());

            metrics.recordDLQEvent(eventRecord.getEntityName(), eventRecord.getActionName(), eventRecord.getErrorMessage());

        } catch (Exception e) {
            log.error("Failed to send event to DLQ: eventId={}", eventRecord.getEventId(), e);
            // Event remains in FAILED status if DLQ send fails
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse JSON payload for DLQ", e);
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }
}
