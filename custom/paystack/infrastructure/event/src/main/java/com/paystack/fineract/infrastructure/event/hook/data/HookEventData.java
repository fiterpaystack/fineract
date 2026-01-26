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
package com.paystack.fineract.infrastructure.event.hook.data;

import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * Data transfer object for hook event records.
 */
@Data
public class HookEventData {

    private Long id;
    private String eventId;
    private Long hookId;
    private String entityName;
    private String actionName;
    private String topicName;
    private String partitionKey;
    private HookEventStatus status;
    private Integer retryCount;
    private Integer maxRetries;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime lastRetryAt;
    private LocalDateTime sentAt;
    private LocalDateTime failedAt;
    private String tenantIdentifier;
    private String payload;

    public HookEventData() {
        // Default constructor
    }

    public HookEventData(HookEventRecord eventRecord) {
        this.id = eventRecord.getId();
        this.eventId = eventRecord.getEventId();
        this.hookId = eventRecord.getHookId();
        this.entityName = eventRecord.getEntityName();
        this.actionName = eventRecord.getActionName();
        this.topicName = eventRecord.getTopicName();
        this.partitionKey = eventRecord.getPartitionKey();
        this.status = eventRecord.getStatus();
        this.retryCount = eventRecord.getRetryCount();
        this.maxRetries = eventRecord.getMaxRetries();
        this.errorMessage = eventRecord.getErrorMessage();
        this.createdAt = eventRecord.getCreatedAt();
        this.lastRetryAt = eventRecord.getLastRetryAt();
        this.sentAt = eventRecord.getSentAt();
        this.failedAt = eventRecord.getFailedAt();
        this.tenantIdentifier = eventRecord.getTenantIdentifier();
        this.payload = eventRecord.getPayload();
    }
}
