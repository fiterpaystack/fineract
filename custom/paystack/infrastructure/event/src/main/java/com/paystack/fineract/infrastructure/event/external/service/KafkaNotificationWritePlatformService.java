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

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;

/**
 * Service interface for writing Kafka notification operations.
 */
public interface KafkaNotificationWritePlatformService {

    /**
     * Retry a specific failed notification by ID.
     *
     * @param notificationId
     *            the notification ID to retry
     * @param command
     *            the JSON command containing retry parameters
     * @return CommandProcessingResult with the result of the retry operation
     */
    CommandProcessingResult retryFailedNotification(Long notificationId, JsonCommand command);

    /**
     * Retry all failed notifications for a specific account.
     *
     * @param accountId
     *            the account ID to retry notifications for
     * @param command
     *            the JSON command containing retry parameters
     * @return CommandProcessingResult with the result of the retry operation
     */
    CommandProcessingResult retryFailedNotificationsByAccountId(Long accountId, JsonCommand command);

    /**
     * Retry all failed notifications in the system.
     *
     * @param command
     *            the JSON command containing retry parameters
     * @return CommandProcessingResult with the result of the retry operation
     */
    CommandProcessingResult retryAllFailedNotifications(JsonCommand command);
}
