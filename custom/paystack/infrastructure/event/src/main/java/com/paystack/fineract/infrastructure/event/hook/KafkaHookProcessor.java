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
package com.paystack.fineract.infrastructure.event.hook;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.paystack.fineract.infrastructure.config.PaystackEventProperties;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import com.paystack.fineract.infrastructure.event.hook.service.HookEventRetryService;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.hooks.domain.Hook;
import org.apache.fineract.infrastructure.hooks.domain.HookConfiguration;
import org.apache.fineract.infrastructure.hooks.processor.HookProcessor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Kafka hook processor that publishes hook events to Kafka topics.
 *
 * Configuration fields: - "Topic Name" (required): Kafka topic to publish to - "Partition Key Strategy" (optional): How
 * to generate partition key (aggregateRootId, entityId, roundRobin)
 */
@Service("kafkaHookProcessor")
@RequiredArgsConstructor
@Slf4j
public class KafkaHookProcessor implements HookProcessor {

    private static final String TOPIC_NAME_FIELD = "Topic Name";
    private static final String PARTITION_KEY_STRATEGY_FIELD = "Partition Key Strategy";
    private static final String DEFAULT_PARTITION_STRATEGY = "aggregateRootId";

    private final KafkaTemplate<String, String> paystackExternalEventsKafkaTemplate;
    private final PaystackHookEventIdGenerator eventIdGenerator;
    private final HookEventRecordRepository eventRecordRepository;
    private final HookEventRetryService retryService;
    private final PaystackKafkaEventMetrics metrics;
    private final PaystackEventProperties eventProperties;

    @Override
    public void process(final Hook hook, final String payload, final String entityName, final String actionName,
            final FineractContext context) throws Exception {

        String eventId = null;
        String topicName = null;
        String partitionKey = null;
        String enrichedPayload = null;

        try {
            // Extract configuration from hook
            topicName = extractConfigValue(hook, TOPIC_NAME_FIELD);
            if (topicName == null || topicName.isBlank()) {
                throw new IllegalArgumentException("Topic Name is required for Kafka hooks");
            }

            String partitionKeyStrategy = extractConfigValue(hook, PARTITION_KEY_STRATEGY_FIELD);
            if (partitionKeyStrategy == null || partitionKeyStrategy.isBlank()) {
                partitionKeyStrategy = DEFAULT_PARTITION_STRATEGY;
            }

            // Generate stable event ID (requirement #4)
            eventId = eventIdGenerator.generate(entityName, actionName, payload, context);

            // Generate partition key based on strategy
            partitionKey = generatePartitionKey(payload, partitionKeyStrategy, entityName);

            // Enrich payload with metadata
            enrichedPayload = enrichPayloadWithMetadata(payload, eventId, entityName, actionName, context);

            // Measure Kafka send operation duration only (from send to get)
            long startTime = System.nanoTime();
            // Publish to Kafka (with producer-level retry already configured)
            CompletableFuture<SendResult<String, String>> future = paystackExternalEventsKafkaTemplate.send(topicName, partitionKey,
                    enrichedPayload);

            // Wait for result with configurable timeout
            long timeoutSeconds = eventProperties.getKafka().getHook().getKafkaPublishTimeoutSeconds();
            SendResult<String, String> result = future.get(timeoutSeconds, TimeUnit.SECONDS);

            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
            log.debug(
                    "Successfully published Kafka hook event: eventId={}, topic={}, partitionKey={}, entity={}, action={}. "
                            + "Note: No DB record created for successful publishes (DB only tracks failed events for retry).",
                    eventId, topicName, partitionKey, entityName, actionName);

            metrics.recordEventProduced(entityName, actionName, true, duration);

        } catch (Exception e) {
            // For error case, we can't measure Kafka send duration since it failed before or during send
            // Use a minimal duration (0 or 1) to indicate failure occurred before/during send
            Duration duration = Duration.ZERO;
            log.warn("Failed to publish Kafka hook event synchronously: entity={}, action={}, hookId={}, eventId={}", entityName,
                    actionName, hook.getId(), eventId, e);

            // Persist event for retry (with duplicate detection)
            // IMPORTANT: Events are ONLY saved to DB when Kafka publish fails.
            // Successful publishes do NOT create DB records (DB is only for retry/DLQ tracking).
            // The eventId has a unique constraint to prevent duplicate records.
            if (eventId != null && topicName != null && enrichedPayload != null) {
                try {
                    // Check if event already exists (duplicate detection)
                    HookEventRecord existingRecord = eventRecordRepository.findByEventId(eventId).orElse(null);

                    if (existingRecord != null) {
                        log.info(
                                "Event record already exists in DB for eventId={}, status={}. "
                                        + "Skipping duplicate save. This can happen if the same event failed multiple times. "
                                        + "Event will be automatically retried by scheduled job when eligible.",
                                eventId, existingRecord.getStatus());
                        // Event remains in PENDING/FAILED status and will be picked up by scheduled job
                        // when the retry interval has elapsed (no immediate retry scheduling)
                    } else {
                        // Create new event record for retry
                        log.info("Saving event record to DB for retry: eventId={}, entity={}, action={}", eventId, entityName, actionName);
                        HookEventRecord eventRecord = createEventRecord(eventId, hook, topicName, partitionKey, enrichedPayload, entityName,
                                actionName, context, e);

                        try {
                            eventRecordRepository.save(eventRecord);
                            log.info("Event record saved successfully: eventId={}, status={}. "
                                    + "Event will be automatically retried by scheduled job when eligible (based on retry interval).",
                                    eventId, eventRecord.getStatus());
                            // No immediate retry scheduling - event will be picked up by scheduled job
                            // when the configured retry interval has elapsed
                        } catch (DataIntegrityViolationException dive) {
                            // Handle race condition: another thread might have saved the same eventId concurrently
                            final String finalEventId = eventId; // Make effectively final for lambda
                            log.warn("Duplicate eventId detected during save (race condition): eventId={}. "
                                    + "Another thread may have saved this event. Event will be automatically retried by scheduled job when eligible.",
                                    finalEventId);
                            // Event will be picked up by scheduled job automatically (no need to schedule retry)
                        }
                    }
                } catch (Exception saveException) {
                    log.error("Failed to persist event record for retry: eventId={}, entity={}, action={}. "
                            + "Event will not be retried automatically.", eventId, entityName, actionName, saveException);
                    // Don't throw - we don't want to fail the command
                }
            }

            metrics.recordEventProduced(entityName, actionName, false, duration);

            // Don't throw exception - hook processing should not fail the command
            // The event will be retried asynchronously
        }
    }

    /**
     * Create event record for persistence and retry.
     */
    private HookEventRecord createEventRecord(String eventId, Hook hook, String topicName, String partitionKey, String payload,
            String entityName, String actionName, FineractContext context, Exception error) {
        HookEventRecord eventRecord = new HookEventRecord();
        eventRecord.setEventId(eventId);
        eventRecord.setHookId(hook.getId());
        eventRecord.setEntityName(entityName);
        eventRecord.setActionName(actionName);
        eventRecord.setTopicName(topicName);
        eventRecord.setPartitionKey(partitionKey != null ? partitionKey : "default");
        eventRecord.setPayload(payload);
        eventRecord.setTenantIdentifier(context.getTenantContext().getTenantIdentifier());
        eventRecord.setStatus(HookEventStatus.PENDING);
        eventRecord.setRetryCount(0);
        eventRecord.setMaxRetries(eventProperties.getKafka().getHook().getMaxRetries());
        eventRecord.setErrorMessage(error != null ? error.getMessage() : "Unknown error");
        eventRecord.setCreatedAt(DateUtils.getAuditLocalDateTime());
        return eventRecord;
    }

    /**
     * Extract configuration value from hook.
     */
    private String extractConfigValue(Hook hook, String fieldName) {
        Set<HookConfiguration> config = hook.getConfig();
        return config.stream().filter(conf -> conf.getFieldName().equals(fieldName)).findFirst().map(HookConfiguration::getFieldValue)
                .orElse(null);
    }

    /**
     * Generate partition key based on strategy.
     */
    private String generatePartitionKey(String payload, String strategy, String entityName) {
        try {
            if ("aggregateRootId".equalsIgnoreCase(strategy)) {
                return extractAggregateRootId(payload);
            } else if ("entityId".equalsIgnoreCase(strategy)) {
                return extractEntityId(payload);
            } else {
                // Round-robin or hash-based
                return String.valueOf(payload.hashCode());
            }
        } catch (Exception e) {
            log.warn("Failed to generate partition key using strategy: {}, using fallback", strategy, e);
            return String.valueOf(payload.hashCode());
        }
    }

    /**
     * Extract aggregate root ID for Kafka partition key.
     *
     * Priority for partition key: 1. resourceId (transaction ID) - unique per transaction (e.g., DEPOSIT, WITHDRAWAL)
     * 2. savingsId/accountId - unique per account 3. clientId - unique per client (fallback for non-transaction events)
     *
     * Note: Using resourceId ensures each transaction gets a unique partition key, which is important for
     * transaction-level event uniqueness and idempotency.
     */
    private String extractAggregateRootId(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            // Priority 1: Check for resourceId in response (transaction ID - unique per transaction)
            if (json.has("response")) {
                JsonObject response = json.getAsJsonObject("response");
                // resourceId is the transaction ID (unique per deposit/withdrawal)
                if (response.has("resourceId") && !response.get("resourceId").isJsonNull()) {
                    return response.get("resourceId").getAsString();
                }
                // savingsId is the account ID (unique per account)
                if (response.has("savingsId") && !response.get("savingsId").isJsonNull()) {
                    return response.get("savingsId").getAsString();
                }
                // clientId as fallback
                if (response.has("clientId") && !response.get("clientId").isJsonNull()) {
                    return response.get("clientId").getAsString();
                }
            }

            // Priority 2: Check at root level
            if (json.has("resourceId") && !json.get("resourceId").isJsonNull()) {
                return json.get("resourceId").getAsString();
            }
            if (json.has("savingsId") && !json.get("savingsId").isJsonNull()) {
                return json.get("savingsId").getAsString();
            }
            if (json.has("accountId") && !json.get("accountId").isJsonNull()) {
                return json.get("accountId").getAsString();
            }

            // Priority 3: Check for clientId in request
            if (json.has("request")) {
                JsonObject request = json.getAsJsonObject("request");
                if (request.has("clientId") && !request.get("clientId").isJsonNull()) {
                    return request.get("clientId").getAsString();
                }
            }

            // Priority 4: Check clientId at root level (fallback)
            if (json.has("clientId") && !json.get("clientId").isJsonNull()) {
                return json.get("clientId").getAsString();
            }

        } catch (Exception e) {
            log.debug("Could not extract aggregate root ID from payload", e);
        }

        return "default";
    }

    /**
     * Extract entity ID (resourceId) from payload.
     */
    private String extractEntityId(String payload) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            if (json.has("response")) {
                JsonObject response = json.getAsJsonObject("response");
                if (response.has("resourceId") && !response.get("resourceId").isJsonNull()) {
                    return response.get("resourceId").getAsString();
                }
            }

            if (json.has("resourceId") && !json.get("resourceId").isJsonNull()) {
                return json.get("resourceId").getAsString();
            }

        } catch (Exception e) {
            log.debug("Could not extract entity ID from payload", e);
        }

        return "default";
    }

    /**
     * Enrich payload with metadata (eventId, timestamp, tenant, eventType).
     */
    private String enrichPayloadWithMetadata(String payload, String eventId, String entityName, String actionName,
            FineractContext context) {
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            // Add metadata
            json.addProperty("eventId", eventId);
            json.addProperty("eventType", entityName + "_" + actionName);
            json.addProperty("timestamp", DateUtils.getAuditLocalDateTime().toString());
            json.addProperty("tenantId", context.getTenantContext().getTenantIdentifier());

            return json.toString();
        } catch (Exception e) {
            log.warn("Failed to enrich payload with metadata, using original payload", e);
            return payload;
        }
    }
}
