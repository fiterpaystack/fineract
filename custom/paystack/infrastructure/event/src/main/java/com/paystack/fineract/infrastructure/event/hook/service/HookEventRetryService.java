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

import com.paystack.fineract.infrastructure.config.PaystackEventProperties;
import com.paystack.fineract.infrastructure.config.RetryIntervalUnit;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttemptRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for automatic retry of failed hook events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HookEventRetryService {

    private final HookEventRecordRepository eventRecordRepository;
    private final HookEventRetryAttemptRepository retryAttemptRepository;
    private final KafkaTemplate<String, String> paystackExternalEventsKafkaTemplate;
    private final HookEventDLQService dlqService;
    private final PaystackKafkaEventMetrics metrics;
    private final PaystackEventProperties eventProperties;
    private final TenantDetailsService tenantDetailsService;

    /**
     * Retry a single event.
     */
    public void retryEvent(HookEventRecord eventRecord) {
        if (eventRecord.hasExceededMaxRetries()) {
            log.error("Max retries exceeded for event: eventId={}, retryCount={}, maxRetries={}", eventRecord.getEventId(),
                    eventRecord.getRetryCount(), eventRecord.getMaxRetries());
            handleMaxRetriesExceeded(eventRecord);
            return;
        }

        // Increment retry count
        eventRecord.incrementRetryCount();
        int attemptNumber = eventRecord.getRetryCount();
        eventRecord.setStatus(HookEventStatus.PENDING);
        eventRecordRepository.save(eventRecord);

        LocalDateTime attemptStartTime = DateUtils.getAuditLocalDateTime();
        long startTimeNanos = System.nanoTime();

        try {
            // Attempt send and wait for completion (throws exception if fails or times out)
            CompletableFuture<SendResult<String, String>> future = paystackExternalEventsKafkaTemplate.send(eventRecord.getTopicName(),
                    eventRecord.getPartitionKey(), eventRecord.getPayload());

            future.get(5, TimeUnit.SECONDS); // Wait for completion, throws exception on failure/timeout

            // Calculate duration
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;

            // Record successful attempt
            HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(eventRecord, attemptNumber, attemptStartTime, true, null,
                    durationMs);
            retryAttemptRepository.save(attempt);

            // Success
            eventRecord.setStatus(HookEventStatus.SENT);
            eventRecord.setSentAt(DateUtils.getAuditLocalDateTime());
            eventRecord.setErrorMessage(null);
            eventRecordRepository.save(eventRecord);

            log.info("Successfully retried hook event: eventId={}, retryCount={}, durationMs={}", eventRecord.getEventId(), attemptNumber,
                    durationMs);

            metrics.recordEventRetry(eventRecord.getEntityName(), eventRecord.getActionName(), true);

        } catch (InterruptedException e) {
            // Restore interruption status
            Thread.currentThread().interrupt();
            // Calculate duration
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
            String errorMessage = "Retry " + attemptNumber + " failed: Thread interrupted";

            // Record failed attempt
            HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(eventRecord, attemptNumber, attemptStartTime, false,
                    errorMessage, durationMs);
            retryAttemptRepository.save(attempt);

            log.warn("Retry interrupted for event: eventId={}, retryCount={}, durationMs={}", eventRecord.getEventId(), attemptNumber,
                    durationMs);

            eventRecord.setErrorMessage(errorMessage);
            eventRecordRepository.save(eventRecord);

            // Check if max retries exceeded
            if (eventRecord.hasExceededMaxRetries()) {
                handleMaxRetriesExceeded(eventRecord);
            }
            // If not exceeded, event remains PENDING and will be picked up by scheduled job
            // when the retry interval has elapsed (no immediate retry scheduling)

            metrics.recordEventRetry(eventRecord.getEntityName(), eventRecord.getActionName(), false);
        } catch (Exception e) {
            // Calculate duration
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
            String errorMessage = "Retry " + attemptNumber + " failed: " + e.getMessage();

            // Record failed attempt
            HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(eventRecord, attemptNumber, attemptStartTime, false,
                    errorMessage, durationMs);
            retryAttemptRepository.save(attempt);

            log.warn("Retry failed for event: eventId={}, retryCount={}, durationMs={}", eventRecord.getEventId(), attemptNumber,
                    durationMs, e);

            eventRecord.setErrorMessage(errorMessage);
            eventRecordRepository.save(eventRecord);

            // Check if max retries exceeded
            if (eventRecord.hasExceededMaxRetries()) {
                handleMaxRetriesExceeded(eventRecord);
            }
            // If not exceeded, event remains PENDING and will be picked up by scheduled job
            // when the retry interval has elapsed (no immediate retry scheduling)

            metrics.recordEventRetry(eventRecord.getEntityName(), eventRecord.getActionName(), false);
        }
    }

    /**
     * Handle max retries exceeded - send to DLQ.
     */
    private void handleMaxRetriesExceeded(HookEventRecord eventRecord) {
        eventRecord.setStatus(HookEventStatus.FAILED);
        eventRecord.setFailedAt(DateUtils.getAuditLocalDateTime());
        eventRecordRepository.save(eventRecord);

        // Send to DLQ
        dlqService.sendToDLQ(eventRecord);
    }

    /**
     * Calculate the retry interval duration based on configuration.
     *
     * @return Duration representing the retry interval
     */
    private Duration getRetryIntervalDuration() {
        PaystackEventProperties.HookProperties hookProps = eventProperties.getKafka().getHook();
        long value = hookProps.getRetryIntervalValue();
        RetryIntervalUnit unit = hookProps.getRetryIntervalUnit() != null ? hookProps.getRetryIntervalUnit()
                : RetryIntervalUnit.MINUTE;
        return unit.toDuration(value);
    }

    /**
     * Batch retry all pending events (scheduled job). Processes events for all tenants by iterating over each tenant
     * and setting the tenant context.
     * 
     * Only retries events that are eligible based on the configured retry interval (time-based).
     * An event is eligible if enough time has passed since the last retry (or since creation if never retried).
     */
    @Scheduled(fixedDelayString = "${paystack.events.kafka.hook.retry-check-interval-ms:60000}")
    public void retryPendingEvents() {
        try {
            List<FineractPlatformTenant> allTenants = tenantDetailsService.findAllTenants();
            for (FineractPlatformTenant tenant : allTenants) {
                processTenantRetries(tenant);
            }
        } catch (Exception e) {
            log.error("Error in retryPendingEvents scheduled job", e);
            // Don't re-throw to prevent scheduled task failure
        }
    }

    /**
     * Process retries for a single tenant with proper context management and error handling.
     */
    private void processTenantRetries(FineractPlatformTenant tenant) {
        try {
            initializeTenantContext(tenant);
            processPendingEventsForTenant(tenant.getTenantIdentifier());
        } catch (DataAccessException e) {
            handleDataAccessException(tenant, e);
        } catch (Exception e) {
            handleGeneralException(tenant, e);
        } finally {
            ThreadLocalContextUtil.reset();
        }
    }

    /**
     * Initialize tenant context for processing.
     */
    private void initializeTenantContext(FineractPlatformTenant tenant) {
        ThreadLocalContextUtil.setTenant(tenant);
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
    }

    /**
     * Handle DataAccessException with specific logic for missing tables.
     */
    private void handleDataAccessException(FineractPlatformTenant tenant, DataAccessException e) {
        if (isTableMissingError(e)) {
            logTableMissingWarning(tenant);
            return;
        }
        log.error("Error processing pending hook events for tenant: {}", tenant.getTenantIdentifier(), e);
        throw e;
    }

    /**
     * Handle general exceptions with fallback logic.
     */
    private void handleGeneralException(FineractPlatformTenant tenant, Exception e) {
        if (isTableMissingError(e)) {
            log.debug("Hook event tables not yet created for tenant: {}. Migrations may still be running.",
                    tenant.getTenantIdentifier());
            return;
        }
        log.error("Unexpected error processing pending hook events for tenant: {}", tenant.getTenantIdentifier(), e);
    }

    /**
     * Check if the exception indicates missing tables.
     */
    private boolean isTableMissingError(Exception e) {
        String errorMessage = extractErrorMessage(e);
        if (errorMessage == null) {
            return false;
        }
        return errorMessage.contains("does not exist")
                || (errorMessage.contains("relation") && errorMessage.contains("ps_hook_event_record"));
    }

    /**
     * Log warning when tables are missing for a tenant.
     */
    private void logTableMissingWarning(FineractPlatformTenant tenant) {
        String schemaName = tenant.getConnection() != null ? tenant.getConnection().getSchemaName() : "unknown";
        log.warn(
                "Hook event tables not found for tenant: {} (schema: {}). "
                        + "Migrations may not have run for this tenant. "
                        + "Please verify migrations have executed for tenant '{}' in database '{}'. "
                        + "See MIGRATION_DIAGNOSTICS.md for troubleshooting steps.",
                tenant.getTenantIdentifier(), schemaName, tenant.getTenantIdentifier(), schemaName);
    }

    /**
     * Process pending events for a specific tenant.
     * Only processes events that are eligible for retry based on the configured time interval.
     */
    private void processPendingEventsForTenant(String tenantIdentifier) {
        // Calculate cutoff time: events with lastRetryAt (or createdAt if never retried) before this time are eligible
        Duration retryInterval = getRetryIntervalDuration();
        LocalDateTime cutoffTime = DateUtils.getAuditLocalDateTime().minus(retryInterval);

        // Find events eligible for retry (status=PENDING, retryCount < maxRetries, and enough time has passed)
        List<HookEventRecord> eligibleEvents = eventRecordRepository.findEligibleForRetry(HookEventStatus.PENDING, cutoffTime);

        if (eligibleEvents.isEmpty()) {
            log.debug("No eligible events for retry in tenant: {} (cutoff time: {})", tenantIdentifier, cutoffTime);
            return;
        }

        log.info("Processing {} eligible hook events for retry in tenant: {} (retry interval: {}, cutoff time: {})",
                eligibleEvents.size(), tenantIdentifier, retryInterval, cutoffTime);

        for (HookEventRecord event : eligibleEvents) {
            retryEvent(event);
        }
    }

    /**
     * Extract error message from exception chain.
     */
    private String extractErrorMessage(Exception e) {
        String errorMessage = e.getMessage();
        Throwable cause = e.getCause();
        while (cause != null && (errorMessage == null || !errorMessage.contains("does not exist"))) {
            errorMessage = cause.getMessage();
            cause = cause.getCause();
        }
        return errorMessage;
    }
}
