/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.paystack.fineract.portfolio.discount.calculator.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsAccountTransactionCountDiscountCalculator Tests")
class SavingsAccountTransactionCountDiscountCalculatorTest {

    @Mock
    private PaystackSavingsAccountTransactionRepository transactionRepository;

    private SavingsAccountTransactionCountDiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SavingsAccountTransactionCountDiscountCalculator(transactionRepository);
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should return correct rule type and category")
        void shouldReturnCorrectRuleTypeAndCategory() {
            assertThat(calculator.getRuleType()).isEqualTo("TRANSACTION_COUNT");
            assertThat(calculator.getRuleCategory()).isEqualTo("SAVINGS_ACCOUNT");
        }

        @Test
        @DisplayName("Should return correct rule description")
        void shouldReturnCorrectRuleDescription() {
            assertThat(calculator.getRuleDescription())
                    .isEqualTo("Account-level transaction count-based fee discount (direction-aware, period-based)");
        }

        @Test
        @DisplayName("Should return required parameters")
        void shouldReturnRequiredParameters() {
            assertThat(calculator.getRequiredParameters()).containsExactly("thresholdCount", "periodType", "directionType",
                    "discountPercentage");
        }

        @Test
        @DisplayName("Should return optional parameters")
        void shouldReturnOptionalParameters() {
            assertThat(calculator.getOptionalParameters()).containsExactly("includeReversed");
        }

        @Test
        @DisplayName("Should return parameter descriptions")
        void shouldReturnParameterDescriptions() {
            Map<String, String> descriptions = calculator.getParameterDescriptions();

            assertThat(descriptions).hasSize(5);
            assertThat(descriptions.get("thresholdCount")).isEqualTo("Minimum number of transactions in period required (e.g., 50)");
            assertThat(descriptions.get("periodType")).isEqualTo("Counting window: [DAILY, MONTHLY, QUARTERLY]");
            assertThat(descriptions.get("directionType")).isEqualTo("Direction of transactions to count: [INFLOW, OUTFLOW, ALL]");
            assertThat(descriptions.get("discountPercentage")).isEqualTo("Percentage discount to apply when threshold met (0 < p <= 100)");
            assertThat(descriptions.get("includeReversed"))
                    .isEqualTo("Whether to include reversed transactions in the count [TRUE, FALSE] (default FALSE)");
        }
    }

    @Nested
    @DisplayName("Configuration Validation Tests")
    class ConfigurationValidationTests {

        @Test
        @DisplayName("Should configure with valid parameters")
        void shouldConfigureWithValidParameters() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));
            parameters.put("includeReversed", false);

            calculator.configure(parameters);

            // Verify configuration was applied (we can't directly access private fields)
            // But we can test through the isValid method
            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isTrue();
        }

        @Test
        @DisplayName("Should handle invalid threshold count")
        void shouldHandleInvalidThresholdCount() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", "invalid");
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isFalse();
        }

        @Test
        @DisplayName("Should handle null threshold count")
        void shouldHandleNullThresholdCount() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", null);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isFalse();
        }

        @Test
        @DisplayName("Should handle invalid discount percentage")
        void shouldHandleInvalidDiscountPercentage() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", "invalid");

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isFalse();
        }

        @Test
        @DisplayName("Should handle discount percentage out of range")
        void shouldHandleDiscountPercentageOutOfRange() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(150.0)); // > 100

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isFalse();
        }

        @Test
        @DisplayName("Should handle includeReversed as boolean")
        void shouldHandleIncludeReversedAsBoolean() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));
            parameters.put("includeReversed", true);

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isTrue();
        }

        @Test
        @DisplayName("Should handle includeReversed as string")
        void shouldHandleIncludeReversedAsString() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));
            parameters.put("includeReversed", "true");

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isTrue();
        }

        @Test
        @DisplayName("Should default includeReversed to false when not provided")
        void shouldDefaultIncludeReversedToFalseWhenNotProvided() {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", BigDecimal.valueOf(10.0));

            calculator.configure(parameters);

            DiscountContext context = createValidContext();
            assertThat(calculator.isValid(context)).isTrue();
        }
    }

    @Nested
    @DisplayName("Discount Calculation Tests")
    class DiscountCalculationTests {

        @Test
        @DisplayName("Should apply discount when threshold is reached")
        void shouldApplyDiscountWhenThresholdIsReached() {
            // Configure calculator
            configureCalculator(50, "MONTHLY", "INFLOW", BigDecimal.valueOf(10.0), false);

            // Mock repository to return count above threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(60L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(10.0));
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), any(LocalDate.class), any(LocalDate.class), eq(false),
                    any());
        }

        @Test
        @DisplayName("Should not apply discount when threshold is not reached")
        void shouldNotApplyDiscountWhenThresholdIsNotReached() {
            // Configure calculator
            configureCalculator(50, "MONTHLY", "INFLOW", BigDecimal.valueOf(10.0), false);

            // Mock repository to return count below threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(30L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should apply discount for OUTFLOW direction")
        void shouldApplyDiscountForOutflowDirection() {
            // Configure calculator
            configureCalculator(30, "DAILY", "OUTFLOW", BigDecimal.valueOf(15.0), false);

            // Mock repository to return count above threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(35L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(200.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(30.0));
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), any(LocalDate.class), any(LocalDate.class), eq(false),
                    any());
        }

        @Test
        @DisplayName("Should apply discount for ALL direction")
        void shouldApplyDiscountForAllDirection() {
            // Configure calculator
            configureCalculator(100, "QUARTERLY", "ALL", BigDecimal.valueOf(20.0), true);

            // Mock repository to return count above threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(150L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(500.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100.0));
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), any(LocalDate.class), any(LocalDate.class), eq(true), any());
        }

        @Test
        @DisplayName("Should handle 100% discount correctly")
        void shouldHandle100PercentDiscountCorrectly() {
            // Configure calculator
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(100.0), false);

            // Mock repository to return count above threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        }

        @Test
        @DisplayName("Should cap discount at original amount")
        void shouldCapDiscountAtOriginalAmount() {
            // Configure calculator with 150% discount (should be capped)
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(150.0), false);

            // Mock repository to return count above threshold
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            // Should be capped at original amount (100.0)
            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(100.0));
        }

        @Test
        @DisplayName("Should handle zero original amount")
        void shouldHandleZeroOriginalAmount() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(10.0), false);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.ZERO, context);

            assertThat(result).isEqualTo(BigDecimal.ZERO);
            verify(transactionRepository, never()).countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class),
                    anyBoolean(), any());
        }

        @Test
        @DisplayName("Should handle null original amount")
        void shouldHandleNullOriginalAmount() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(10.0), false);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(null, context);

            assertThat(result).isEqualTo(BigDecimal.ZERO);
            verify(transactionRepository, never()).countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class),
                    anyBoolean(), any());
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(10.0), false);

            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), null);

            assertThat(result).isEqualTo(BigDecimal.ZERO);
            verify(transactionRepository, never()).countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class),
                    anyBoolean(), any());
        }

        @Test
        @DisplayName("Should handle repository exception gracefully")
        void shouldHandleRepositoryExceptionGracefully() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(10.0), false);

            // Mock repository to throw exception
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenThrow(new RuntimeException("Database error"));

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            assertThat(result).isEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Period Resolution Tests")
    class PeriodResolutionTests {

        @Test
        @DisplayName("Should use DAILY period correctly")
        void shouldUseDailyPeriodCorrectly() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(10.0), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            LocalDate transactionDate = LocalDate.of(2024, 3, 15);
            context.setTransactionDate(transactionDate);

            calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            // For DAILY period, start date should be the same as end date
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), eq(transactionDate), eq(transactionDate), eq(false), any());
        }

        @Test
        @DisplayName("Should use MONTHLY period correctly")
        void shouldUseMonthlyPeriodCorrectly() {
            configureCalculator(10, "MONTHLY", "INFLOW", BigDecimal.valueOf(10.0), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            LocalDate transactionDate = LocalDate.of(2024, 3, 15);
            context.setTransactionDate(transactionDate);

            calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            // For MONTHLY period, start date should be first day of month
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), eq(LocalDate.of(2024, 3, 1)), eq(transactionDate), eq(false),
                    any());
        }

        @Test
        @DisplayName("Should use QUARTERLY period correctly")
        void shouldUseQuarterlyPeriodCorrectly() {
            configureCalculator(10, "QUARTERLY", "INFLOW", BigDecimal.valueOf(10.0), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            LocalDate transactionDate = LocalDate.of(2024, 5, 15); // Q2
            context.setTransactionDate(transactionDate);

            calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            // For QUARTERLY period, start date should be first day of quarter (April 1st for Q2)
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), eq(LocalDate.of(2024, 4, 1)), eq(transactionDate), eq(false),
                    any());
        }

        @Test
        @DisplayName("Should handle unknown period type with default to DAILY")
        void shouldHandleUnknownPeriodTypeWithDefaultToDaily() {
            configureCalculator(10, "UNKNOWN", "INFLOW", BigDecimal.valueOf(10.0), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            LocalDate transactionDate = LocalDate.of(2024, 3, 15);
            context.setTransactionDate(transactionDate);

            calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            // Should default to DAILY behavior
            verify(transactionRepository).countTransactionsForPeriod(eq(123L), eq(transactionDate), eq(transactionDate), eq(false), any());
        }
    }

    @Nested
    @DisplayName("Applicability Tests")
    class ApplicabilityTests {

        @Test
        @DisplayName("Should be applicable with valid context")
        void shouldBeApplicableWithValidContext() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(5.0), false);
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);
            DiscountContext context = createValidContext();
            assertThat(calculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should not be applicable with null context")
        void shouldNotBeApplicableWithNullContext() {
            assertThat(calculator.isApplicable(null)).isFalse();
        }

        @Test
        @DisplayName("Should not be applicable with null account ID")
        void shouldNotBeApplicableWithNullAccountId() {
            DiscountContext context = new DiscountContext();
            context.setAccountId(null);
            context.setTransactionAmount(BigDecimal.valueOf(100.0));

            assertThat(calculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should not be applicable with null transaction amount")
        void shouldNotBeApplicableWithNullTransactionAmount() {
            DiscountContext context = new DiscountContext();
            context.setAccountId(123L);
            context.setTransactionAmount(null);

            assertThat(calculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should not be applicable with zero transaction amount")
        void shouldNotBeApplicableWithZeroTransactionAmount() {
            DiscountContext context = new DiscountContext();
            context.setAccountId(123L);
            context.setTransactionAmount(BigDecimal.ZERO);

            assertThat(calculator.isApplicable(context)).isFalse();
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle exact threshold count")
        void shouldHandleExactThresholdCount() {
            configureCalculator(50, "MONTHLY", "INFLOW", BigDecimal.valueOf(10.0), false);

            // Mock repository to return exact threshold count
            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(50L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(100.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        }

        @Test
        @DisplayName("Should handle threshold count of 1")
        void shouldHandleThresholdCountOfOne() {
            configureCalculator(1, "DAILY", "INFLOW", BigDecimal.valueOf(5.0), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(1L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(200.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(10.0));
        }

        @Test
        @DisplayName("Should handle very large threshold count")
        void shouldHandleVeryLargeThresholdCount() {
            configureCalculator(10000, "QUARTERLY", "ALL", BigDecimal.valueOf(25.0), true);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(10001L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(1000.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(250.0));
        }

        @Test
        @DisplayName("Should handle very small discount percentage")
        void shouldHandleVerySmallDiscountPercentage() {
            configureCalculator(10, "DAILY", "INFLOW", BigDecimal.valueOf(0.01), false);

            when(transactionRepository.countTransactionsForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class), anyBoolean(),
                    any())).thenReturn(15L);

            DiscountContext context = createValidContext();
            BigDecimal result = calculator.calculateDiscount(BigDecimal.valueOf(1000.0), context);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(0.10));
        }
    }

    // Helper methods
    private void configureCalculator(int thresholdCount, String periodType, String directionType, BigDecimal discountPercentage,
            boolean includeReversed) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("thresholdCount", thresholdCount);
        parameters.put("periodType", periodType);
        parameters.put("directionType", directionType);
        parameters.put("discountPercentage", discountPercentage);
        parameters.put("includeReversed", includeReversed);

        calculator.configure(parameters);
    }

    private DiscountContext createValidContext() {
        DiscountContext context = new DiscountContext();
        context.setAccountId(123L);
        context.setTransactionAmount(BigDecimal.valueOf(100.0));
        context.setTransactionDate(LocalDate.of(2024, 3, 15));
        context.setOfficeId(456L);
        return context;
    }
}
