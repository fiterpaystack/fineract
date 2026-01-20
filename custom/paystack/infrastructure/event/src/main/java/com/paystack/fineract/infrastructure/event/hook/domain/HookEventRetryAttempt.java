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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Entity for tracking individual retry attempts for hook events.
 *
 * This provides a complete audit trail of all retry attempts, including: - When each attempt was made - Whether it
 * succeeded or failed - Error message if it failed - Duration of the attempt
 */
@Entity
@Table(name = "ps_hook_event_retry_attempt")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HookEventRetryAttempt extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private HookEventRecord eventRecord;

    /**
     * The attempt number (1-based: 1, 2, 3, ...)
     */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    /**
     * When this attempt was made.
     */
    @Column(name = "attempted_at", nullable = false)
    private LocalDateTime attemptedAt;

    /**
     * Whether this attempt was successful.
     */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /**
     * Error message if the attempt failed, null if successful.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Duration of the attempt in milliseconds.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Create a new retry attempt record.
     */
    public static HookEventRetryAttempt newAttempt(HookEventRecord eventRecord, Integer attemptNumber, LocalDateTime attemptedAt,
            boolean success, String errorMessage, Long durationMs) {
        HookEventRetryAttempt attempt = new HookEventRetryAttempt();
        attempt.setEventRecord(eventRecord);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setAttemptedAt(attemptedAt);
        attempt.setSuccess(success);
        attempt.setErrorMessage(errorMessage);
        attempt.setDurationMs(durationMs);
        return attempt;
    }
}
