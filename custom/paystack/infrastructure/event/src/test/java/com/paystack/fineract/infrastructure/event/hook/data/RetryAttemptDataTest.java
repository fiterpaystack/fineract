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

import static org.assertj.core.api.Assertions.assertThat;

import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import java.time.LocalDateTime;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RetryAttemptData DTO.
 */
class RetryAttemptDataTest {

    private HookEventRecord eventRecord;
    private HookEventRetryAttempt retryAttempt;

    @BeforeEach
    void setUp() {
        eventRecord = new HookEventRecord();
        eventRecord.setId(1L);
        eventRecord.setEventId("test-event-1");
    }

    @Test
    void shouldMapSuccessfulAttemptToData() {
        // Given
        LocalDateTime attemptedAt = DateUtils.getAuditLocalDateTime();
        retryAttempt = HookEventRetryAttempt.newAttempt(eventRecord, 1, attemptedAt, true, null, 100L);
        retryAttempt.setId(10L);

        // When
        RetryAttemptData data = new RetryAttemptData(retryAttempt);

        // Then
        assertThat(data.getId()).isEqualTo(10L);
        assertThat(data.getAttemptNumber()).isEqualTo(1);
        assertThat(data.getAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(data.getSuccess()).isTrue();
        assertThat(data.getErrorMessage()).isNull();
        assertThat(data.getDurationMs()).isEqualTo(100L);
    }

    @Test
    void shouldMapFailedAttemptToData() {
        // Given
        LocalDateTime attemptedAt = DateUtils.getAuditLocalDateTime();
        retryAttempt = HookEventRetryAttempt.newAttempt(eventRecord, 2, attemptedAt, false, "Timeout error", 5000L);
        retryAttempt.setId(20L);

        // When
        RetryAttemptData data = new RetryAttemptData(retryAttempt);

        // Then
        assertThat(data.getId()).isEqualTo(20L);
        assertThat(data.getAttemptNumber()).isEqualTo(2);
        assertThat(data.getAttemptedAt()).isEqualTo(attemptedAt);
        assertThat(data.getSuccess()).isFalse();
        assertThat(data.getErrorMessage()).isEqualTo("Timeout error");
        assertThat(data.getDurationMs()).isEqualTo(5000L);
    }
}
