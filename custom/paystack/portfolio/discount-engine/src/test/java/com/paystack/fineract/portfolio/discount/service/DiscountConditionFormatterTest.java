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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("DiscountConditionFormatter Tests")
class DiscountConditionFormatterTest {

    private DiscountConditionFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new DiscountConditionFormatter();
    }

    @Nested
    @DisplayName("Account Balance Conditions Formatting")
    class AccountBalanceTests {

        @Test
        @DisplayName("Should format account balance conditions with all parameters")
        void shouldFormatAccountBalanceWithAllParameters() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("minimumAverageBalance", new BigDecimal("500000"));
            parameters.put("discountPercentage", new BigDecimal("5"));
            parameters.put("maxDiscountAmount", new BigDecimal("1000"));

            // When
            String result = formatter.formatConditions("ACCOUNT_BALANCE", parameters);

            // Then
            assertThat(result).contains("Average balance >= 500000");
            assertThat(result).contains("Discount: 5%");
            assertThat(result).contains("Max discount: 1000");
        }

        @Test
        @DisplayName("Should format account balance conditions with minimal parameters")
        void shouldFormatAccountBalanceWithMinimalParameters() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("minimumAverageBalance", new BigDecimal("100000"));
            parameters.put("discountPercentage", new BigDecimal("10"));

            // When
            String result = formatter.formatConditions("ACCOUNT_BALANCE", parameters);

            // Then
            assertThat(result).contains("Average balance >= 100000");
            assertThat(result).contains("Discount: 10%");
        }
    }

    @Nested
    @DisplayName("Transaction Count Conditions Formatting")
    class TransactionCountTests {

        @Test
        @DisplayName("Should format transaction count conditions with all parameters")
        void shouldFormatTransactionCountWithAllParameters() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 50);
            parameters.put("periodType", "MONTHLY");
            parameters.put("directionType", "INFLOW");
            parameters.put("discountPercentage", new BigDecimal("15"));

            // When
            String result = formatter.formatConditions("TRANSACTION_COUNT", parameters);

            // Then
            assertThat(result).contains("Transaction count >= 50");
            assertThat(result).contains("MONTHLY");
            assertThat(result).contains("Direction: INFLOW");
            assertThat(result).contains("Discount: 15%");
        }

        @Test
        @DisplayName("Should format transaction count with quarterly period")
        void shouldFormatTransactionCountWithQuarterlyPeriod() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("thresholdCount", 100);
            parameters.put("periodType", "QUARTERLY");
            parameters.put("discountPercentage", new BigDecimal("20"));

            // When
            String result = formatter.formatConditions("TRANSACTION_COUNT", parameters);

            // Then
            assertThat(result).contains("Transaction count >= 100");
            assertThat(result).contains("QUARTERLY");
        }
    }

    @Nested
    @DisplayName("Percentage Conditions Formatting")
    class PercentageTests {

        @Test
        @DisplayName("Should format percentage conditions with all parameters")
        void shouldFormatPercentageWithAllParameters() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("percentage", new BigDecimal("25"));
            parameters.put("minimumTransactionAmount", new BigDecimal("1000"));
            parameters.put("maximumTransactionAmount", new BigDecimal("10000"));
            parameters.put("maxDiscountAmount", new BigDecimal("500"));

            // When
            String result = formatter.formatConditions("PERCENTAGE", parameters);

            // Then
            assertThat(result).contains("Percentage: 25%");
            assertThat(result).contains("Min amount: 1000");
            assertThat(result).contains("Max amount: 10000");
            assertThat(result).contains("Max discount: 500");
        }

        @Test
        @DisplayName("Should format percentage with only percentage value")
        void shouldFormatPercentageWithOnlyPercentage() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("percentage", new BigDecimal("10"));

            // When
            String result = formatter.formatConditions("PERCENTAGE", parameters);

            // Then
            assertThat(result).contains("Percentage: 10%");
        }
    }

    @Nested
    @DisplayName("Flat Discount Conditions Formatting")
    class FlatDiscountTests {

        @Test
        @DisplayName("Should format flat discount conditions")
        void shouldFormatFlatDiscount() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("amount", new BigDecimal("50"));

            // When
            String result = formatter.formatConditions("FLAT", parameters);

            // Then
            assertThat(result).contains("Flat discount: 50");
        }

        @Test
        @DisplayName("Should handle flat discount without amount")
        void shouldHandleFlatDiscountWithoutAmount() {
            // Given
            Map<String, Object> parameters = new HashMap<>();

            // When
            String result = formatter.formatConditions("FLAT", parameters);

            // Then
            // When parameters are empty, the formatter returns "No conditions specified" from the initial check
            assertThat(result).isNotNull();
            // The formatter will return "Flat discount: Amount not specified" if amount key is missing
            // But with empty map, it returns "No conditions specified" from the initial null check
            assertThat(result).isIn("No conditions specified", "Flat discount: Amount not specified");
        }
    }

    @Nested
    @DisplayName("Time Based Conditions Formatting")
    class TimeBasedTests {

        @Test
        @DisplayName("Should format time-based conditions")
        void shouldFormatTimeBasedConditions() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("dayOfWeek", "FRIDAY");
            parameters.put("timeRange", "09:00-17:00");
            parameters.put("discountPercentage", new BigDecimal("20"));

            // When
            String result = formatter.formatConditions("TIME_BASED", parameters);

            // Then
            assertThat(result).contains("Time-based:");
            assertThat(result).contains("Day: FRIDAY");
            assertThat(result).contains("Time: 09:00-17:00");
            assertThat(result).contains("Discount: 20%");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null rule type")
        void shouldHandleNullRuleType() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("key", "value");

            // When
            String result = formatter.formatConditions(null, parameters);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle null parameters")
        void shouldHandleNullParameters() {
            // When
            String result = formatter.formatConditions("PERCENTAGE", null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).contains("No conditions specified");
        }

        @Test
        @DisplayName("Should handle empty parameters")
        void shouldHandleEmptyParameters() {
            // Given
            Map<String, Object> parameters = new HashMap<>();

            // When
            String result = formatter.formatConditions("PERCENTAGE", parameters);

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should handle unknown rule type with generic formatting")
        void shouldHandleUnknownRuleType() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("param1", "value1");
            parameters.put("param2", 123);

            // When
            String result = formatter.formatConditions("UNKNOWN_TYPE", parameters);

            // Then
            assertThat(result).contains("param1: value1");
            assertThat(result).contains("param2: 123");
        }

        @Test
        @DisplayName("Should handle BigDecimal as string")
        void shouldHandleBigDecimalAsString() {
            // Given
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("percentage", "15.5"); // String instead of BigDecimal

            // When
            String result = formatter.formatConditions("PERCENTAGE", parameters);

            // Then
            assertThat(result).contains("Percentage: 15.5%");
        }
    }
}

