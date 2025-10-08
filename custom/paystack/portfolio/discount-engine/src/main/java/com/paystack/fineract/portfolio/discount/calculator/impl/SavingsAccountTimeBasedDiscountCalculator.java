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

import com.paystack.fineract.portfolio.discount.annotation.DiscountRuleType;
import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.springframework.stereotype.Service;

/**
 * Savings Account Time-Based Fee Discount Calculator Implements time-based fee discounts for weekends, holidays, and
 * specific date ranges. Supports charge-specific rules with generic fallback logic.
 */
@DiscountRuleType(value = "TIME_BASED", category = "SAVINGS_ACCOUNT")
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsAccountTimeBasedDiscountCalculator implements DiscountRuleCalculator {

    private final HolidayRepositoryWrapper holidayRepositoryWrapper;

    // Parameter constants
    private static final String PARAM_TIME_RULE_TYPE = "timeRuleType";
    private static final String PARAM_DISCOUNT_PERCENTAGE = "discountPercentage";
    private static final String PARAM_WEEKEND_DAYS = "weekendDays";
    private static final String PARAM_START_DATE = "startDate";
    private static final String PARAM_END_DATE = "endDate";
    private static final String PARAM_DATE_FORMAT = "dateFormat";

    // Time rule type constants
    private static final String TIME_RULE_WEEKEND = "WEEKEND";
    private static final String TIME_RULE_HOLIDAY = "HOLIDAY";
    private static final String TIME_RULE_DATE_RANGE = "DATE_RANGE";

    // Default values
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
    private static final BigDecimal MAX_DISCOUNT_PERCENTAGE = BigDecimal.valueOf(100);

    // Configuration fields
    private String timeRuleType;
    private BigDecimal discountPercentage;
    private List<String> weekendDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String dateFormat;

    @Override
    public String getRuleType() {
        return "TIME_BASED";
    }

    @Override
    public String getRuleCategory() {
        return "SAVINGS_ACCOUNT";
    }

    @Override
    public String getRuleDescription() {
        return "Time-based fee discount calculator supporting weekends, holidays, and date ranges";
    }

    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList(PARAM_TIME_RULE_TYPE, PARAM_DISCOUNT_PERCENTAGE);
    }

    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList(PARAM_WEEKEND_DAYS, PARAM_START_DATE, PARAM_END_DATE, PARAM_DATE_FORMAT);
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put(PARAM_TIME_RULE_TYPE, "Time rule type: [WEEKEND, HOLIDAY, DATE_RANGE]");
        descriptions.put(PARAM_DISCOUNT_PERCENTAGE, "Discount percentage to apply (0 < p <= 100)");
        descriptions.put(PARAM_WEEKEND_DAYS, "Weekend days for WEEKEND rule: [SATURDAY, SUNDAY] (optional)");
        descriptions.put(PARAM_START_DATE, "Start date for date range constraint (optional)");
        descriptions.put(PARAM_END_DATE, "End date for date range constraint (optional)");
        descriptions.put(PARAM_DATE_FORMAT, "Custom date format for parsing dates (default: yyyy-MM-dd)");
        return descriptions;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context) {
        if (!isOriginalAmountValid(originalAmount) || !isContextValid(context)) {
            return BigDecimal.ZERO;
        }

        try {
            LocalDate transactionDate = getTransactionDate(context);
            boolean isApplicable = isTimeRuleApplicable(transactionDate, context);

            return isApplicable ? computePercentageDiscount(originalAmount) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("TIME_BASED CALCULATOR: Error calculating time-based discount for account {}", context.getAccountId(), e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean isApplicable(DiscountContext context) {
        return context != null && context.getAccountId() != null && context.getTransactionAmount() != null
                && context.getTransactionAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public boolean isValid(DiscountContext context) {
        return timeRuleType != null && !timeRuleType.trim().isEmpty() && discountPercentage != null
                && discountPercentage.compareTo(BigDecimal.ZERO) > 0 && discountPercentage.compareTo(MAX_DISCOUNT_PERCENTAGE) <= 0
                && isValidTimeRuleType(timeRuleType);
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        this.timeRuleType = parseStringParam(parameters, PARAM_TIME_RULE_TYPE);
        this.discountPercentage = parseDiscountPercentage(parameters);
        this.weekendDays = parseWeekendDays(parameters);
        this.dateFormat = parseStringParam(parameters, PARAM_DATE_FORMAT, DEFAULT_DATE_FORMAT);
        this.startDate = parseDateParam(parameters, PARAM_START_DATE);
        this.endDate = parseDateParam(parameters, PARAM_END_DATE);
    }

    /**
     * Check if the time rule is applicable for the given transaction date
     */
    private boolean isTimeRuleApplicable(LocalDate transactionDate, DiscountContext context) {
        if (!isWithinDateRange(transactionDate)) {
            return false;
        }

        return switch (timeRuleType.toUpperCase()) {
            case TIME_RULE_WEEKEND -> isWeekendApplicable(transactionDate);
            case TIME_RULE_HOLIDAY -> isHolidayApplicable(transactionDate, context);
            case TIME_RULE_DATE_RANGE -> isDateRangeApplicable(transactionDate);
            default -> {
                log.warn("TIME_BASED CALCULATOR: Unknown time rule type: {}", timeRuleType);
                yield false;
            }
        };
    }

    /**
     * Check if weekend discount is applicable
     */
    private boolean isWeekendApplicable(LocalDate transactionDate) {
        if (weekendDays == null || weekendDays.isEmpty()) {
            // Default to Saturday and Sunday if no weekend days specified
            DayOfWeek dayOfWeek = transactionDate.getDayOfWeek();
            return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        }

        String dayName = transactionDate.getDayOfWeek().name();
        return weekendDays.contains(dayName);
    }

    /**
     * Check if holiday discount is applicable
     */
    private boolean isHolidayApplicable(LocalDate transactionDate, DiscountContext context) {
        if (context.getOfficeId() == null) {
            log.warn("TIME_BASED CALCULATOR: Office ID is null, cannot check holiday");
            return false;
        }

        try {
            return holidayRepositoryWrapper.isHoliday(context.getOfficeId(), transactionDate);
        } catch (Exception e) {
            log.error("TIME_BASED CALCULATOR: Error checking holiday for date {} and office {}", transactionDate, context.getOfficeId(), e);
            return false;
        }
    }

    /**
     * Check if date range discount is applicable
     */
    private boolean isDateRangeApplicable(LocalDate transactionDate) {
        // For DATE_RANGE rule type, the date range is already checked in isWithinDateRange()
        // This method can be used for additional date range logic if needed
        log.info("transactionDate = {}", transactionDate);
        return true;
    }

    /**
     * Check if transaction date is within the configured date range
     */
    private boolean isWithinDateRange(LocalDate transactionDate) {
        if (startDate == null && endDate == null) {
            return true; // No date range constraint
        }

        boolean afterStart = startDate == null || !transactionDate.isBefore(startDate);
        boolean beforeEnd = endDate == null || !transactionDate.isAfter(endDate);

        return afterStart && beforeEnd;
    }

    /**
     * Get transaction date from context or current business date
     */
    private LocalDate getTransactionDate(DiscountContext context) {
        return context.getTransactionDate() != null ? context.getTransactionDate() : DateUtils.getBusinessLocalDate();
    }

    /**
     * Compute percentage discount amount
     */
    private BigDecimal computePercentageDiscount(BigDecimal originalAmount) {
        BigDecimal discount = originalAmount.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Ensure discount doesn't exceed original amount
        return discount.compareTo(originalAmount) > 0 ? originalAmount : discount;
    }

    /**
     * Validate time rule type
     */
    private boolean isValidTimeRuleType(String timeRuleType) {
        return TIME_RULE_WEEKEND.equals(timeRuleType) || TIME_RULE_HOLIDAY.equals(timeRuleType)
                || TIME_RULE_DATE_RANGE.equals(timeRuleType);
    }

    /**
     * Parse string parameter with default value
     */
    private String parseStringParam(Map<String, Object> parameters, String key, String defaultValue) {
        String value = parseStringParam(parameters, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Parse string parameter
     */
    private String parseStringParam(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) {
            return null;
        }
        Object val = parameters.get(key);
        return val != null ? val.toString() : null;
    }

    /**
     * Parse discount percentage parameter
     */
    private BigDecimal parseDiscountPercentage(Map<String, Object> parameters) {
        if (!parameters.containsKey(PARAM_DISCOUNT_PERCENTAGE)) {
            return null;
        }
        Object val = parameters.get(PARAM_DISCOUNT_PERCENTAGE);
        if (val == null) {
            return null;
        }
        try {
            return new BigDecimal(val.toString());
        } catch (NumberFormatException e) {
            log.warn("TIME_BASED CALCULATOR: Invalid discount percentage '{}'", val);
            return null;
        }
    }

    /**
     * Parse weekend days parameter
     */
    @SuppressWarnings("unchecked")
    private List<String> parseWeekendDays(Map<String, Object> parameters) {
        if (!parameters.containsKey(PARAM_WEEKEND_DAYS)) {
      return Collections.emptyList();
        }
        Object val = parameters.get(PARAM_WEEKEND_DAYS);
        if (val instanceof List) {
            return (List<String>) val;
        }
        log.warn("TIME_BASED CALCULATOR: Invalid weekend days format '{}'", val);
    return Collections.emptyList();
    }

    /**
     * Parse date parameter with custom format support
     */
    private LocalDate parseDateParam(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) {
            return null;
        }
        Object val = parameters.get(key);
        if (val == null) {
            return null;
        }

        String dateString = val.toString();
        if (dateString.trim().isEmpty()) {
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(dateFormat);
            return LocalDate.parse(dateString, formatter);
        } catch (DateTimeParseException e) {
            log.warn("TIME_BASED CALCULATOR: Invalid date format '{}' for parameter '{}'", dateString, key);
            return null;
        }
    }

    /**
     * Validate original amount
     */
    private boolean isOriginalAmountValid(BigDecimal originalAmount) {
        return originalAmount != null && originalAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Validate discount context
     */
    private boolean isContextValid(DiscountContext context) {
        return context != null && context.getAccountId() != null;
    }
}
