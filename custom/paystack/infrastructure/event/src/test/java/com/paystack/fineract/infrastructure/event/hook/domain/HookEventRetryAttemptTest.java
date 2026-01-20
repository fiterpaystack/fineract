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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for HookEventRetryAttempt entity.
 */
class HookEventRetryAttemptTest {

    private HookEventRecord eventRecord;

    @BeforeEach
    void setUp() {
        eventRecord = new HookEventRecord();
        eventRecord.setId(1L);
        eventRecord.setEventId("test-event-1");
    }

    @Test
    void shouldCreateSuccessfulRetryAttempt() {
        // Given
        LocalDateTime attemptedAt = DateUtils.getAuditLocalDateTime();
        Integer attemptNumber = 1;
        boolean success = true;
        String errorMessage = null;
        Long durationMs = 100L;

        // When
        HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(eventRecord, attemptNumber, attemptedAt, success, errorMessage,
                durationMs);

        // Then
        assertThat(attempt.getEventRecord()).isEqualTo(eventRecord);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(attempt.getSuccess()).isTrue();
        assertThat(attempt.getErrorMessage()).isNull();
        assertThat(attempt.getDurationMs()).isEqualTo(100L);
    }

    @Test
    void shouldCreateFailedRetryAttempt() {
        // Given
        LocalDateTime attemptedAt = DateUtils.getAuditLocalDateTime();
        Integer attemptNumber = 2;
        boolean success = false;
        String errorMessage = "Timeout error";
        Long durationMs = 5000L;

        // When
        HookEventRetryAttempt attempt = HookEventRetryAttempt.newAttempt(eventRecord, attemptNumber, attemptedAt, success, errorMessage,
                durationMs);

        // Then
        assertThat(attempt.getEventRecord()).isEqualTo(eventRecord);
        assertThat(attempt.getAttemptNumber()).isEqualTo(2);
        assertThat(attempt.getAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(attempt.getSuccess()).isFalse();
        assertThat(attempt.getErrorMessage()).isEqualTo("Timeout error");
        assertThat(attempt.getDurationMs()).isEqualTo(5000L);
    }
}
