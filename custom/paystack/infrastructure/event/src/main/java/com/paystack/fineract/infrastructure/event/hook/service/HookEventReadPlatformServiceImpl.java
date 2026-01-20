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

import com.paystack.fineract.infrastructure.event.hook.data.HookEventData;
import com.paystack.fineract.infrastructure.event.hook.data.RetryAttemptData;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttemptRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.exception.HookEventNotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.springframework.stereotype.Service;

/**
 * Service implementation for reading hook event operations.
 */
@Service
@RequiredArgsConstructor
public class HookEventReadPlatformServiceImpl implements HookEventReadPlatformService {

    private final HookEventRecordRepository eventRecordRepository;
    private final HookEventRetryAttemptRepository retryAttemptRepository;

    @Override
    public HookEventRecord retrieveOne(String eventId) {
        return eventRecordRepository.findByEventId(eventId).orElseThrow(() -> new HookEventNotFoundException(eventId));
    }

    @Override
    public List<HookEventData> retrieveAll(HookEventStatus status) {
        List<HookEventRecord> records;
        if (status != null) {
            records = eventRecordRepository.findByStatus(status);
        } else {
            records = eventRecordRepository.findAll();
        }
        return records.stream().map(HookEventData::new).toList();
    }

    @Override
    public HookEventStatus validateAndParseStatus(String statusParam) {
        if (statusParam == null || statusParam.isBlank()) {
            return null;
        }
        try {
            return HookEventStatus.valueOf(statusParam.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new UnrecognizedQueryParamException("status", statusParam, new Object[] { "PENDING", "SENT", "FAILED", "DLQ" });
        }
    }

    @Override
    public List<RetryAttemptData> retrieveRetryAttempts(String eventId) {
        // Verify event exists
        retrieveOne(eventId);

        // Get all retry attempts for this event
        List<HookEventRetryAttempt> attempts = retryAttemptRepository.findByEventIdStringOrderByAttemptNumber(eventId);
        return attempts.stream().map(RetryAttemptData::new).toList();
    }
}
