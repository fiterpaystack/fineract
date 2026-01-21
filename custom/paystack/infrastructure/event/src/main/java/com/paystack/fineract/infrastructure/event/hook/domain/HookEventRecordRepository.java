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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for HookEventRecord entities.
 */
@Repository
public interface HookEventRecordRepository extends JpaRepository<HookEventRecord, Long>, JpaSpecificationExecutor<HookEventRecord> {

    /**
     * Find event record by event ID.
     */
    Optional<HookEventRecord> findByEventId(String eventId);

    /**
     * Find all records with given status.
     */
    List<HookEventRecord> findByStatus(HookEventStatus status);

    /**
     * Find all records with given status and retry count less than max retries.
     */
    @Query("SELECT e FROM HookEventRecord e WHERE e.status = :status AND e.retryCount < e.maxRetries")
    List<HookEventRecord> findByStatusAndRetryCountLessThanMax(@Param("status") HookEventStatus status);

    /**
     * Find all failed records for a given hook.
     */
    List<HookEventRecord> findByHookIdAndStatus(Long hookId, HookEventStatus status);

    /**
     * Find all records for a given tenant.
     */
    List<HookEventRecord> findByTenantIdentifier(String tenantIdentifier);

    /**
     * Find pending events eligible for retry based on time interval. An event is eligible if: - Status is PENDING -
     * retryCount < maxRetries - COALESCE(lastRetryAt, createdAt) <= cutoffTime
     *
     * Performance optimization: Uses COALESCE to avoid OR condition, enabling better index usage. The query uses
     * COALESCE(lastRetryAt, createdAt) which returns lastRetryAt if not null, otherwise createdAt. This allows the
     * database to use indexes more efficiently.
     *
     * @param status
     *            the status to filter by (typically PENDING)
     * @param cutoffTime
     *            the cutoff time - events with lastRetryAt (or createdAt if never retried) before this time are
     *            eligible
     * @return List of eligible events for retry
     */
    @Query("""
            SELECT e FROM HookEventRecord e
            WHERE e.status = :status
            AND e.retryCount < e.maxRetries
            AND COALESCE(e.lastRetryAt, e.createdAt) <= :cutoffTime
            """)
    List<HookEventRecord> findEligibleForRetry(@Param("status") HookEventStatus status, @Param("cutoffTime") LocalDateTime cutoffTime);
}
