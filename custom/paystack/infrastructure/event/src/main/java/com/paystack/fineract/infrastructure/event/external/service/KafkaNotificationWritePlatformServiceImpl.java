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
import com.paystack.fineract.infrastructure.event.external.exception.KafkaNotificationInvalidStatusException;
import com.paystack.fineract.infrastructure.event.external.exception.KafkaNotificationNotFoundException;
import com.paystack.fineract.infrastructure.event.external.producer.PaystackExternalEventProducer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of KafkaNotificationWritePlatformService for retry operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class KafkaNotificationWritePlatformServiceImpl implements KafkaNotificationWritePlatformService {

    private final KafkaNotificationRepository kafkaNotificationRepository;
    private final ObjectMapper objectMapper;
    @Autowired(required = false)
    private PaystackExternalEventProducer paystackExternalEventProducer;

    @Override
    public CommandProcessingResult retryFailedNotification(Long notificationId, JsonCommand command) {
        log.info("Retrying failed notification with ID: {}", notificationId);

        KafkaNotification notification = kafkaNotificationRepository.findById(notificationId)
                .orElseThrow(() -> new KafkaNotificationNotFoundException(notificationId));

        if (!KafkaNotificationStatus.FAILED.equals(notification.getStatus())) {
            throw new KafkaNotificationInvalidStatusException(notificationId, notification.getStatus(), KafkaNotificationStatus.FAILED);
        }

        try {
            // Reset notification status to PENDING for retry
            notification.setStatus(KafkaNotificationStatus.PENDING);
            notification.setErrorMessage(null);
            kafkaNotificationRepository.save(notification);

            // Send the notification again
            sendNotificationToKafka(notification);

            log.info("Successfully retried notification with ID: {}", notificationId);

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(notificationId).build();

        } catch (Exception e) {
            log.error("Failed to retry notification with ID: {}", notificationId, e);

            // Mark as failed again if retry fails
            notification.setStatus(KafkaNotificationStatus.FAILED);
            notification.setErrorMessage("Retry failed: " + e.getMessage());
            kafkaNotificationRepository.save(notification);

            throw new RuntimeException("Failed to retry notification: " + e.getMessage(), e);
        }
    }

    @Override
    public CommandProcessingResult retryFailedNotificationsByAccountId(Long accountId, JsonCommand command) {
        log.info("Retrying failed notifications for account ID: {}", accountId);

        // Find all failed notifications for the account
        Specification<KafkaNotification> spec = Specification.where(
                (root, query, criteriaBuilder) -> criteriaBuilder.and(criteriaBuilder.equal(root.get("account").get("id"), accountId),
                        criteriaBuilder.equal(root.get("status"), KafkaNotificationStatus.FAILED)));

        List<KafkaNotification> failedNotifications = kafkaNotificationRepository.findAll(spec);

        if (failedNotifications.isEmpty()) {
            log.info("No failed notifications found for account ID: {}", accountId);
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accountId).build();
        }

        int successCount = 0;
        int failureCount = 0;

        for (KafkaNotification notification : failedNotifications) {
            try {
                // Reset notification status to PENDING for retry
                notification.setStatus(KafkaNotificationStatus.PENDING);
                notification.setErrorMessage(null);
                kafkaNotificationRepository.save(notification);

                // Send the notification again
                sendNotificationToKafka(notification);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to retry notification with ID: {} for account: {}", notification.getId(), accountId, e);

                // Mark as failed again if retry fails
                notification.setStatus(KafkaNotificationStatus.FAILED);
                notification.setErrorMessage("Retry failed: " + e.getMessage());
                kafkaNotificationRepository.save(notification);
                failureCount++;
            }
        }

        log.info("Retry completed for account ID: {}. Success: {}, Failures: {}", accountId, successCount, failureCount);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(accountId).build();
    }

    @Override
    public CommandProcessingResult retryAllFailedNotifications(JsonCommand command) {
        log.info("Retrying all failed notifications");

        // Find all failed notifications
        Specification<KafkaNotification> spec = Specification
                .where((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), KafkaNotificationStatus.FAILED));

        List<KafkaNotification> failedNotifications = kafkaNotificationRepository.findAll(spec);

        if (failedNotifications.isEmpty()) {
            log.info("No failed notifications found");
            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).build();
        }

        int successCount = 0;
        int failureCount = 0;

        for (KafkaNotification notification : failedNotifications) {
            try {
                // Reset notification status to PENDING for retry
                notification.setStatus(KafkaNotificationStatus.PENDING);
                notification.setErrorMessage(null);
                kafkaNotificationRepository.save(notification);

                // Send the notification again
                sendNotificationToKafka(notification);
                successCount++;

            } catch (Exception e) {
                log.error("Failed to retry notification with ID: {}", notification.getId(), e);

                // Mark as failed again if retry fails
                notification.setStatus(KafkaNotificationStatus.FAILED);
                notification.setErrorMessage("Retry failed: " + e.getMessage());
                kafkaNotificationRepository.save(notification);
                failureCount++;
            }
        }

        log.info("Retry completed for all failed notifications. Success: {}, Failures: {}", successCount, failureCount);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).build();
    }

    /**
     * Send notification to Kafka.
     */
    private void sendNotificationToKafka(KafkaNotification notification) throws JsonProcessingException {
        // Check if producer is available
        if (paystackExternalEventProducer != null) {
            // Convert to DTO and serialize
            KafkaNotificationDTO notificationDTO = new KafkaNotificationDTO(notification);

            String jsonMessage = objectMapper.writeValueAsString(notificationDTO);

            // Send to Kafka
            paystackExternalEventProducer.sendEvents(jsonMessage);

            // Update status to SENT
            notification.setStatus(KafkaNotificationStatus.SENT);
            kafkaNotificationRepository.save(notification);
        } else {
            log.warn("Kafka notification producer not available. Notification was not sent.");
        }
    }
}
