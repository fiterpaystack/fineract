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
package com.paystack.fineract.infrastructure.event.external.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.producer.PaystackExternalEventProducer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.apache.fineract.infrastructure.event.external.exception.AcknowledgementTimeoutException;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class KafkaNotificationServiceTest {

    @Mock
    private KafkaNotificationRepository kafkaNotificationRepository;

    @Mock
    private PaystackExternalEventProducer paystackExternalEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Executor kafkaNotificationExecutor;

    @Mock
    private SavingsAccount savingsAccount;

    private KafkaNotificationService service;
    private KafkaNotification notification;

    @BeforeEach
    void setUp() {
        service = new KafkaNotificationService(kafkaNotificationRepository, paystackExternalEventProducer, objectMapper);

        // Set the executor using reflection for testing
        ReflectionTestUtils.setField(service, "kafkaNotificationExecutor", kafkaNotificationExecutor);

        // Set max retries to 3 for testing
        ReflectionTestUtils.setField(service, "maxRetries", 3);

        notification = new KafkaNotification("BLOCK_DEBIT", savingsAccount, "Test Limit", "Test Details");
        notification.setId(1L);
    }

    @Test
    void sendNotificationAsync_Success() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\": \"data\"}");

        // Mock executor to run tasks immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(kafkaNotificationExecutor).execute(any(Runnable.class));

        // When
        CompletableFuture<Void> future = service.sendNotificationAsync(notification);

        // Then
        future.join(); // Wait for completion
        verify(paystackExternalEventProducer).sendEvents(anyString());
        verify(kafkaNotificationRepository, times(1)).save(notification); // Once for success
    }

    @Test
    void sendNotificationAsync_JsonProcessingException() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON error") {});

        // Mock executor to run tasks immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(kafkaNotificationExecutor).execute(any(Runnable.class));

        // When
        CompletableFuture<Void> future = service.sendNotificationAsync(notification);

        // Then
        future.join(); // Wait for completion
        verify(paystackExternalEventProducer, never()).sendEvents(anyString());
        verify(kafkaNotificationRepository, times(2)).save(notification); // Once for retry increment, once for failure
    }

    @Test
    void sendNotificationAsync_AcknowledgementTimeoutException() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"test\": \"data\"}");
        doThrow(new AcknowledgementTimeoutException("Timeout", new RuntimeException("Test timeout")))
                .when(paystackExternalEventProducer).sendEvents(anyString());

        // Mock executor to run tasks immediately
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(kafkaNotificationExecutor).execute(any(Runnable.class));

        // When
        CompletableFuture<Void> future = service.sendNotificationAsync(notification);

        // Then
        future.join(); // Wait for completion
        verify(paystackExternalEventProducer, times(4)).sendEvents(anyString()); // Initial + 3 retries
        verify(kafkaNotificationRepository, times(5)).save(notification); // Retry increments + final failure
    }
}
