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
package com.paystack.fineract.infrastructure.event.external.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO class for the KafkaNotification entity that will be serialized and sent to Kafka.
 */
@Getter
@Setter
@NoArgsConstructor
public class KafkaNotificationDTO {

    private Long id;
    private Long customerId;
    private String eventType;
    private Long accountId;
    private String pndReason;
    private LocalDateTime timestamp;
    private String transactionDetails;
    private String clientName;
    private String accountProductName;
    private String accountNumber;
    private String status;

    /**
     * Constructor for creating a new KafkaNotificationDTO from a KafkaNotification entity. This constructor is used for
     * Kafka serialization and does not populate the additional fields.
     */
    public KafkaNotificationDTO(KafkaNotification notification) {
        this.id = notification.getId();
        this.customerId = notification.getAccount().getClient() != null ? notification.getAccount().getClient().getId() : null;
        this.eventType = notification.getEventType();
        this.accountId = notification.getAccount().getId();
        this.pndReason = notification.getPndReason();
        this.timestamp = notification.getCreatedDate();
        this.transactionDetails = notification.getTransactionDetails();
    }

    /**
     * Constructor for creating a new KafkaNotificationDTO from a KafkaNotification entity with additional fields. This
     * constructor is used by the ReadPlatformService to populate clientName, accountProductName, status, and
     * accountNumber.
     */
    public KafkaNotificationDTO(KafkaNotification notification, boolean populateAdditionalFields) {
        this.id = notification.getId();
        this.customerId = notification.getAccount().getClient() != null ? notification.getAccount().getClient().getId() : null;
        this.eventType = notification.getEventType();
        this.accountId = notification.getAccount().getId();
        this.pndReason = notification.getPndReason();
        this.timestamp = notification.getCreatedDate();
        this.transactionDetails = notification.getTransactionDetails();
        this.status = notification.getStatus().name();

        if (populateAdditionalFields) {
            // Populate client name (display name)
            if (notification.getAccount().getClient() != null) {
                this.clientName = notification.getAccount().getClient().getDisplayName();
            }

            // Populate account product name
            if (notification.getAccount().savingsProduct() != null) {
                this.accountProductName = notification.getAccount().savingsProduct().getName();
            }

            // Populate account number
            this.accountNumber = notification.getAccount().getAccountNumber();
        }
    }
}
