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
package com.paystack.fineract.infrastructure.event.external.service.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.business.domain.BusinessEvent;
import org.apache.fineract.infrastructure.event.external.service.idempotency.ExternalEventIdempotencyKeyGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Stable idempotency key generator for external events.
 * 
 * Generates deterministic keys that remain unchanged across retries:
 * Format: {eventType}_{aggregateRootId}_{businessDate}_{hash}
 * 
 * This enables downstream consumers to detect duplicates (requirement #4).
 */
@Component
@Primary
@Slf4j
@ConditionalOnProperty(value = "paystack.events.kafka.stableIdempotencyEnabled", havingValue = "true", matchIfMissing = true)
public class PaystackStableExternalEventIdempotencyKeyGenerator implements ExternalEventIdempotencyKeyGenerator {

    private static final int HASH_LENGTH = 8;

    @Override
    public <T> String generate(BusinessEvent<T> event) {
        try {
            String eventType = event.getType();
            Long aggregateRootId = event.getAggregateRootId();
            LocalDate businessDate = DateUtils.getBusinessLocalDate();
            
            // Generate stable hash from event characteristics
            String eventDataHash = generateEventDataHash(event, eventType, aggregateRootId, businessDate);
            
            String key = String.format("%s_%s_%s_%s",
                eventType,
                aggregateRootId != null ? aggregateRootId.toString() : "null",
                businessDate,
                eventDataHash);
            
            log.debug("Generated idempotency key: {} for event type: {}", key, eventType);
            return key;
            
        } catch (Exception e) {
            log.error("Failed to generate stable idempotency key, using fallback", e);
            // Fallback to hash-based key if generation fails (still stable)
            LocalDate businessDate = DateUtils.getBusinessLocalDate();
            String input = String.format("%s_%s_%s",
                event.getType(),
                event.getAggregateRootId() != null ? event.getAggregateRootId() : "null",
                businessDate);
            int hash = input.hashCode();
            String hashStr = String.valueOf(Math.abs(hash));
            return String.format("%s_%s_%s_%s",
                event.getType(),
                event.getAggregateRootId() != null ? event.getAggregateRootId() : "null",
                businessDate,
                hashStr.length() > HASH_LENGTH ? hashStr.substring(0, HASH_LENGTH) : hashStr);
        }
    }

    private <T> String generateEventDataHash(BusinessEvent<T> event, String eventType, Long aggregateRootId, LocalDate businessDate) {
        try {
            // Create hash input from event characteristics
            // This ensures same event on same day gets same key
            String input = String.format("%s_%s_%s",
                eventType,
                aggregateRootId != null ? aggregateRootId.toString() : "null",
                businessDate);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, HASH_LENGTH);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash
            String input = String.format("%s_%s_%s",
                eventType, aggregateRootId != null ? aggregateRootId.toString() : "null", businessDate);
            int hash = input.hashCode();
            String hashStr = String.valueOf(Math.abs(hash));
            return hashStr.length() > HASH_LENGTH ? hashStr.substring(0, HASH_LENGTH) : hashStr;
        }
    }
}
