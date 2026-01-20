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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for HookEventWritePlatformServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class HookEventWritePlatformServiceImplTest {

    @Mock
    private HookEventReadPlatformService readPlatformService;

    @Mock
    private HookEventRetryService retryService;

    @Mock
    private HookEventRecordRepository eventRecordRepository;

    @InjectMocks
    private HookEventWritePlatformServiceImpl writePlatformService;

    private HookEventRecord failedEvent;
    private HookEventRecord pendingEvent;

    @BeforeEach
    void setUp() {
        failedEvent = createEventRecord(1L, "event-1", HookEventStatus.FAILED);
        pendingEvent = createEventRecord(2L, "event-2", HookEventStatus.PENDING);
    }

    @Test
    void shouldRetryFailedEvent() {
        // Given
        when(readPlatformService.retrieveOne("event-1")).thenReturn(failedEvent);

        // When
        HookEventRecord result = writePlatformService.retryEvent("event-1");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEventId()).isEqualTo("event-1");
        verify(retryService).retryEvent(failedEvent);
    }

    @Test
    void shouldThrowExceptionWhenRetryingNonFailedEvent() {
        // Given
        when(readPlatformService.retrieveOne("event-2")).thenReturn(pendingEvent);

        // When/Then
        assertThatThrownBy(() -> writePlatformService.retryEvent("event-2"))
                .isInstanceOf(PlatformApiDataValidationException.class);
        verify(retryService, never()).retryEvent(any());
    }

    @Test
    void shouldRetryAllFailedEvents() {
        // Given
        List<HookEventRecord> failedEvents = Arrays.asList(failedEvent, createEventRecord(3L, "event-3", HookEventStatus.FAILED));
        when(eventRecordRepository.findByStatus(HookEventStatus.FAILED)).thenReturn(failedEvents);

        // When
        HookEventWritePlatformService.RetryResult result = writePlatformService.retryAllFailed();

        // Then
        assertThat(result.totalEvents()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.failureCount()).isEqualTo(0);
        verify(retryService, org.mockito.Mockito.times(2)).retryEvent(any());
    }

    @Test
    void shouldReturnZeroCountsWhenNoFailedEvents() {
        // Given
        when(eventRecordRepository.findByStatus(HookEventStatus.FAILED)).thenReturn(Collections.emptyList());

        // When
        HookEventWritePlatformService.RetryResult result = writePlatformService.retryAllFailed();

        // Then
        assertThat(result.totalEvents()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.failureCount()).isEqualTo(0);
        verify(retryService, never()).retryEvent(any());
    }

    @Test
    void shouldHandlePartialFailuresInRetryAll() {
        // Given
        HookEventRecord event1 = createEventRecord(1L, "event-1", HookEventStatus.FAILED);
        HookEventRecord event2 = createEventRecord(2L, "event-2", HookEventStatus.FAILED);
        List<HookEventRecord> failedEvents = Arrays.asList(event1, event2);
        when(eventRecordRepository.findByStatus(HookEventStatus.FAILED)).thenReturn(failedEvents);
        doNothing().when(retryService).retryEvent(event1);
        doThrow(new RuntimeException("Retry failed")).when(retryService).retryEvent(event2);

        // When
        HookEventWritePlatformService.RetryResult result = writePlatformService.retryAllFailed();

        // Then
        assertThat(result.totalEvents()).isEqualTo(2);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
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
}
