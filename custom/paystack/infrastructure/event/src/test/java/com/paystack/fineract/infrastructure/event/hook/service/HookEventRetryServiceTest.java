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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.infrastructure.config.PaystackEventProperties;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttempt;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRetryAttemptRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.tenant.TenantDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for HookEventRetryService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HookEventRetryServiceTest {

    @Mock
    private HookEventRecordRepository eventRecordRepository;

    @Mock
    private HookEventRetryAttemptRepository retryAttemptRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private HookEventDLQService dlqService;

    @Mock
    private PaystackKafkaEventMetrics metrics;

    @Mock
    private PaystackEventProperties eventProperties;

    @Mock
    private TenantDetailsService tenantDetailsService;

    private HookEventRetryService retryService;

    private HookEventRecord pendingEvent;

    @BeforeEach
    void setUp() {
        pendingEvent = createEventRecord(1L, "event-1", HookEventStatus.PENDING, 1);

        PaystackEventProperties.KafkaProperties kafkaProperties = new PaystackEventProperties.KafkaProperties();
        PaystackEventProperties.HookProperties hookProperties = new PaystackEventProperties.HookProperties();
        hookProperties.setMaxRetries(3);
        hookProperties.setRetryIntervalValue(5L);
        hookProperties.setRetryIntervalUnit(com.paystack.fineract.infrastructure.config.RetryIntervalUnit.MINUTE);
        kafkaProperties.setHook(hookProperties);
        when(eventProperties.getKafka()).thenReturn(kafkaProperties);

        // Manually construct service since @Qualifier doesn't work well with @InjectMocks
        retryService = new HookEventRetryService(eventRecordRepository, retryAttemptRepository, kafkaTemplate, dlqService, metrics,
                eventProperties, tenantDetailsService);
    }

    @Test
    void shouldRetryEventSuccessfully() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> {
            HookEventRecord eventRecord = invocation.getArgument(0);
            // Update the actual object state
            pendingEvent.setStatus(eventRecord.getStatus());
            pendingEvent.setRetryCount(eventRecord.getRetryCount());
            return eventRecord;
        });
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryEvent(pendingEvent);

        // Then
        verify(kafkaTemplate).send(eq(pendingEvent.getTopicName()), eq(pendingEvent.getPartitionKey()), eq(pendingEvent.getPayload()));
        verify(eventRecordRepository, org.mockito.Mockito.atLeastOnce()).save(any(HookEventRecord.class));
        verify(retryAttemptRepository).save(any());
        verify(metrics).recordEventRetry(pendingEvent.getEntityName(), pendingEvent.getActionName(), true, anyLong());
        verify(dlqService, never()).sendToDLQ(any());
    }

    @Test
    void shouldUpdateStatusToSentOnSuccess() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> {
            HookEventRecord eventRecord = invocation.getArgument(0);
            // Update the actual object state
            pendingEvent.setStatus(eventRecord.getStatus());
            pendingEvent.setRetryCount(eventRecord.getRetryCount());
            return eventRecord;
        });
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryEvent(pendingEvent);

        // Then
        // Verify save was called (at least twice: once for PENDING, once for SENT)
        verify(eventRecordRepository, org.mockito.Mockito.atLeastOnce()).save(any(HookEventRecord.class));
        verify(retryAttemptRepository).save(any());
        // Verify the final status is SENT
        assertThat(pendingEvent.getStatus()).isEqualTo(HookEventStatus.SENT);
    }

    @Test
    void shouldIncrementRetryCountOnFailure() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        int initialRetryCount = pendingEvent.getRetryCount();
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenThrow(new RuntimeException("Kafka error"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryEvent(pendingEvent);

        // Then
        // Verify retry count was incremented (was 1, now 2)
        assertThat(pendingEvent.getRetryCount()).isEqualTo(initialRetryCount + 1);
        verify(eventRecordRepository, org.mockito.Mockito.atLeastOnce()).save(any(HookEventRecord.class));
        verify(retryAttemptRepository).save(any());
        verify(metrics).recordEventRetry(pendingEvent.getEntityName(), pendingEvent.getActionName(), false, anyLong());
    }

    @Test
    void shouldSendToDLQWhenMaxRetriesExceeded() {
        // Given
        // Event with retryCount=3 and maxRetries=3 means it has already exceeded max retries
        HookEventRecord maxRetriesEvent = createEventRecord(3L, "event-3", HookEventStatus.PENDING, 3);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(dlqService).sendToDLQ(any());

        // When
        retryService.retryEvent(maxRetriesEvent);

        // Then
        // Should send to DLQ without attempting retry (since max retries already exceeded)
        verify(dlqService).sendToDLQ(maxRetriesEvent);
        verify(eventRecordRepository, org.mockito.Mockito.atLeastOnce()).save(any(HookEventRecord.class));
        // Should not attempt to send to Kafka since max retries already exceeded
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        // Should not record retry metrics since we're not actually retrying
        verify(metrics, never()).recordEventRetry(anyString(), anyString(), anyBoolean(), anyLong());
    }

    @Test
    void shouldRetryPendingEvents() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        FineractPlatformTenant tenant = createMockTenant("default");
        when(tenantDetailsService.findAllTenants()).thenReturn(Arrays.asList(tenant));
        HookEventRecord event2 = createEventRecord(4L, "event-4", HookEventStatus.PENDING, 0);
        List<HookEventRecord> pendingEvents = Arrays.asList(pendingEvent, event2);
        when(eventRecordRepository.findByStatusAndRetryCountLessThanMax(HookEventStatus.PENDING)).thenReturn(pendingEvents);
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> {
            HookEventRecord eventRecord = invocation.getArgument(0);
            // Update the actual object state
            if (eventRecord.getId().equals(pendingEvent.getId())) {
                pendingEvent.setStatus(eventRecord.getStatus());
                pendingEvent.setRetryCount(eventRecord.getRetryCount());
            } else if (eventRecord.getId().equals(event2.getId())) {
                event2.setStatus(eventRecord.getStatus());
                event2.setRetryCount(eventRecord.getRetryCount());
            }
            return eventRecord;
        });
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryPendingEvents();

        // Then
        verify(eventRecordRepository).findByStatusAndRetryCountLessThanMax(HookEventStatus.PENDING);
        // Should attempt to retry both events
        verify(kafkaTemplate, org.mockito.Mockito.times(2)).send(anyString(), anyString(), anyString());
        verify(retryAttemptRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void shouldRecordSuccessfulRetryAttempt() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryEvent(pendingEvent);

        // Then
        verify(retryAttemptRepository).save(argThat(attempt -> {
            HookEventRetryAttempt a = (HookEventRetryAttempt) attempt;
            return a.getEventRecord().getId().equals(pendingEvent.getId()) && a.getAttemptNumber().equals(2) // pendingEvent
                                                                                                             // starts
                                                                                                             // with
                                                                                                             // retryCount=1,
                                                                                                             // increments
                                                                                                             // to 2
                    && a.getSuccess() == true && a.getErrorMessage() == null && a.getDurationMs() != null;
        }));
    }

    @Test
    void shouldRecordFailedRetryAttempt() throws ExecutionException, InterruptedException, TimeoutException {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(5, java.util.concurrent.TimeUnit.SECONDS)).thenThrow(new RuntimeException("Kafka timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(retryAttemptRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        retryService.retryEvent(pendingEvent);

        // Then
        verify(retryAttemptRepository).save(argThat(attempt -> {
            HookEventRetryAttempt a = (HookEventRetryAttempt) attempt;
            return a.getEventRecord().getId().equals(pendingEvent.getId()) && a.getAttemptNumber().equals(2) // pendingEvent
                                                                                                             // starts
                                                                                                             // with
                                                                                                             // retryCount=1,
                                                                                                             // increments
                                                                                                             // to 2
                    && a.getSuccess() == false && a.getErrorMessage() != null && a.getErrorMessage().contains("Kafka timeout")
                    && a.getDurationMs() != null;
        }));
    }

    @Test
    void shouldNotRetryWhenNoPendingEvents() {
        // Given
        FineractPlatformTenant tenant = createMockTenant("default");
        when(tenantDetailsService.findAllTenants()).thenReturn(Arrays.asList(tenant));
        when(eventRecordRepository.findByStatusAndRetryCountLessThanMax(HookEventStatus.PENDING)).thenReturn(Collections.emptyList());

        // When
        retryService.retryPendingEvents();

        // Then
        verify(tenantDetailsService).findAllTenants();
        verify(eventRecordRepository).findByStatusAndRetryCountLessThanMax(HookEventStatus.PENDING);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(eventRecordRepository, never()).save(any());
    }

    private FineractPlatformTenant createMockTenant(String tenantIdentifier) {
        FineractPlatformTenant tenant = mock(FineractPlatformTenant.class);
        when(tenant.getTenantIdentifier()).thenReturn(tenantIdentifier);
        return tenant;
    }

    @SuppressWarnings("unchecked")
    private <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }

    private HookEventRecord createEventRecord(Long id, String eventId, HookEventStatus status, int retryCount) {
        HookEventRecord eventRecord = new HookEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId(eventId);
        eventRecord.setStatus(status);
        eventRecord.setEntityName("CLIENT");
        eventRecord.setActionName("CREATE");
        eventRecord.setTopicName("test-topic");
        eventRecord.setPartitionKey("key-123");
        eventRecord.setPayload("{\"test\":\"data\"}");
        eventRecord.setRetryCount(retryCount);
        eventRecord.setMaxRetries(3);
        eventRecord.setCreatedAt(DateUtils.getAuditLocalDateTime());
        return eventRecord;
    }
}
