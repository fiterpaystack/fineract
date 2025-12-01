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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SavingsAccountTimeBasedDiscountCalculator
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SavingsAccountTimeBasedDiscountCalculator Tests")
class SavingsAccountTimeBasedDiscountCalculatorTest {

    // Test date constants (using dd MMMM yyyy format)
    private static final String TEST_START_DATE_JAN = "01 January 2024";
    private static final String TEST_END_DATE_JAN = "31 January 2024";
    private static final String TEST_START_DATE_DEC = "01 December 2024";
    private static final String TEST_END_DATE_DEC = "31 December 2024";

    @Mock
    private HolidayRepositoryWrapper holidayRepositoryWrapper;

    private SavingsAccountTimeBasedDiscountCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new SavingsAccountTimeBasedDiscountCalculator(holidayRepositoryWrapper);
    }

    @Nested
    @DisplayName("Weekend Discount Tests")
    class WeekendDiscountTests {

        @Test
        @DisplayName("Should apply weekend discount on Saturday")
        void shouldApplyWeekendDiscountOnSaturday() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY", "SUNDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        }

        @Test
        @DisplayName("Should apply weekend discount on Sunday")
        void shouldApplyWeekendDiscountOnSunday() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(15.0), List.of("SATURDAY", "SUNDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 7)); // Sunday
            BigDecimal originalAmount = BigDecimal.valueOf(200.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        }

        @Test
        @DisplayName("Should not apply weekend discount on weekday")
        void shouldNotApplyWeekendDiscountOnWeekday() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY", "SUNDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 8)); // Monday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should apply weekend discount with date range constraint")
        void shouldApplyWeekendDiscountWithDateRange() {
            // Given
            Map<String, Object> parameters = createWeekendParametersWithDateRange(BigDecimal.valueOf(15.0), List.of("SATURDAY", "SUNDAY"),
                    TEST_START_DATE_JAN, TEST_END_DATE_JAN);
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday within range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        }

        @Test
        @DisplayName("Should not apply weekend discount outside date range")
        void shouldNotApplyWeekendDiscountOutsideDateRange() {
            // Given
            Map<String, Object> parameters = createWeekendParametersWithDateRange(BigDecimal.valueOf(15.0), List.of("SATURDAY", "SUNDAY"),
                    TEST_START_DATE_JAN, TEST_END_DATE_JAN);
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 2, 3)); // Saturday outside range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should apply weekend discount on Saturday when WEEKEND option is specified")
        void shouldApplyWeekendDiscountOnSaturdayWithWeekendOption() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("WEEKEND"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        }

        @Test
        @DisplayName("Should apply weekend discount on Sunday when WEEKEND option is specified")
        void shouldApplyWeekendDiscountOnSundayWithWeekendOption() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(15.0), List.of("WEEKEND"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 7)); // Sunday
            BigDecimal originalAmount = BigDecimal.valueOf(200.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        }

        @Test
        @DisplayName("Should not apply weekend discount on weekday when WEEKEND option is specified")
        void shouldNotApplyWeekendDiscountOnWeekdayWithWeekendOption() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("WEEKEND"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 8)); // Monday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle WEEKEND option with date range constraint")
        void shouldHandleWeekendOptionWithDateRange() {
            // Given
            Map<String, Object> parameters = createWeekendParametersWithDateRange(BigDecimal.valueOf(15.0), List.of("WEEKEND"),
                    TEST_START_DATE_JAN, TEST_END_DATE_JAN);
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday within range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(15.00));
        }

        @Test
        @DisplayName("Should handle WEEKEND option combined with SATURDAY (should not duplicate)")
        void shouldHandleWeekendOptionWithSaturday() {
            // Given - WEEKEND should expand to both Saturday and Sunday, even if SATURDAY is also specified
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("WEEKEND", "SATURDAY"));
            calculator.configure(parameters);

            // Saturday should work
            DiscountContext contextSaturday = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discountSaturday = calculator.calculateDiscount(originalAmount, contextSaturday);

            // Then
            assertThat(discountSaturday).isEqualByComparingTo(BigDecimal.valueOf(10.00));

            // Sunday should also work (WEEKEND expands to include Sunday)
            DiscountContext contextSunday = createContext(LocalDate.of(2024, 1, 7)); // Sunday
            BigDecimal discountSunday = calculator.calculateDiscount(originalAmount, contextSunday);

            // Then
            assertThat(discountSunday).isEqualByComparingTo(BigDecimal.valueOf(10.00));
        }
    }

    @Nested
    @DisplayName("Holiday Discount Tests")
    class HolidayDiscountTests {

        @Test
        @DisplayName("Should apply holiday discount on holiday")
        void shouldApplyHolidayDiscountOnHoliday() {
            // Given
            Map<String, Object> parameters = createHolidayParameters(BigDecimal.valueOf(20.0));
            calculator.configure(parameters);

            when(holidayRepositoryWrapper.isHoliday(anyLong(), any(LocalDate.class))).thenReturn(true);

            DiscountContext context = createContext(LocalDate.of(2024, 12, 25)); // Christmas
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(20.00));
        }

        @Test
        @DisplayName("Should not apply holiday discount on non-holiday")
        void shouldNotApplyHolidayDiscountOnNonHoliday() {
            // Given
            Map<String, Object> parameters = createHolidayParameters(BigDecimal.valueOf(20.0));
            calculator.configure(parameters);

            when(holidayRepositoryWrapper.isHoliday(anyLong(), any(LocalDate.class))).thenReturn(false);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 15)); // Regular day
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should apply holiday discount with date range constraint")
        void shouldApplyHolidayDiscountWithDateRange() {
            // Given
            Map<String, Object> parameters = createHolidayParametersWithDateRange(BigDecimal.valueOf(25.0), TEST_START_DATE_DEC,
                    TEST_END_DATE_DEC);
            calculator.configure(parameters);

            when(holidayRepositoryWrapper.isHoliday(anyLong(), any(LocalDate.class))).thenReturn(true);

            DiscountContext context = createContext(LocalDate.of(2024, 12, 25)); // Christmas within range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        }

        @Test
        @DisplayName("Should not apply holiday discount outside date range")
        void shouldNotApplyHolidayDiscountOutsideDateRange() {
            // Given
            Map<String, Object> parameters = createHolidayParametersWithDateRange(BigDecimal.valueOf(25.0), TEST_START_DATE_DEC,
                    TEST_END_DATE_DEC);
            calculator.configure(parameters);

            // No mock setup needed since date is outside range, holiday check won't be called

            DiscountContext context = createContext(LocalDate.of(2024, 11, 25)); // Holiday outside range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle null office ID gracefully")
        void shouldHandleNullOfficeIdGracefully() {
            // Given
            Map<String, Object> parameters = createHolidayParameters(BigDecimal.valueOf(20.0));
            calculator.configure(parameters);

            DiscountContext context = createContextWithNullOffice(LocalDate.of(2024, 12, 25));
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("Date Range Discount Tests")
    class DateRangeDiscountTests {

        @Test
        @DisplayName("Should apply date range discount within range")
        void shouldApplyDateRangeDiscountWithinRange() {
            // Given
            Map<String, Object> parameters = createDateRangeParameters(BigDecimal.valueOf(30.0), "24 November 2024", "30 November 2024");
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 11, 26)); // Black Friday week
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(30.00));
        }

        @Test
        @DisplayName("Should not apply date range discount outside range")
        void shouldNotApplyDateRangeDiscountOutsideRange() {
            // Given
            Map<String, Object> parameters = createDateRangeParameters(BigDecimal.valueOf(30.0), "24 November 2024", "30 November 2024");
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 12, 1)); // After range
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should apply date range discount with dd MMMM yyyy format")
        void shouldApplyDateRangeDiscountWithNewDateFormat() {
            // Given
            Map<String, Object> parameters = createDateRangeParameters(BigDecimal.valueOf(25.0), "24 November 2024", "30 November 2024");
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 11, 26));
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(25.00));
        }

        @Test
        @DisplayName("Should apply date range discount with single-digit day format")
        void shouldApplyDateRangeDiscountWithSingleDigitDayFormat() {
            // Given
            Map<String, Object> parameters = createDateRangeParameters(BigDecimal.valueOf(50.0), "1 December 2025", "1 December 2025");
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2025, 12, 1));
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        }

        @Test
        @DisplayName("Should apply date range discount with two-digit day format (Black Friday format)")
        void shouldApplyDateRangeDiscountWithTwoDigitDayFormat() {
            // Given - This matches the actual format used in the API: "01 December 2025"
            Map<String, Object> parameters = createDateRangeParameters(BigDecimal.valueOf(50.0), "01 December 2025", "01 December 2025");
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2025, 12, 1));
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(50.00));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation Tests")
    class EdgeCasesAndValidationTests {

        @Test
        @DisplayName("Should handle zero original amount")
        void shouldHandleZeroOriginalAmount() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.ZERO;

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle null original amount")
        void shouldHandleNullOriginalAmount() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday

            // When
            BigDecimal discount = calculator.calculateDiscount(null, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle null context")
        void shouldHandleNullContext() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY"));
            calculator.configure(parameters);

            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, null);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("Should handle 100% discount correctly")
        void shouldHandle100PercentDiscount() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(100.0), List.of("SATURDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        }

        @Test
        @DisplayName("Should handle discount exceeding original amount")
        void shouldHandleDiscountExceedingOriginalAmount() {
            // Given
            Map<String, Object> parameters = createWeekendParameters(BigDecimal.valueOf(150.0), List.of("SATURDAY"));
            calculator.configure(parameters);

            DiscountContext context = createContext(LocalDate.of(2024, 1, 6)); // Saturday
            BigDecimal originalAmount = BigDecimal.valueOf(100.00);

            // When
            BigDecimal discount = calculator.calculateDiscount(originalAmount, context);

            // Then
            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(100.00)); // Capped at original amount
        }
    }

    @Nested
    @DisplayName("Configuration and Validation Tests")
    class ConfigurationAndValidationTests {

        @Test
        @DisplayName("Should return correct rule type")
        void shouldReturnCorrectRuleType() {
            assertThat(calculator.getRuleType()).isEqualTo("TIME_BASED");
        }

        @Test
        @DisplayName("Should return correct rule category")
        void shouldReturnCorrectRuleCategory() {
            assertThat(calculator.getRuleCategory()).isEqualTo("SAVINGS_ACCOUNT");
        }

        @Test
        @DisplayName("Should return correct rule description")
        void shouldReturnCorrectRuleDescription() {
            assertThat(calculator.getRuleDescription())
                    .isEqualTo("Time-based fee discount calculator supporting weekends, holidays, and date ranges");
        }

        @Test
        @DisplayName("Should return required parameters")
        void shouldReturnRequiredParameters() {
            assertThat(calculator.getRequiredParameters()).containsExactly("timeRuleType", "discountPercentage");
        }

        @Test
        @DisplayName("Should return optional parameters")
        void shouldReturnOptionalParameters() {
            assertThat(calculator.getOptionalParameters()).containsExactly("weekendDays", "startDate", "endDate");
        }

        @Test
        @DisplayName("Should validate context correctly")
        void shouldValidateContextCorrectly() {
            calculator.configure(createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY", "SUNDAY")));

            // Valid context
            DiscountContext validContext = createContext(LocalDate.of(2024, 1, 6)); // Saturday aligns with configured
                                                                                    // WEEKEND rule
            assertThat(calculator.isApplicable(validContext)).isTrue();

            // Invalid context - null
            assertThat(calculator.isApplicable(null)).isFalse();

            // Invalid context - null account ID
            DiscountContext invalidContext = DiscountContext.builder().accountId(null).transactionAmount(BigDecimal.valueOf(100)).build();
            assertThat(calculator.isApplicable(invalidContext)).isFalse();
        }

        @Test
        @DisplayName("Should validate configuration correctly")
        void shouldValidateConfigurationCorrectly() {
            // Valid configuration
            Map<String, Object> validParams = createWeekendParameters(BigDecimal.valueOf(10.0), List.of("SATURDAY"));
            calculator.configure(validParams);
            DiscountContext context = createContext(LocalDate.now());
            assertThat(calculator.isValid(context)).isTrue();

            // Invalid configuration - null time rule type
            Map<String, Object> invalidParams = new HashMap<>();
            invalidParams.put("discountPercentage", BigDecimal.valueOf(10.0));
            calculator.configure(invalidParams);
            assertThat(calculator.isValid(context)).isFalse();
        }
    }

    // Helper methods for creating test data
    private Map<String, Object> createWeekendParameters(BigDecimal discountPercentage, List<String> weekendDays) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timeRuleType", "WEEKEND");
        parameters.put("discountPercentage", discountPercentage);
        parameters.put("weekendDays", weekendDays);
        return parameters;
    }

    private Map<String, Object> createWeekendParametersWithDateRange(BigDecimal discountPercentage, List<String> weekendDays,
            String startDate, String endDate) {
        Map<String, Object> parameters = createWeekendParameters(discountPercentage, weekendDays);
        parameters.put("startDate", startDate);
        parameters.put("endDate", endDate);
        return parameters;
    }

    private Map<String, Object> createHolidayParameters(BigDecimal discountPercentage) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timeRuleType", "HOLIDAY");
        parameters.put("discountPercentage", discountPercentage);
        return parameters;
    }

    private Map<String, Object> createHolidayParametersWithDateRange(BigDecimal discountPercentage, String startDate, String endDate) {
        Map<String, Object> parameters = createHolidayParameters(discountPercentage);
        parameters.put("startDate", startDate);
        parameters.put("endDate", endDate);
        return parameters;
    }

    private Map<String, Object> createDateRangeParameters(BigDecimal discountPercentage, String startDate, String endDate) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("timeRuleType", "DATE_RANGE");
        parameters.put("discountPercentage", discountPercentage);
        parameters.put("startDate", startDate);
        parameters.put("endDate", endDate);
        return parameters;
    }

    private DiscountContext createContext(LocalDate transactionDate) {
        return DiscountContext.builder().accountId(1L).officeId(1L).transactionDate(transactionDate)
                .transactionAmount(BigDecimal.valueOf(100)).build();
    }

    private DiscountContext createContextWithNullOffice(LocalDate transactionDate) {
        return DiscountContext.builder().accountId(1L).officeId(null).transactionDate(transactionDate)
                .transactionAmount(BigDecimal.valueOf(100)).build();
    }
}
