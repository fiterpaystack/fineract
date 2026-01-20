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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Component;

/**
 * Generates stable, deterministic event IDs for hook-based Kafka events.
 *
 * Format: {entityName}_{actionName}_{aggregateRootId}_{businessDate}_{hash}
 *
 * This ensures the same event generates the same ID across retries, enabling downstream duplicate detection
 * (requirement #4).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaystackHookEventIdGenerator {

    private static final int HASH_LENGTH = 8;

    /**
     * Generate a stable event ID for a hook event.
     *
     * @param entityName
     *            The entity name (e.g., "CLIENT", "SAVINGSACCOUNT")
     * @param actionName
     *            The action name (e.g., "CREATE", "ACTIVATE")
     * @param payload
     *            The event payload (JSON string)
     * @param context
     *            The Fineract context
     * @return Stable event ID
     */
    public String generate(String entityName, String actionName, String payload, FineractContext context) {
        try {
            // Extract aggregate root ID from payload (clientId or accountId)
            String aggregateRootId = extractAggregateRootId(payload);

            // Get business date
            LocalDate businessDate = DateUtils.getBusinessLocalDate();

            // Generate stable hash from event characteristics
            String eventDataHash = generateEventDataHash(entityName, actionName, aggregateRootId, businessDate, payload);

            // Build event ID
            String eventId = String.format("%s_%s_%s_%s_%s", entityName, actionName, aggregateRootId != null ? aggregateRootId : "null",
                    businessDate, eventDataHash);

            log.debug("Generated event ID: {} for entity: {}, action: {}", eventId, entityName, actionName);
            return eventId;

        } catch (Exception e) {
            log.error("Failed to generate event ID, using fallback", e);
            // Fallback to hash-based ID if generation fails (still stable)
            LocalDate businessDate = DateUtils.getBusinessLocalDate();
            String payloadHash = payload != null ? String.valueOf(payload.hashCode()) : "null";
            String input = String.format("%s_%s_%s_%s", entityName, actionName, businessDate, payloadHash);
            int hash = input.hashCode();
            String hashStr = String.valueOf(Math.abs(hash));
            return String.format("%s_%s_%s_%s", entityName, actionName, businessDate,
                    hashStr.length() > HASH_LENGTH ? hashStr.substring(0, HASH_LENGTH) : hashStr);
        }
    }

    /**
     * Extract aggregate root ID (clientId or accountId) from payload.
     */
    private String extractAggregateRootId(String payload) {
        if (payload == null || payload.isBlank()) {
            return "unknown";
        }
        try {
            JsonObject json = JsonParser.parseString(payload).getAsJsonObject();

            // Check for clientId in response
            if (json.has("response")) {
                JsonObject response = json.getAsJsonObject("response");
                if (response.has("clientId") && !response.get("clientId").isJsonNull()) {
                    return response.get("clientId").getAsString();
                }
                if (response.has("resourceId") && !response.get("resourceId").isJsonNull()) {
                    return response.get("resourceId").getAsString();
                }
            }

            // Check for clientId in request
            if (json.has("request")) {
                JsonObject request = json.getAsJsonObject("request");
                if (request.has("clientId") && !request.get("clientId").isJsonNull()) {
                    return request.get("clientId").getAsString();
                }
            }

            // Check at root level
            if (json.has("clientId") && !json.get("clientId").isJsonNull()) {
                return json.get("clientId").getAsString();
            }
            if (json.has("accountId") && !json.get("accountId").isJsonNull()) {
                return json.get("accountId").getAsString();
            }
            if (json.has("resourceId") && !json.get("resourceId").isJsonNull()) {
                return json.get("resourceId").getAsString();
            }

        } catch (Exception e) {
            log.debug("Could not extract aggregate root ID from payload", e);
        }

        return "unknown";
    }

    /**
     * Generate stable hash from event characteristics. Same event on same day generates same hash.
     */
    private String generateEventDataHash(String entityName, String actionName, String aggregateRootId, LocalDate businessDate,
            String payload) {
        try {
            // Create hash input from event characteristics
            String payloadPart = (payload != null && !payload.isBlank()) ? (payload.length() > 100 ? payload.substring(0, 100) : payload)
                    : "null";
            String input = String.format("%s_%s_%s_%s_%s", entityName, actionName, aggregateRootId != null ? aggregateRootId : "null",
                    businessDate, payloadPart);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, HASH_LENGTH);

        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not available", e);
            // Fallback to simple hash
            String input = String.format("%s_%s_%s_%s", entityName, actionName, aggregateRootId, businessDate);
            int hash = input.hashCode();
            String hashStr = String.valueOf(Math.abs(hash));
            return hashStr.length() > HASH_LENGTH ? hashStr.substring(0, HASH_LENGTH) : hashStr;
        }
    }
}
