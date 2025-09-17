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
package com.paystack.fineract.infrastructure.event.external.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import com.paystack.fineract.infrastructure.event.external.producer.PaystackExternalEventProducer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.external.exception.AcknowledgementTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for handling asynchronous Kafka notifications with proper error handling, retry logic, and performance
 * monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "fineract.events.external.producer.kafka.enabled", havingValue = "true")
public class KafkaNotificationService {

    private final KafkaNotificationRepository kafkaNotificationRepository;
    private final PaystackExternalEventProducer paystackExternalEventProducer;
    private final ObjectMapper objectMapper;

    @Autowired
    @Qualifier("kafkaNotificationExecutor")
    private Executor kafkaNotificationExecutor;

    @Autowired
    @Qualifier("kafkaNotificationMaxRetries")
    private Integer maxRetries;

    /**
     * Sends a Kafka notification asynchronously with retry logic and proper error handling.
     *
     * @param notification
     *            the notification to send
     * @return CompletableFuture that completes when the notification is processed
     */
    public CompletableFuture<Void> sendNotificationAsync(KafkaNotification notification) {
        // Get context safely, handling the case where business dates might not be set (e.g., in tests)
        FineractContext context = null;
        try {
            context = ThreadLocalContextUtil.getContext();
        } catch (Exception e) {
            log.warn("Could not get FineractContext, proceeding without it: {}", e.getMessage());
        }

        final FineractContext finalContext = context;
        return CompletableFuture.runAsync(() -> {
            measure(() -> {
                // Only initialize context if it was successfully retrieved
                if (finalContext != null) {
                    try {
                        ThreadLocalContextUtil.init(finalContext);
                    } catch (Exception e) {
                        log.warn("Could not initialize ThreadLocalContext: {}", e.getMessage());
                    }
                }

                try {
                    sendNotificationWithRetry(notification);
                } catch (Exception e) {
                    log.error("Failed to send notification after all retries. Notification ID: {}", notification.getId(), e);
                    handleFinalFailure(notification, e);
                }
            }, timeTaken -> {
                if (log.isDebugEnabled()) {
                    log.debug("Processed Kafka notification ID: {} in {} ms", notification.getId(), timeTaken.toMillis());
                }
            });
        }, kafkaNotificationExecutor);
    }

    /**
     * Sends notification with retry logic.
     */
    private void sendNotificationWithRetry(KafkaNotification notification) {
        int retries = 0;
        boolean sent = false;

        while (!sent && retries <= maxRetries) {
            try {
                String jsonMessage = serializeNotification(notification);
                paystackExternalEventProducer.sendEvents(jsonMessage);

                sent = true;
                markNotificationSent(notification);
                log.info("Successfully sent Kafka notification ID: {} after {} retries", notification.getId(), retries);

            } catch (AcknowledgementTimeoutException e) {
                retries++;
                notification.incrementRetries();
                kafkaNotificationRepository.save(notification);

                if (retries > maxRetries) {
                    log.error("Max retries ({}) exceeded for Kafka notification ID: {}", maxRetries, notification.getId());
                    throw e;
                }

                log.warn("Timeout while waiting for acknowledgement from Kafka. Retry {}/{} for notification ID: {}", retries, maxRetries,
                        notification.getId());

                // Exponential backoff
                try {
                    Thread.sleep(Math.min(1000 * (1L << retries), 30000)); // Max 30 seconds
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted during retry backoff", ie);
                }

            } catch (JsonProcessingException e) {
                log.error("Error serializing notification to JSON for ID: {}", notification.getId(), e);
                // First save: increment retry count
                notification.incrementRetries();
                kafkaNotificationRepository.save(notification);

                // Second save: mark as failed
                notification.setStatus(KafkaNotificationStatus.FAILED);
                notification.setErrorMessage("JSON serialization error: " + e.getMessage());
                kafkaNotificationRepository.save(notification);
                throw new RuntimeException("Failed to serialize notification", e);
            } catch (RuntimeException e) {
                log.error("Error sending notification to Kafka for ID: {}", notification.getId(), e);
                notification.setStatus(KafkaNotificationStatus.FAILED);
                notification.setErrorMessage("Runtime error: " + e.getMessage());
                kafkaNotificationRepository.save(notification);
                throw e;
            }
        }
    }

    /**
     * Serializes notification to JSON.
     */
    private String serializeNotification(KafkaNotification notification) throws JsonProcessingException {
        KafkaNotificationDTO notificationDTO = new KafkaNotificationDTO(notification);
        return objectMapper.writeValueAsString(notificationDTO);
    }

    /**
     * Marks notification as sent in the database.
     */
    protected void markNotificationSent(KafkaNotification notification) {
        notification.setStatus(KafkaNotificationStatus.SENT);
        kafkaNotificationRepository.save(notification);
    }

    /**
     * Handles final failure after all retries are exhausted.
     */
    protected void handleFinalFailure(KafkaNotification notification, Exception e) {
        if (!KafkaNotificationStatus.FAILED.equals(notification.getStatus())) {
            notification.setStatus(KafkaNotificationStatus.FAILED);
            notification.setErrorMessage("Failed after max retries: " + e.getMessage());
            kafkaNotificationRepository.save(notification);
        }
    }

    /**
     * Performance measurement utility similar to the standard implementation.
     */
    private void measure(Runnable operation, java.util.function.Consumer<java.time.Duration> onComplete) {
        long startTime = System.nanoTime();
        try {
            operation.run();
        } finally {
            long endTime = System.nanoTime();
            java.time.Duration duration = java.time.Duration.ofNanos(endTime - startTime);
            onComplete.accept(duration);
        }
    }
}
