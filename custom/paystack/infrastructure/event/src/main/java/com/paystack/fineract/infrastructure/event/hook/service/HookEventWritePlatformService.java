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

/**
 * Service interface for writing hook event operations.
 */
public interface HookEventWritePlatformService {

    /**
     * Retry a single failed hook event.
     *
     * @param eventId
     *            the event ID to retry
     * @return HookEventRecord after retry attempt
     * @throws com.paystack.fineract.infrastructure.event.hook.exception.HookEventNotFoundException
     *             if not found
     * @throws org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException
     *             if event not in FAILED status
     */
    HookEventRecord retryEvent(String eventId);

    /**
     * Retry all failed hook events.
     *
     * @return RetryResult containing success and failure counts
     */
    RetryResult retryAllFailed();

    /**
     * Result of retry all operation.
     */
    record RetryResult(int totalEvents, int successCount, int failureCount) {
    }
}
