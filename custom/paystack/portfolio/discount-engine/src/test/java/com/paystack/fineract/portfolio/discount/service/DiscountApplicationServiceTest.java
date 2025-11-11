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
package com.paystack.fineract.portfolio.discount.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.domain.DiscountApplication;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.repository.DiscountApplicationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiscountApplicationService Tests")
class DiscountApplicationServiceTest {

    @Mock
    private DiscountApplicationRepository discountApplicationRepository;

    @Mock
    private DiscountConditionFormatter conditionFormatter;

    @Mock
    private SavingsAccountRepository savingsAccountRepository;

    @Mock
    private ChargeRepository chargeRepository;

    private DiscountApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DiscountApplicationService(discountApplicationRepository, conditionFormatter, savingsAccountRepository,
                chargeRepository);
    }

    @Nested
    @DisplayName("Save Discount Application Tests")
    class SaveDiscountApplicationTests {

        @Test
        @DisplayName("Should save discount application with all fields populated")
        void shouldSaveDiscountApplicationWithAllFields() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "PERCENTAGE");
            DiscountContext context = createDiscountContext(100L, 50L, 200L);
            Charge charge = createCharge(50L, "Transfer Fee", false, 5);

            SavingsAccount account = createSavingsAccount(200L, "ACC001", 300L);
            when(savingsAccountRepository.findById(200L)).thenReturn(Optional.of(account));
            // Charge is passed directly, so we don't need to mock repository lookup
            // Mock the formatter to return the expected string
            when(conditionFormatter.formatConditions(eq("PERCENTAGE"), any()))
                    .thenReturn("Percentage: 10%");

            ArgumentCaptor<DiscountApplication> captor = ArgumentCaptor.forClass(DiscountApplication.class);
            when(discountApplicationRepository.save(any(DiscountApplication.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("10.00"), context, charge);

            // Then
            verify(discountApplicationRepository).save(captor.capture());
            DiscountApplication saved = captor.getValue();

            assertThat(saved.getDiscountRuleId()).isEqualTo(1L);
            assertThat(saved.getEntityType()).isEqualTo("SAVINGS_PRODUCT");
            assertThat(saved.getEntityId()).isEqualTo(100L);
            assertThat(saved.getChargeId()).isEqualTo(50L);
            assertThat(saved.getOriginalAmount()).isEqualByComparingTo("100.00");
            assertThat(saved.getDiscountAmount()).isEqualByComparingTo("10.00");
            assertThat(saved.getFinalAmount()).isEqualByComparingTo("90.00");
            assertThat(saved.getAccountNumber()).isEqualTo("ACC001");
            assertThat(saved.getCustomerId()).isEqualTo(300L);
            assertThat(saved.getDiscountType()).isEqualTo("PERCENTAGE");
            assertThat(saved.getFeeTypeCategory()).isEqualTo("TRANSFER");
            assertThat(saved.getTriggeredConditions()).isEqualTo("Percentage: 10%");
        }

        @Test
        @DisplayName("Should fetch charge if not provided")
        void shouldFetchChargeIfNotProvided() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "FLAT");
            DiscountContext context = createDiscountContext(100L, 50L, 200L);
            Charge charge = createCharge(50L, "Maintenance Fee", false, 7);

            SavingsAccount account = createSavingsAccount(200L, "ACC002", 300L);
            when(savingsAccountRepository.findById(200L)).thenReturn(Optional.of(account));
            when(chargeRepository.findById(50L)).thenReturn(Optional.of(charge));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = any(Map.class);
            when(conditionFormatter.formatConditions(anyString(), params)).thenReturn("Flat discount: 50");

            // When
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("50.00"), context, null);

            // Then
            verify(chargeRepository).findById(50L);
            verify(discountApplicationRepository).save(any(DiscountApplication.class));
        }

        @Test
        @DisplayName("Should handle penalty charge categorization")
        void shouldHandlePenaltyChargeCategorization() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "PERCENTAGE");
            DiscountContext context = createDiscountContext(100L, 50L, 200L);
            Charge charge = createCharge(50L, "Late Payment Penalty", true, 5); // isPenalty = true

            SavingsAccount account = createSavingsAccount(200L, "ACC003", 300L);
            when(savingsAccountRepository.findById(200L)).thenReturn(Optional.of(account));
            when(chargeRepository.findById(50L)).thenReturn(Optional.of(charge));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = any(Map.class);
            when(conditionFormatter.formatConditions(anyString(), params)).thenReturn("Percentage: 5%");

            ArgumentCaptor<DiscountApplication> captor = ArgumentCaptor.forClass(DiscountApplication.class);
            when(discountApplicationRepository.save(any(DiscountApplication.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("5.00"), context, charge);

            // Then
            verify(discountApplicationRepository).save(captor.capture());
            DiscountApplication saved = captor.getValue();
            assertThat(saved.getFeeTypeCategory()).isEqualTo("PENALTY");
        }

        @Test
        @DisplayName("Should handle missing account gracefully")
        void shouldHandleMissingAccountGracefully() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "PERCENTAGE");
            DiscountContext context = createDiscountContext(100L, 50L, 999L); // Non-existent account
            Charge charge = createCharge(50L, "Test Charge", false, 5);

            when(savingsAccountRepository.findById(999L)).thenReturn(Optional.empty());
            when(chargeRepository.findById(50L)).thenReturn(Optional.of(charge));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = any(Map.class);
            when(conditionFormatter.formatConditions(anyString(), params)).thenReturn("Percentage: 10%");

            ArgumentCaptor<DiscountApplication> captor = ArgumentCaptor.forClass(DiscountApplication.class);
            when(discountApplicationRepository.save(any(DiscountApplication.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("10.00"), context, charge);

            // Then
            verify(discountApplicationRepository).save(captor.capture());
            DiscountApplication saved = captor.getValue();
            assertThat(saved.getAccountNumber()).isNull();
            assertThat(saved.getCustomerId()).isNull();
        }

        @Test
        @DisplayName("Should not save if charge not found")
        void shouldNotSaveIfChargeNotFound() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "PERCENTAGE");
            DiscountContext context = createDiscountContext(100L, 50L, 200L);

            when(chargeRepository.findById(50L)).thenReturn(Optional.empty());

            // When
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("10.00"), context, null);

            // Then
            verify(discountApplicationRepository, never()).save(any(DiscountApplication.class));
        }

        @Test
        @DisplayName("Should handle exception during save gracefully")
        void shouldHandleExceptionDuringSaveGracefully() {
            // Given
            DiscountRule rule = createDiscountRule(1L, "Test Rule", "PERCENTAGE");
            DiscountContext context = createDiscountContext(100L, 50L, 200L);
            Charge charge = createCharge(50L, "Test Charge", false, 5);

            SavingsAccount account = createSavingsAccount(200L, "ACC004", 300L);
            when(savingsAccountRepository.findById(200L)).thenReturn(Optional.of(account));
            when(chargeRepository.findById(50L)).thenReturn(Optional.of(charge));
            @SuppressWarnings("unchecked")
            Map<String, Object> params = any(Map.class);
            when(conditionFormatter.formatConditions(anyString(), params)).thenReturn("Percentage: 10%");
            when(discountApplicationRepository.save(any(DiscountApplication.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then - Should not throw exception
            service.saveDiscountApplication(rule, "SAVINGS_PRODUCT", 100L, 50L, new BigDecimal("100.00"),
                    new BigDecimal("10.00"), context, charge);

            verify(discountApplicationRepository).save(any(DiscountApplication.class));
        }
    }

    @Nested
    @DisplayName("Update Transaction ID Tests")
    class UpdateTransactionIdTests {

        @Test
        @DisplayName("Should update transaction ID for matching discount application")
        void shouldUpdateTransactionIdForMatchingApplication() {
            // Given
            Long chargeId = 50L;
            Long accountId = 200L;
            Long transactionId = 500L;

            DiscountApplication application = DiscountApplication.createNew(1L, "SAVINGS_PRODUCT", accountId, chargeId,
                    new BigDecimal("100.00"), new BigDecimal("10.00"));
            application.setAccountNumber("ACC005");
            application.setTransactionId(null); // Initially null

            List<DiscountApplication> applications = new ArrayList<>();
            applications.add(application);

            when(discountApplicationRepository.findByChargeId(chargeId)).thenReturn(applications);
            when(discountApplicationRepository.save(any(DiscountApplication.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            service.updateTransactionId(chargeId, accountId, transactionId);

            // Then
            verify(discountApplicationRepository).save(application);
            assertThat(application.getTransactionId()).isEqualTo(transactionId);
        }

        @Test
        @DisplayName("Should not update if transaction ID already set")
        void shouldNotUpdateIfTransactionIdAlreadySet() {
            // Given
            Long chargeId = 50L;
            Long accountId = 200L;
            Long transactionId = 500L;
            Long existingTransactionId = 400L;

            DiscountApplication application = DiscountApplication.createNew(1L, "SAVINGS_PRODUCT", accountId, chargeId,
                    new BigDecimal("100.00"), new BigDecimal("10.00"));
            application.setTransactionId(existingTransactionId); // Already set

            List<DiscountApplication> applications = new ArrayList<>();
            applications.add(application);

            when(discountApplicationRepository.findByChargeId(chargeId)).thenReturn(applications);

            // When
            service.updateTransactionId(chargeId, accountId, transactionId);

            // Then
            verify(discountApplicationRepository, never()).save(any(DiscountApplication.class));
            assertThat(application.getTransactionId()).isEqualTo(existingTransactionId);
        }

        @Test
        @DisplayName("Should handle exception during update gracefully")
        void shouldHandleExceptionDuringUpdateGracefully() {
            // Given
            Long chargeId = 50L;
            Long accountId = 200L;
            Long transactionId = 500L;

            when(discountApplicationRepository.findByChargeId(chargeId))
                    .thenThrow(new RuntimeException("Database error"));

            // When & Then - Should not throw exception
            service.updateTransactionId(chargeId, accountId, transactionId);

            verify(discountApplicationRepository).findByChargeId(chargeId);
        }
    }

    // Helper methods
    private DiscountRule createDiscountRule(Long id, String name, String ruleType) {
        DiscountRule rule = new DiscountRule();
        rule.setId(id);
        rule.setName(name);
        rule.setRuleType(ruleType);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("percentage", new BigDecimal("10"));
        rule.setRuleParameters(parameters);
        rule.setRuleParametersJson("{\"percentage\":10}");
        return rule;
    }

    private DiscountContext createDiscountContext(Long productId, Long chargeId, Long accountId) {
        DiscountContext context = new DiscountContext();
        context.setProductId(productId);
        context.setChargeId(chargeId);
        context.setAccountId(accountId);
        context.setTransactionAmount(new BigDecimal("100.00"));
        context.setTransactionDate(java.time.LocalDate.now());
        return context;
    }

    private Charge createCharge(Long id, String name, boolean isPenalty, Integer chargeTimeType) {
        // Use reflection or mock - Charge doesn't have public setters
        // For testing purposes, we'll use a mock
        Charge charge = org.mockito.Mockito.mock(Charge.class);
        when(charge.getId()).thenReturn(id);
        when(charge.getName()).thenReturn(name);
        when(charge.isPenalty()).thenReturn(isPenalty);
        when(charge.getChargeTimeType()).thenReturn(chargeTimeType);
        return charge;
    }

    private SavingsAccount createSavingsAccount(Long id, String accountNumber, Long clientId) {
        // Use mock since SavingsAccount doesn't have public constructor
        SavingsAccount account = org.mockito.Mockito.mock(SavingsAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getAccountNumber()).thenReturn(accountNumber);
        if (clientId != null) {
            org.apache.fineract.portfolio.client.domain.Client client = org.mockito.Mockito
                    .mock(org.apache.fineract.portfolio.client.domain.Client.class);
            when(client.getId()).thenReturn(clientId);
            when(account.getClient()).thenReturn(client);
        }
        return account;
    }
}

