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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests for KafkaNotificationReadPlatformServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaNotificationReadPlatformServiceImplTest {

    @Mock
    private KafkaNotificationRepository kafkaNotificationRepository;

    @InjectMocks
    private KafkaNotificationReadPlatformServiceImpl service;

    private KafkaNotification notification1;
    private KafkaNotification notification2;
    private SavingsAccount savingsAccount;

    @BeforeEach
    void setUp() {
        // Create mock client
        Client mockClient = mock(Client.class);
        when(mockClient.getId()).thenReturn(1L);
        when(mockClient.getDisplayName()).thenReturn("John Doe");

        // Create mock savings product
        SavingsProduct mockProduct = mock(SavingsProduct.class);
        when(mockProduct.getName()).thenReturn("Basic Savings Account");

        // Create mock savings account
        savingsAccount = mock(SavingsAccount.class);
        when(savingsAccount.getId()).thenReturn(1L);
        when(savingsAccount.getClient()).thenReturn(mockClient);
        when(savingsAccount.savingsProduct()).thenReturn(mockProduct);
        when(savingsAccount.getAccountNumber()).thenReturn("SAV001234567");

        // Create test notifications
        notification1 = new KafkaNotification("DEPOSIT", savingsAccount, "Test reason", "Test details");
        notification1.setId(1L);
        notification1.setStatus(KafkaNotificationStatus.SENT);
        notification1.setCreatedDate(LocalDateTime.now().minusHours(1));

        notification2 = new KafkaNotification("WITHDRAWAL", savingsAccount, "Test reason 2", "Test details 2");
        notification2.setId(2L);
        notification2.setStatus(KafkaNotificationStatus.FAILED);
        notification2.setCreatedDate(LocalDateTime.now().minusMinutes(30));
    }

    @Test
    void testSearchNotifications_WithNoFilters_ReturnsAllNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1, notification2);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(null, null, null, null, null, null, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getPageItems().size());
        assertEquals(2, result.getTotalFilteredRecords());

        // Verify new fields are populated in search results
        KafkaNotificationDTO firstNotification = result.getPageItems().get(0);
        assertEquals("John Doe", firstNotification.getClientName());
        assertEquals("Basic Savings Account", firstNotification.getAccountProductName());
        assertEquals("SAV001234567", firstNotification.getAccountNumber());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testSearchNotifications_WithCustomerIdFilter_ReturnsFilteredNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(123L, null, null, null, null, null, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testSearchNotifications_WithEventTypeFilter_ReturnsFilteredNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(null, "DEPOSIT", null, null, null, null, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testSearchNotifications_WithStatusFilter_ReturnsFilteredNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification2);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(null, null, null, KafkaNotificationStatus.FAILED, null, null, 50,
                0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testSearchNotifications_WithDateRangeFilter_ReturnsFilteredNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);
        LocalDateTime fromDate = LocalDateTime.now().minusHours(2);
        LocalDateTime toDate = LocalDateTime.now();

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(null, null, null, null, fromDate, toDate, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testSearchNotifications_WithPagination_ReturnsCorrectPage() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.searchNotifications(null, null, null, null, null, null, 10, 5);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testGetNotificationById_WhenNotificationExists_ReturnsNotificationDTO() {
        // Given
        when(kafkaNotificationRepository.findById(1L))
                .thenReturn(Optional.of(notification1));

        // When
        KafkaNotificationDTO result = service.getNotificationById(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("DEPOSIT", result.getEventType());
        assertEquals(1L, result.getAccountId());

        // Verify new fields are populated
        assertEquals("John Doe", result.getClientName());
        assertEquals("Basic Savings Account", result.getAccountProductName());
        assertEquals("SAV001234567", result.getAccountNumber());

        verify(kafkaNotificationRepository).findById(1L);
    }

    @Test
    void testGetNotificationById_WhenNotificationDoesNotExist_ReturnsNull() {
        // Given
        when(kafkaNotificationRepository.findById(999L))
                .thenReturn(Optional.empty());

        // When
        KafkaNotificationDTO result = service.getNotificationById(999L);

        // Then
        assertNull(result);

        verify(kafkaNotificationRepository).findById(999L);
    }

    @Test
    void testGetNotificationsByAccount_ReturnsAccountNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification1, notification2);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.getNotificationsByAccount(1L, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(2, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testGetNotificationsByStatus_ReturnsStatusNotifications() {
        // Given
        List<KafkaNotification> notifications = Arrays.asList(notification2);
        PageImpl<KafkaNotification> page = new PageImpl<>(notifications);

        when(kafkaNotificationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        // When
        Page<KafkaNotificationDTO> result = service.getNotificationsByStatus(KafkaNotificationStatus.FAILED, 50, 0);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getPageItems().size());

        verify(kafkaNotificationRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void testValidatePaginationParameters_WithValidParameters_DoesNotThrowException() {
        // When & Then
        assertDoesNotThrow(() -> service.validatePaginationParameters(50, 0));
        assertDoesNotThrow(() -> service.validatePaginationParameters(100, 10));
        assertDoesNotThrow(() -> service.validatePaginationParameters(null, null));
    }

    @Test
    void testValidatePaginationParameters_WithInvalidLimit_ThrowsException() {
        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> service.validatePaginationParameters(0, 0));
        assertThrows(PlatformApiDataValidationException.class, () -> service.validatePaginationParameters(-1, 0));
        assertThrows(PlatformApiDataValidationException.class, () -> service.validatePaginationParameters(1001, 0));
    }

    @Test
    void testValidatePaginationParameters_WithInvalidOffset_ThrowsException() {
        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> service.validatePaginationParameters(50, -1));
    }

    @Test
    void testValidateAndParseStatus_WithValidStatus_ReturnsStatus() {
        // When & Then
        assertEquals(KafkaNotificationStatus.PENDING, service.validateAndParseStatus("PENDING"));
        assertEquals(KafkaNotificationStatus.SENT, service.validateAndParseStatus("SENT"));
        assertEquals(KafkaNotificationStatus.FAILED, service.validateAndParseStatus("FAILED"));
        assertEquals(KafkaNotificationStatus.PENDING, service.validateAndParseStatus("pending"));
        assertEquals(KafkaNotificationStatus.SENT, service.validateAndParseStatus("sent"));
        assertEquals(KafkaNotificationStatus.FAILED, service.validateAndParseStatus("failed"));
    }

    @Test
    void testValidateAndParseStatus_WithNullStatus_ReturnsNull() {
        // When & Then
        assertNull(service.validateAndParseStatus(null));
        assertNull(service.validateAndParseStatus(""));
        assertNull(service.validateAndParseStatus("   "));
    }

    @Test
    void testValidateAndParseStatus_WithInvalidStatus_ThrowsException() {
        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> service.validateAndParseStatus("INVALID"));
        assertThrows(PlatformApiDataValidationException.class, () -> service.validateAndParseStatus("UNKNOWN"));
    }

    @Test
    void testValidateNotificationExists_WhenNotificationExists_DoesNotThrowException() {
        // Given
        when(kafkaNotificationRepository.existsById(1L)).thenReturn(true);

        // When & Then
        assertDoesNotThrow(() -> service.validateNotificationExists(1L));

        verify(kafkaNotificationRepository).existsById(1L);
    }

    @Test
    void testValidateNotificationExists_WhenNotificationDoesNotExist_ThrowsException() {
        // Given
        when(kafkaNotificationRepository.existsById(999L)).thenReturn(false);

        // When & Then
        assertThrows(PlatformApiDataValidationException.class,
                () -> service.validateNotificationExists(999L));

        verify(kafkaNotificationRepository).existsById(999L);
    }

    @Test
    void testValidateNotificationExists_WithNullId_ThrowsException() {
        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> service.validateNotificationExists(null));
    }
}
