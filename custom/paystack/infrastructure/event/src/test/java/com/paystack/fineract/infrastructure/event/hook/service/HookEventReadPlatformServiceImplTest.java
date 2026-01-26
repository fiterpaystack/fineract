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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.infrastructure.event.hook.data.HookEventData;
import com.paystack.fineract.infrastructure.event.hook.data.RetryAttemptData;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttemptRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.exception.HookEventNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for HookEventReadPlatformServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class HookEventReadPlatformServiceImplTest {

    @Mock
    private HookEventRecordRepository eventRecordRepository;

    @Mock
    private HookEventRetryAttemptRepository retryAttemptRepository;

    @InjectMocks
    private HookEventReadPlatformServiceImpl readPlatformService;

    private HookEventRecord event1;
    private HookEventRecord event2;
    private HookEventRecord event3;

    @BeforeEach
    void setUp() {
        event1 = createEventRecord(1L, "event-1", HookEventStatus.PENDING);
        event2 = createEventRecord(2L, "event-2", HookEventStatus.SENT);
        event3 = createEventRecord(3L, "event-3", HookEventStatus.FAILED);
    }

    @Test
    void shouldRetrieveOneEventById() {
        // Given
        when(eventRecordRepository.findByEventId("event-1")).thenReturn(Optional.of(event1));

        // When
        HookEventRecord result = readPlatformService.retrieveOne("event-1");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getEventId()).isEqualTo("event-1");
    }

    @Test
    void shouldThrowExceptionWhenEventNotFound() {
        // Given
        when(eventRecordRepository.findByEventId("non-existent")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> readPlatformService.retrieveOne("non-existent"))
                .isInstanceOf(HookEventNotFoundException.class);
    }

    @Test
    void shouldRetrieveAllEventsWhenStatusIsNull() {
        // Given
        List<HookEventRecord> allEvents = Arrays.asList(event1, event2, event3);
        Pageable pageable = PageRequest.of(0, 50);
        Page<HookEventRecord> page = new PageImpl<>(allEvents, pageable, allEvents.size());
        when(eventRecordRepository.findAll(pageable)).thenReturn(page);

        // When
        Page<HookEventData> result = readPlatformService.retrieveAll(null, pageable);

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getEventId()).isEqualTo("event-1");
        assertThat(result.getContent().get(1).getEventId()).isEqualTo("event-2");
        assertThat(result.getContent().get(2).getEventId()).isEqualTo("event-3");
        assertThat(result.getTotalElements()).isEqualTo(3);
    }

    @Test
    void shouldRetrieveEventsByStatus() {
        // Given
        List<HookEventRecord> failedEvents = Arrays.asList(event3);
        Pageable pageable = PageRequest.of(0, 50);
        Page<HookEventRecord> page = new PageImpl<>(failedEvents, pageable, failedEvents.size());
        when(eventRecordRepository.findByStatus(HookEventStatus.FAILED, pageable)).thenReturn(page);

        // When
        Page<HookEventData> result = readPlatformService.retrieveAll(HookEventStatus.FAILED, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEventId()).isEqualTo("event-3");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(HookEventStatus.FAILED);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldValidateAndParseValidStatus() {
        // When
        HookEventStatus result = readPlatformService.validateAndParseStatus("PENDING");

        // Then
        assertThat(result).isEqualTo(HookEventStatus.PENDING);
    }

    @Test
    void shouldValidateAndParseStatusCaseInsensitive() {
        // When
        HookEventStatus result = readPlatformService.validateAndParseStatus("pending");

        // Then
        assertThat(result).isEqualTo(HookEventStatus.PENDING);
    }

    @Test
    void shouldReturnNullForNullStatus() {
        // When
        HookEventStatus result = readPlatformService.validateAndParseStatus(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNullForBlankStatus() {
        // When
        HookEventStatus result = readPlatformService.validateAndParseStatus("   ");

        // Then
        assertThat(result).isNull();
    }

    @Test
    void shouldThrowExceptionForInvalidStatus() {
        // When/Then
        assertThatThrownBy(() -> readPlatformService.validateAndParseStatus("INVALID")).isInstanceOf(UnrecognizedQueryParamException.class);
    }

    @Test
    void shouldRetrieveRetryAttemptsForEvent() {
        // Given
        when(eventRecordRepository.findByEventId("event-1")).thenReturn(Optional.of(event1));
        HookEventRetryAttempt attempt1 = createRetryAttempt(1L, event1, 1, true, null, 100L);
        HookEventRetryAttempt attempt2 = createRetryAttempt(2L, event1, 2, false, "Timeout error", 5000L);
        List<HookEventRetryAttempt> attempts = Arrays.asList(attempt1, attempt2);
        when(retryAttemptRepository.findByEventIdStringOrderByAttemptNumber("event-1")).thenReturn(attempts);

        // When
        List<RetryAttemptData> result = readPlatformService.retrieveRetryAttempts("event-1");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAttemptNumber()).isEqualTo(1);
        assertThat(result.get(0).getSuccess()).isTrue();
        assertThat(result.get(0).getErrorMessage()).isNull();
        assertThat(result.get(0).getDurationMs()).isEqualTo(100L);
        assertThat(result.get(1).getAttemptNumber()).isEqualTo(2);
        assertThat(result.get(1).getSuccess()).isFalse();
        assertThat(result.get(1).getErrorMessage()).isEqualTo("Timeout error");
        assertThat(result.get(1).getDurationMs()).isEqualTo(5000L);
    }

    @Test
    void shouldReturnEmptyListWhenNoRetryAttempts() {
        // Given
        when(eventRecordRepository.findByEventId("event-1")).thenReturn(Optional.of(event1));
        when(retryAttemptRepository.findByEventIdStringOrderByAttemptNumber("event-1")).thenReturn(List.of());

        // When
        List<RetryAttemptData> result = readPlatformService.retrieveRetryAttempts("event-1");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldThrowExceptionWhenEventNotFoundForRetryAttempts() {
        // Given
        when(eventRecordRepository.findByEventId("non-existent")).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> readPlatformService.retrieveRetryAttempts("non-existent"))
                .isInstanceOf(HookEventNotFoundException.class);
        verify(retryAttemptRepository, never()).findByEventIdStringOrderByAttemptNumber(anyString());
    }

    private HookEventRecord createEventRecord(Long id, String eventId, HookEventStatus status) {
        HookEventRecord eventRecord = new HookEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId(eventId);
        eventRecord.setStatus(status);
        eventRecord.setEntityName("CLIENT");
        eventRecord.setActionName("CREATE");
        eventRecord.setTopicName("test-topic");
        eventRecord.setRetryCount(0);
        eventRecord.setMaxRetries(3);
        eventRecord.setCreatedAt(DateUtils.getAuditLocalDateTime());
        return eventRecord;
    }

    private HookEventRetryAttempt createRetryAttempt(Long id, HookEventRecord hookEventRecord, Integer attemptNumber, boolean success,
            String errorMessage, Long durationMs) {
        HookEventRetryAttempt retryAttempt = HookEventRetryAttempt.newAttempt(hookEventRecord, attemptNumber,
                DateUtils.getAuditLocalDateTime(), success, errorMessage, durationMs);
        retryAttempt.setId(id);
        return retryAttempt;
    }
}
