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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import com.paystack.fineract.infrastructure.event.external.exception.KafkaNotificationInvalidStatusException;
import com.paystack.fineract.infrastructure.event.external.exception.KafkaNotificationNotFoundException;
import com.paystack.fineract.infrastructure.event.external.producer.PaystackExternalEventProducer;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for KafkaNotificationWritePlatformServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class KafkaNotificationWritePlatformServiceImplTest {

    @Mock
    private KafkaNotificationRepository kafkaNotificationRepository;

    @Mock
    private PaystackExternalEventProducer paystackExternalEventProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private KafkaNotificationWritePlatformServiceImpl service;

    private KafkaNotification failedNotification;
    private KafkaNotification sentNotification;
    private KafkaNotification pendingNotification;
    private SavingsAccount savingsAccount;
    private JsonCommand jsonCommand;

    @BeforeEach
    void setUp() throws NoSuchFieldException, IllegalAccessException {
        // Create mock savings account
        savingsAccount = mock(SavingsAccount.class);

        // Inject optional producer into service
        Field field = KafkaNotificationWritePlatformServiceImpl.class.getDeclaredField("paystackExternalEventProducer");
        field.setAccessible(true);
        field.set(service, paystackExternalEventProducer);

        // Create test notifications
        failedNotification = new KafkaNotification("DEPOSIT", savingsAccount, "Test reason", "Test details");
        failedNotification.setId(1L);
        failedNotification.setStatus(KafkaNotificationStatus.FAILED);
        failedNotification.setCreatedDate(LocalDateTime.now().minusHours(1));
        failedNotification.setErrorMessage("Previous error");

        sentNotification = new KafkaNotification("WITHDRAWAL", savingsAccount, "Test reason 2", "Test details 2");
        sentNotification.setId(2L);
        sentNotification.setStatus(KafkaNotificationStatus.SENT);
        sentNotification.setCreatedDate(LocalDateTime.now().minusMinutes(30));

        pendingNotification = new KafkaNotification("TRANSFER", savingsAccount, "Test reason 3", "Test details 3");
        pendingNotification.setId(3L);
        pendingNotification.setStatus(KafkaNotificationStatus.PENDING);
        pendingNotification.setCreatedDate(LocalDateTime.now().minusMinutes(15));

        // Create mock JsonCommand
        jsonCommand = mock(JsonCommand.class);
    }

    @Test
    void testRetryFailedNotification_WhenNotificationExistsAndIsFailed_SuccessfullyRetries() throws JsonProcessingException {
        // Given
        when(jsonCommand.commandId()).thenReturn(1L);
        when(objectMapper.writeValueAsString(any(KafkaNotificationDTO.class))).thenReturn("{\"id\":1,\"eventType\":\"DEPOSIT\"}");
        when(kafkaNotificationRepository.findById(1L))
                .thenReturn(Optional.of(failedNotification));
        when(kafkaNotificationRepository.save(any(KafkaNotification.class)))
                .thenReturn(failedNotification);

        // When
        CommandProcessingResult result = service.retryFailedNotification(1L, jsonCommand);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getCommandId());
        assertEquals(1L, result.getResourceId());

        // Verify notification status was reset
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
        verify(paystackExternalEventProducer).sendEvents(anyString());
    }

    @Test
    void testRetryFailedNotification_WhenNotificationDoesNotExist_ThrowsException() {
        // Given
        when(kafkaNotificationRepository.findById(999L))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(KafkaNotificationNotFoundException.class,
                () -> service.retryFailedNotification(999L, jsonCommand));

        verify(kafkaNotificationRepository).findById(999L);
        verifyNoInteractions(paystackExternalEventProducer);
    }

    @Test
    void testRetryFailedNotification_WhenNotificationIsNotFailed_ThrowsException() {
        // Given
        when(kafkaNotificationRepository.findById(2L))
                .thenReturn(Optional.of(sentNotification));

        // When & Then
        assertThrows(KafkaNotificationInvalidStatusException.class,
                () -> service.retryFailedNotification(2L, jsonCommand));

        verify(kafkaNotificationRepository).findById(2L);
        verifyNoInteractions(paystackExternalEventProducer);
    }

    @Test
    void testRetryFailedNotification_WhenKafkaSendFails_MarksAsFailed() throws JsonProcessingException {
        // Given
        when(kafkaNotificationRepository.findById(1L))
                .thenReturn(Optional.of(failedNotification));
        when(kafkaNotificationRepository.save(any(KafkaNotification.class)))
                .thenReturn(failedNotification);
        doThrow(new RuntimeException("Kafka send failed"))
                .when(paystackExternalEventProducer).sendEvents(anyString());

        // When & Then
        assertThrows(RuntimeException.class,
                () -> service.retryFailedNotification(1L, jsonCommand));

        // Verify notification was marked as failed
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
        assertEquals(KafkaNotificationStatus.FAILED, failedNotification.getStatus());
        assertTrue(failedNotification.getErrorMessage().contains("Retry failed"));
    }

    @Test
    void testRetryFailedNotificationsByAccountId_WhenNotificationsExist_SuccessfullyRetries() throws JsonProcessingException {
        // Given
        List<KafkaNotification> failedNotifications = Arrays.asList(failedNotification);
        when(kafkaNotificationRepository.findAll(any(Specification.class))).thenReturn(failedNotifications);
        when(kafkaNotificationRepository.save(any(KafkaNotification.class))).thenReturn(failedNotification);

        // When
        CommandProcessingResult result = service.retryFailedNotificationsByAccountId(1L, jsonCommand);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getResourceId());

        verify(kafkaNotificationRepository).findAll(any(Specification.class));
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
    }

    @Test
    void testRetryFailedNotificationsByAccountId_WhenNoNotificationsExist_ReturnsSuccess() {
        // Given
        when(kafkaNotificationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        CommandProcessingResult result = service.retryFailedNotificationsByAccountId(1L, jsonCommand);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getResourceId());

        verify(kafkaNotificationRepository).findAll(any(Specification.class));
        verifyNoInteractions(paystackExternalEventProducer);
    }

    @Test
    void testRetryFailedNotificationsByAccountId_WhenSomeRetriesFail_HandlesPartialSuccess() throws JsonProcessingException {
        // Given
        KafkaNotification anotherFailedNotification = new KafkaNotification("TRANSFER", savingsAccount, "Test reason", "Test details");
        anotherFailedNotification.setId(4L);
        anotherFailedNotification.setStatus(KafkaNotificationStatus.FAILED);
        anotherFailedNotification.setCreatedDate(LocalDateTime.now().minusMinutes(10));

        List<KafkaNotification> failedNotifications = Arrays.asList(failedNotification, anotherFailedNotification);
        when(kafkaNotificationRepository.findAll(any(Specification.class))).thenReturn(failedNotifications);
        when(kafkaNotificationRepository.save(any(KafkaNotification.class))).thenReturn(failedNotification, anotherFailedNotification);

        // Mock first notification succeeds, second fails
        doNothing().doThrow(new RuntimeException("Kafka send failed")).when(paystackExternalEventProducer).sendEvents(anyString());

        // When
        CommandProcessingResult result = service.retryFailedNotificationsByAccountId(1L, jsonCommand);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getResourceId());

        verify(kafkaNotificationRepository).findAll(any(Specification.class));
        verify(kafkaNotificationRepository, times(4)).save(any(KafkaNotification.class)); // 2 saves per notification
    }

    @Test
    void testRetryAllFailedNotifications_WhenNotificationsExist_SuccessfullyRetries() throws JsonProcessingException {
        // Given
        List<KafkaNotification> failedNotifications = Arrays.asList(failedNotification);
        when(kafkaNotificationRepository.findAll(any(Specification.class))).thenReturn(failedNotifications);
        when(kafkaNotificationRepository.save(any(KafkaNotification.class))).thenReturn(failedNotification);

        // When
        CommandProcessingResult result = service.retryAllFailedNotifications(jsonCommand);

        // Then
        assertNotNull(result);

        verify(kafkaNotificationRepository).findAll(any(Specification.class));
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
    }

    @Test
    void testRetryAllFailedNotifications_WhenNoNotificationsExist_ReturnsSuccess() {
        // Given
        when(kafkaNotificationRepository.findAll(any(Specification.class)))
                .thenReturn(Collections.emptyList());

        // When
        CommandProcessingResult result = service.retryAllFailedNotifications(jsonCommand);

        // Then
        assertNotNull(result);

        verify(kafkaNotificationRepository).findAll(any(Specification.class));
        verifyNoInteractions(paystackExternalEventProducer);
    }

    @Test
    void testRetryAllFailedNotifications_WhenJsonSerializationFails_MarksAsFailed() throws JsonProcessingException {
        // Given
        List<KafkaNotification> failedNotifications = Arrays.asList(failedNotification);
        when(kafkaNotificationRepository.findAll(any(Specification.class))).thenReturn(failedNotifications);
        when(kafkaNotificationRepository.save(any(KafkaNotification.class))).thenReturn(failedNotification);
        when(objectMapper.writeValueAsString(any(KafkaNotificationDTO.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        // When & Then
        service.retryAllFailedNotifications(jsonCommand);

        // Verify notification was marked as failed
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
        assertEquals(KafkaNotificationStatus.FAILED, failedNotification.getStatus());
        assertTrue(failedNotification.getErrorMessage().contains("Retry failed"));
    }

    @Test
    void testRetryFailedNotification_WhenJsonSerializationFails_MarksAsFailed() throws JsonProcessingException {
        // Given
        when(kafkaNotificationRepository.findById(1L))
                .thenReturn(Optional.of(failedNotification));
        when(kafkaNotificationRepository.save(any(KafkaNotification.class)))
                .thenReturn(failedNotification);
        when(objectMapper.writeValueAsString(any(KafkaNotificationDTO.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        // When & Then
        assertThrows(RuntimeException.class,
                () -> service.retryFailedNotification(1L, jsonCommand));

        // Verify notification was marked as failed
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
        assertEquals(KafkaNotificationStatus.FAILED, failedNotification.getStatus());
        assertTrue(failedNotification.getErrorMessage().contains("Retry failed"));
    }

    @Test
    void testRetryFailedNotificationsByAccountId_WhenJsonSerializationFails_MarksAsFailed() throws JsonProcessingException {
        // Given
        List<KafkaNotification> failedNotifications = Arrays.asList(failedNotification);
        when(kafkaNotificationRepository.findAll(any(Specification.class))).thenReturn(failedNotifications);
        when(kafkaNotificationRepository.save(any(KafkaNotification.class))).thenReturn(failedNotification);
        when(objectMapper.writeValueAsString(any(KafkaNotificationDTO.class)))
                .thenThrow(new JsonProcessingException("Serialization failed") {});

        // When & Then
        service.retryFailedNotificationsByAccountId(1L, jsonCommand);

        // Verify notification was marked as failed
        verify(kafkaNotificationRepository, times(2)).save(failedNotification);
        assertEquals(KafkaNotificationStatus.FAILED, failedNotification.getStatus());
        assertTrue(failedNotification.getErrorMessage().contains("Retry failed"));
    }

    @Test
    void testRetryFailedNotification_WhenNotificationIsAlreadyPending_ThrowsException() {
        // Given
        when(kafkaNotificationRepository.findById(3L))
                .thenReturn(Optional.of(pendingNotification));

        // When & Then
        assertThrows(KafkaNotificationInvalidStatusException.class,
                () -> service.retryFailedNotification(3L, jsonCommand));

        verify(kafkaNotificationRepository).findById(3L);
        verifyNoInteractions(paystackExternalEventProducer);
    }

    @Test
    void testRetryFailedNotification_WhenNotificationIsAlreadySent_ThrowsException() {
        // Given
        when(kafkaNotificationRepository.findById(2L))
                .thenReturn(Optional.of(sentNotification));

        // When & Then
        assertThrows(KafkaNotificationInvalidStatusException.class,
                () -> service.retryFailedNotification(2L, jsonCommand));

        verify(kafkaNotificationRepository).findById(2L);
        verifyNoInteractions(paystackExternalEventProducer);
    }
}
