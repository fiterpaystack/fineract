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
package com.paystack.fineract.infrastructure.event.external.exception;

import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * Exception thrown when a Kafka notification is not in the expected status for an operation.
 */
public class KafkaNotificationInvalidStatusException extends AbstractPlatformDomainRuleException {

    public KafkaNotificationInvalidStatusException(Long notificationId, KafkaNotificationStatus currentStatus,
            KafkaNotificationStatus expectedStatus) {
        super("error.msg.kafka.notification.invalid.status",
                String.format("Kafka notification with ID %d is in %s status, but %s status is required for this operation", notificationId,
                        currentStatus, expectedStatus),
                notificationId);
    }

    public KafkaNotificationInvalidStatusException(String message) {
        super("error.msg.kafka.notification.invalid.status", message);
    }

    public KafkaNotificationInvalidStatusException(String message, Throwable cause) {
        super("error.msg.kafka.notification.invalid.status", message, cause);
    }
}
