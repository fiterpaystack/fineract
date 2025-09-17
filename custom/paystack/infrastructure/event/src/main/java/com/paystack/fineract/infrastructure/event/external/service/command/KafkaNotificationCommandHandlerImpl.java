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
package com.paystack.fineract.infrastructure.event.external.service.command;

import com.paystack.fineract.infrastructure.event.external.service.KafkaNotificationWritePlatformService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.springframework.stereotype.Component;

/**
 * Implementation of KafkaNotificationCommandHandler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationCommandHandlerImpl implements KafkaNotificationCommandHandler {

    private final KafkaNotificationWritePlatformService kafkaNotificationWritePlatformService;

    @Override
    public CommandProcessingResult retryFailedNotification(JsonCommand command) {
        log.info("Processing retry failed notification command for ID: {}", command.entityId());

        Long notificationId = command.entityId();
        return kafkaNotificationWritePlatformService.retryFailedNotification(notificationId, command);
    }

    @Override
    public CommandProcessingResult retryFailedNotificationsByAccountId(JsonCommand command) {
        log.info("Processing retry failed notifications by account ID command for account: {}", command.entityId());

        Long accountId = command.entityId();
        return kafkaNotificationWritePlatformService.retryFailedNotificationsByAccountId(accountId, command);
    }

    @Override
    public CommandProcessingResult retryAllFailedNotifications(JsonCommand command) {
        log.info("Processing retry all failed notifications command");

        return kafkaNotificationWritePlatformService.retryAllFailedNotifications(command);
    }
}
