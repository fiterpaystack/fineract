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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.infrastructure.config.PaystackEventProperties;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecord;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventRecordRepository;
import com.paystack.fineract.infrastructure.event.hook.domain.HookEventStatus;
import com.paystack.fineract.infrastructure.event.hook.metrics.PaystackKafkaEventMetrics;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/**
 * Unit tests for HookEventDLQService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HookEventDLQServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PaystackEventProperties eventProperties;

    @Mock
    private PaystackKafkaEventMetrics metrics;

    @Mock
    private HookEventRecordRepository eventRecordRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private HookEventDLQService dlqService;

    private HookEventRecord failedEvent;
    private PaystackEventProperties.KafkaProperties kafkaProperties;

    @BeforeEach
    void setUp() {
        failedEvent = createEventRecord(1L, "event-1", HookEventStatus.FAILED);
        kafkaProperties = new PaystackEventProperties.KafkaProperties();
        PaystackEventProperties.HookProperties hookProperties = new PaystackEventProperties.HookProperties();
        hookProperties.setDlqTopic("test-dlq-topic");
        kafkaProperties.setHook(hookProperties);
        when(eventProperties.getKafka()).thenReturn(kafkaProperties);
    }

    @Test
    void shouldSendEventToDLQ() throws Exception {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get()).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"dlq\"}");

        // When
        dlqService.sendToDLQ(failedEvent);

        // Then
        verify(kafkaTemplate).send(eq("test-dlq-topic"), anyString(), anyString());
        verify(eventRecordRepository).save(any(HookEventRecord.class));
        verify(metrics).recordDLQEvent(failedEvent.getEntityName(), failedEvent.getActionName(), failedEvent.getErrorMessage());
        assertThat(failedEvent.getStatus()).isEqualTo(HookEventStatus.DLQ);
    }

    @Test
    void shouldHandleDLQSendFailure() throws Exception {
        // Given
        HookEventStatus originalStatus = failedEvent.getStatus(); // Should be FAILED
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new RuntimeException("DLQ send failed"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\":\"dlq\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        // When
        dlqService.sendToDLQ(failedEvent);

        // Then
        // Should not throw exception, just log error
        verify(kafkaTemplate).send(anyString(), anyString(), anyString());
        // Implementation catches exception and logs, doesn't update status
        // Status should remain unchanged (original status, not DLQ) since exception is thrown
        assertThat(failedEvent.getStatus()).isEqualTo(originalStatus);
        // Verify save was NOT called (since exception occurred before status update)
        verify(eventRecordRepository, never()).save(any());
    }

    @Test
    void shouldIncludeEventDetailsInDLQMessage() throws Exception {
        // Given
        @SuppressWarnings("unchecked")
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        SendResult<String, String> sendResult = mock(SendResult.class);
        when(future.get()).thenReturn(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(eventRecordRepository.save(any(HookEventRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"eventId\":\"event-1\"}");
        when(objectMapper.readValue(anyString(), eq(Map.class))).thenReturn(new HashMap<>());

        // When
        dlqService.sendToDLQ(failedEvent);

        // Then
        verify(objectMapper).writeValueAsString(any());
        verify(kafkaTemplate).send(eq("test-dlq-topic"), anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }

    private HookEventRecord createEventRecord(Long id, String eventId, HookEventStatus status) {
        HookEventRecord eventRecord = new HookEventRecord();
        eventRecord.setId(id);
        eventRecord.setEventId(eventId);
        eventRecord.setStatus(status);
        eventRecord.setEntityName("CLIENT");
        eventRecord.setActionName("CREATE");
        eventRecord.setTopicName("test-topic");
        eventRecord.setPartitionKey("key-123");
        eventRecord.setPayload("{\"test\":\"data\"}");
        eventRecord.setRetryCount(3);
        eventRecord.setMaxRetries(3);
        eventRecord.setErrorMessage("Max retries exceeded");
        eventRecord.setCreatedAt(DateUtils.getAuditLocalDateTime());
        return eventRecord;
    }
}
