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

import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.core.service.Page;

/**
 * Service interface for reading Paystack Kafka notifications with search and pagination capabilities.
 */
public interface KafkaNotificationReadPlatformService {

    /**
     * Retrieves a paginated list of Kafka notifications with optional search criteria.
     *
     * @param customerId
     *            optional customer ID filter
     * @param eventType
     *            optional event type filter (supports partial matching)
     * @param accountId
     *            optional account ID filter
     * @param status
     *            optional status filter
     * @param fromDate
     *            optional start date filter (inclusive)
     * @param toDate
     *            optional end date filter (inclusive)
     * @param limit
     *            maximum number of results per page
     * @param offset
     *            number of results to skip
     * @return Page containing KafkaNotificationDTOs and total count
     */
    Page<KafkaNotificationDTO> searchNotifications(Long customerId, String eventType, Long accountId, KafkaNotificationStatus status,
            LocalDateTime fromDate, LocalDateTime toDate, Integer limit, Integer offset);

    /**
     * Retrieves a specific Kafka notification by ID.
     *
     * @param id
     *            the notification ID
     * @return KafkaNotificationDTO or null if not found
     */
    KafkaNotificationDTO getNotificationById(Long id);

    /**
     * Retrieves notifications for a specific account.
     *
     * @param accountId
     *            the account ID
     * @param limit
     *            maximum number of results per page
     * @param offset
     *            number of results to skip
     * @return Page containing KafkaNotificationDTOs and total count
     */
    Page<KafkaNotificationDTO> getNotificationsByAccount(Long accountId, Integer limit, Integer offset);

    /**
     * Retrieves notifications by status.
     *
     * @param status
     *            the notification status
     * @param limit
     *            maximum number of results per page
     * @param offset
     *            number of results to skip
     * @return Page containing KafkaNotificationDTOs and total count
     */
    Page<KafkaNotificationDTO> getNotificationsByStatus(KafkaNotificationStatus status, Integer limit, Integer offset);

    /**
     * Validates pagination parameters and throws validation exceptions if invalid.
     *
     * @param limit
     *            maximum number of results per page
     * @param offset
     *            number of results to skip
     * @throws org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException
     *             if validation fails
     */
    void validatePaginationParameters(Integer limit, Integer offset);

    /**
     * Validates and parses status string to KafkaNotificationStatus enum.
     *
     * @param status
     *            the status string to validate and parse
     * @return KafkaNotificationStatus enum value
     * @throws org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException
     *             if status is invalid
     */
    KafkaNotificationStatus validateAndParseStatus(String status);

    /**
     * Validates that a notification exists by ID.
     *
     * @param notificationId
     *            the notification ID to validate
     * @throws org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException
     *             if notification not found
     */
    void validateNotificationExists(Long notificationId);
}
