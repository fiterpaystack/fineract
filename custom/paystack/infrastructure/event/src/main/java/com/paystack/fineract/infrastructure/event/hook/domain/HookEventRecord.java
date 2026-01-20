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
package com.paystack.fineract.infrastructure.event.hook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.infrastructure.core.service.DateUtils;

/**
 * Entity for persisting hook events that need retry or DLQ handling.
 */
@Entity
@Table(name = "ps_hook_event_record")
@Getter
@NoArgsConstructor
public class HookEventRecord extends AbstractPersistableCustom<Long> {

    @Column(name = "event_id", nullable = false, unique = true, length = 200)
    @Setter
    private String eventId;

    @Column(name = "hook_id", nullable = false)
    @Setter
    private Long hookId;

    @Column(name = "entity_name", nullable = false, length = 50)
    @Setter
    private String entityName;

    @Column(name = "action_name", nullable = false, length = 50)
    @Setter
    private String actionName;

    @Column(name = "topic_name", nullable = false, length = 200)
    @Setter
    private String topicName;

    @Column(name = "partition_key", length = 100)
    @Setter
    private String partitionKey;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    @Setter
    private String payload;

    @Column(name = "tenant_identifier", nullable = false, length = 50)
    @Setter
    private String tenantIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Setter
    private HookEventStatus status = HookEventStatus.PENDING;

    @Column(name = "retry_count", nullable = false)
    @Setter
    private Integer retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    @Setter
    private Integer maxRetries = 3;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Setter
    private String errorMessage;

    @Column(name = "last_retry_at")
    @Setter
    private LocalDateTime lastRetryAt;

    @Column(name = "sent_at")
    @Setter
    private LocalDateTime sentAt;

    @Column(name = "failed_at")
    @Setter
    private LocalDateTime failedAt;

    @Column(name = "created_at", nullable = false)
    @Setter
    private LocalDateTime createdAt = DateUtils.getAuditLocalDateTime();

    /**
     * Increment the retry count.
     */
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
        this.lastRetryAt = DateUtils.getAuditLocalDateTime();
    }

    /**
     * Check if max retries have been exceeded.
     */
    public boolean hasExceededMaxRetries() {
        return this.retryCount != null && this.maxRetries != null
                && this.retryCount >= this.maxRetries;
    }
}
