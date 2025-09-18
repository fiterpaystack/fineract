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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;

/**
 * Entity class for the ps_kafka_notification table which is used to keep track of Paystack notifications that are sent
 * on the Kafka queue.
 */
@Entity
@Table(name = "ps_kafka_notification")
@Getter
@NoArgsConstructor
public class KafkaNotification extends AbstractPersistableCustom<Long> {

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private SavingsAccount account;

    @Column(name = "pnd_reason", length = 500)
    private String pndReason;

    @Setter
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    @Column(name = "transaction_details", length = 1000)
    private String transactionDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private KafkaNotificationStatus status;

    @Column(name = "no_of_retries", nullable = false)
    @Setter
    private Integer numberOfRetries;

    @Column(name = "error_message", length = 1000)
    @Setter
    private String errorMessage;

    /**
     * Constructor for creating a new KafkaNotification.
     */
    public KafkaNotification(String eventType, SavingsAccount account, String pndReason, String transactionDetails) {
        this.eventType = eventType;
        this.account = account;
        this.pndReason = pndReason;
        this.transactionDetails = transactionDetails;
        this.createdDate = LocalDateTime.now();
        this.status = KafkaNotificationStatus.PENDING;
        this.numberOfRetries = 0;
    }

    /**
     * Increment the number of retries.
     */
    public void incrementRetries() {
        this.numberOfRetries++;
    }
}
