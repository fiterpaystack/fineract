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
package com.paystack.fineract.infrastructure.event.hook.service;

import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.stereotype.Service;

/**
 * Service implementation for writing hook event operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HookEventWritePlatformServiceImpl implements HookEventWritePlatformService {

    private final HookEventReadPlatformService readPlatformService;
    private final HookEventRetryService retryService;
    private final HookEventRecordRepository eventRecordRepository;

    @Override
    public HookEventRecord retryEvent(String eventId) {
        HookEventRecord event = readPlatformService.retrieveOne(eventId);

        // Validate event is in FAILED status
        if (event.getStatus() != HookEventStatus.FAILED) {
            List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            ApiParameterError error = ApiParameterError.parameterError("error.msg.hook.event.invalid.status.for.retry",
                    "Event must be in FAILED status to retry. Current status: " + event.getStatus(),
                    "status", event.getStatus().name(), HookEventStatus.FAILED.name());
            dataValidationErrors.add(error);
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        retryService.retryEvent(event);
        return event;
    }

    @Override
    public RetryResult retryAllFailed() {
        // Get all failed events directly from repository (we need entities for write operations)
        List<HookEventRecord> failedEvents = eventRecordRepository.findByStatus(HookEventStatus.FAILED);

        if (failedEvents.isEmpty()) {
            return new RetryResult(0, 0, 0);
        }

        int successCount = 0;
        int failureCount = 0;

        for (HookEventRecord event : failedEvents) {
            try {
                retryService.retryEvent(event);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to retry event: eventId={}", event.getEventId(), e);
                failureCount++;
            }
        }

        return new RetryResult(failedEvents.size(), successCount, failureCount);
    }
}
