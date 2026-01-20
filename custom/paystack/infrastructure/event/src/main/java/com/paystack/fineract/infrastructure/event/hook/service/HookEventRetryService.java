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
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttemptRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Qualifier("kafkaHookRetryExecutor")
    private final Executor kafkaHookRetryExecutor;

    /**
     * Schedule async retry for a failed event.
     */
    public void scheduleRetry(HookEventRecord eventRecord) {
        CompletableFuture.runAsync(() -> {
            // Wait before retry (exponential backoff)
            long delay = calculateRetryDelay(eventRecord.getRetryCount());
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            retryEvent(eventRecord);
        }, kafkaHookRetryExecutor);
    }

    /**
     * Retry a single event.
     */
    public void retryEvent(HookEventRecord eventRecord) {
        if (eventRecord.hasExceededMaxRetries()) {
            log.error("Max retries exceeded for event: eventId={}, retryCount={}, maxRetries={}",
                    eventRecord.getEventId(), eventRecord.getRetryCount(), eventRecord.getMaxRetries());
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
            // Attempt send
            CompletableFuture<SendResult<String, String>> future = paystackExternalEventsKafkaTemplate.send(
                    eventRecord.getTopicName(),
                    eventRecord.getPartitionKey(),
                    eventRecord.getPayload());

            SendResult<String, String> result = future.get(5, TimeUnit.SECONDS);

            // Calculate duration
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;

            // Record successful attempt
            HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(
                    eventRecord,
                    attemptNumber,
                    attemptStartTime,
                    true,
                    null,
                    durationMs);
            retryAttemptRepository.save(attempt);

            // Success
            eventRecord.setStatus(HookEventStatus.SENT);
            eventRecord.setSentAt(DateUtils.getAuditLocalDateTime());
            eventRecord.setErrorMessage(null);
            eventRecordRepository.save(eventRecord);

            log.info("Successfully retried hook event: eventId={}, retryCount={}, durationMs={}",
                    eventRecord.getEventId(), attemptNumber, durationMs);

            metrics.recordEventRetry(eventRecord.getEntityName(), eventRecord.getActionName(), true);

        } catch (Exception e) {
            // Calculate duration
            long durationMs = (System.nanoTime() - startTimeNanos) / 1_000_000;
            String errorMessage = "Retry " + attemptNumber + " failed: " + e.getMessage();

            // Record failed attempt
            HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(
                    eventRecord,
                    attemptNumber,
                    attemptStartTime,
                    false,
                    errorMessage,
                    durationMs);
            retryAttemptRepository.save(attempt);

            log.warn("Retry failed for event: eventId={}, retryCount={}, durationMs={}",
                    eventRecord.getEventId(), attemptNumber, durationMs, e);

            eventRecord.setErrorMessage(errorMessage);
            eventRecordRepository.save(eventRecord);

            // Schedule next retry if not exceeded
            if (!eventRecord.hasExceededMaxRetries()) {
                scheduleRetry(eventRecord);
            } else {
                handleMaxRetriesExceeded(eventRecord);
            }

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
     * Calculate exponential backoff delay.
     */
    private long calculateRetryDelay(int retryCount) {
        PaystackEventProperties.HookProperties hookProps = eventProperties.getKafka().getHook();
        long initialDelay = hookProps.getRetryInitialDelayMs();
        long maxDelay = hookProps.getRetryMaxDelayMs();

        long delay = initialDelay * (1L << retryCount); // Exponential: 1s, 2s, 4s, 8s...
        return Math.min(delay, maxDelay); // Cap at max delay
    }

    /**
     * Batch retry all pending events (scheduled job).
     * Processes events for all tenants by iterating over each tenant and setting the tenant context.
     */
    @Scheduled(fixedDelayString = "${paystack.events.kafka.hook.retry-interval-ms:60000}")
    public void retryPendingEvents() {
        try {
            List<FineractPlatformTenant> allTenants = tenantDetailsService.findAllTenants();
            
            for (FineractPlatformTenant tenant : allTenants) {
                boolean contextInitialized = false;
                try {
                    // Set tenant context for this iteration
                    contextInitialized = true;
                    ThreadLocalContextUtil.setTenant(tenant);
                    ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
                    
                    // Process pending events for this tenant
                    processPendingEventsForTenant(tenant.getTenantIdentifier());
                } catch (DataAccessException e) {
                    // Gracefully handle cases where tables don't exist yet (migrations not run)
                    String errorMessage = extractErrorMessage(e);
                    
                    if (errorMessage != null && (errorMessage.contains("does not exist") 
                            || (errorMessage.contains("relation") && errorMessage.contains("ps_hook_event_record")))) {
                        log.warn("Hook event tables not found for tenant: {} (schema: {}). " +
                                "Migrations may not have run for this tenant. " +
                                "Please verify migrations have executed for tenant '{}' in database '{}'. " +
                                "See MIGRATION_DIAGNOSTICS.md for troubleshooting steps.",
                                tenant.getTenantIdentifier(),
                                tenant.getConnection() != null ? tenant.getConnection().getSchemaName() : "unknown",
                                tenant.getTenantIdentifier(),
                                tenant.getConnection() != null ? tenant.getConnection().getSchemaName() : "unknown");
                        continue; // Skip this tenant and continue with next
                    }
                    // Re-throw other exceptions for proper error handling
                    log.error("Error processing pending hook events for tenant: {}", tenant.getTenantIdentifier(), e);
                    throw e;
                } catch (Exception e) {
                    // Catch any other unexpected exceptions
                    String errorMessage = extractErrorMessage(e);
                    if (errorMessage != null && errorMessage.contains("does not exist")) {
                        log.debug("Hook event tables not yet created for tenant: {}. Migrations may still be running.", 
                                tenant.getTenantIdentifier());
                        continue; // Skip this tenant and continue with next
                    }
                    log.error("Unexpected error processing pending hook events for tenant: {}", 
                            tenant.getTenantIdentifier(), e);
                    // Continue with next tenant instead of failing entire job
                } finally {
                    if (contextInitialized) {
                        ThreadLocalContextUtil.reset();
                    }
                }
            }
        } catch (Exception e) {
            // Catch any errors at the outer level (e.g., tenantDetailsService.findAllTenants())
            log.error("Error in retryPendingEvents scheduled job", e);
            // Don't re-throw to prevent scheduled task failure
        }
    }
    
    /**
     * Process pending events for a specific tenant.
     */
    private void processPendingEventsForTenant(String tenantIdentifier) {
        try {
            List<HookEventRecord> pendingEvents = eventRecordRepository
                    .findByStatusAndRetryCountLessThanMax(HookEventStatus.PENDING);

            if (pendingEvents.isEmpty()) {
                return;
            }

            log.debug("Processing {} pending hook events for retry in tenant: {}", 
                    pendingEvents.size(), tenantIdentifier);

            for (HookEventRecord event : pendingEvents) {
                retryEvent(event);
            }
        } catch (DataAccessException e) {
            // Re-throw to be handled by caller with tenant context
            throw e;
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
