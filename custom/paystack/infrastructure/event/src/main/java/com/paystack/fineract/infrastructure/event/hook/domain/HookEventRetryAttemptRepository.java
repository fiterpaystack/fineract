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

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for hook event retry attempts.
 */
@Repository
public interface HookEventRetryAttemptRepository extends JpaRepository<HookEventRetryAttempt, Long> {

    /**
     * Find all retry attempts for a specific event, ordered by attempt number.
     */
    @Query("SELECT a FROM HookEventRetryAttempt a WHERE a.eventRecord.id = :eventId ORDER BY a.attemptNumber ASC")
    List<HookEventRetryAttempt> findByEventIdOrderByAttemptNumber(@Param("eventId") Long eventId);

    /**
     * Find all retry attempts for a specific event by event ID string, ordered by attempt number.
     */
    @Query("SELECT a FROM HookEventRetryAttempt a WHERE a.eventRecord.eventId = :eventId ORDER BY a.attemptNumber ASC")
    List<HookEventRetryAttempt> findByEventIdStringOrderByAttemptNumber(@Param("eventId") String eventId);

    /**
     * Count retry attempts for a specific event.
     */
    @Query("SELECT COUNT(a) FROM HookEventRetryAttempt a WHERE a.eventRecord.id = :eventId")
    Long countByEventId(@Param("eventId") Long eventId);

    /**
     * Count failed retry attempts for a specific event.
     */
    @Query("SELECT COUNT(a) FROM HookEventRetryAttempt a WHERE a.eventRecord.id = :eventId AND a.success = false")
    Long countFailedByEventId(@Param("eventId") Long eventId);
}
