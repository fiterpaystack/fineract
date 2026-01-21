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
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for reading hook event operations.
 */
public interface HookEventReadPlatformService {

    /**
     * Retrieve hook event by event ID.
     *
     * @param eventId
     *            the event ID
     * @return HookEventRecord
     * @throws com.paystack.fineract.infrastructure.event.hook.exception.HookEventNotFoundException
     *             if not found
     */
    HookEventRecord retrieveOne(String eventId);

    /**
     * Retrieve all hook events as data objects with pagination, optionally filtered by status.
     *
     * @param status
     *            optional status filter. If null, returns all events.
     * @param pageable
     *            pagination parameters
     * @return Page of hook event data objects
     */
    Page<HookEventData> retrieveAll(HookEventStatus status, Pageable pageable);

    /**
     * Validate and parse status string to enum.
     *
     * @param statusParam
     *            the status string to validate
     * @return HookEventStatus enum
     * @throws org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException
     *             if invalid
     */
    HookEventStatus validateAndParseStatus(String statusParam);

    /**
     * Retrieve all retry attempts for a specific event, ordered by attempt number.
     *
     * @param eventId
     *            the event ID
     * @return List of retry attempt data objects
     * @throws com.paystack.fineract.infrastructure.event.hook.exception.HookEventNotFoundException
     *             if event not found
     */
    List<RetryAttemptData> retrieveRetryAttempts(String eventId);
}
