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
import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.stereotype.Service;

/**
 * Savings Account Transaction Count-Based Fee Discount Calculator Implements Account-Level Transaction Count-Based Fee
 * Discounts. Applies a percentage discount to fee amounts if an account's number of transactions within a configured
 * period reaches a configured threshold. Direction-aware.
 */
@DiscountRuleType(value = "TRANSACTION_COUNT", category = "SAVINGS_ACCOUNT")
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsAccountTransactionCountDiscountCalculator implements DiscountRuleCalculator {

    private final PaystackSavingsAccountTransactionRepository transactionRepository;

    private static final String PARAM_THRESHOLD_COUNT = "thresholdCount";
    private static final String PARAM_INCLUDE_REVERSED = "includeReversed";
    private static final String PARAM_PERIOD_TYPE = "periodType";
    private static final String PARAM_DIRECTION_TYPE = "directionType";
    private static final String PARAM_DISCOUNT_PERCENTAGE = "discountPercentage";

    private Integer thresholdCount;
    private String periodType; // DAILY, MONTHLY, QUARTERLY
    private String directionType; // INFLOW, OUTFLOW, ALL
    private BigDecimal discountPercentage; // 0 < p <= 100
    private Boolean includeReversed; // default false

    @Override
    public String getRuleType() {
        return "TRANSACTION_COUNT";
    }

    @Override
    public String getRuleCategory() {
        return "SAVINGS_ACCOUNT";
    }

    @Override
    public String getRuleDescription() {
        return "Account-level transaction count-based fee discount (direction-aware, period-based)";
    }

    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList(PARAM_THRESHOLD_COUNT, PARAM_PERIOD_TYPE, PARAM_DIRECTION_TYPE, PARAM_DISCOUNT_PERCENTAGE);
    }

    @Override
    public List<String> getOptionalParameters() {
        return List.of(PARAM_INCLUDE_REVERSED);
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put(PARAM_THRESHOLD_COUNT, "Minimum number of transactions in period required (e.g., 50)");
        descriptions.put(PARAM_PERIOD_TYPE, "Counting window: [DAILY, MONTHLY, QUARTERLY]");
        descriptions.put(PARAM_DIRECTION_TYPE, "Direction of transactions to count: [INFLOW, OUTFLOW, ALL]");
        descriptions.put(PARAM_DISCOUNT_PERCENTAGE, "Percentage discount to apply when threshold met (0 < p <= 100)");
        descriptions.put(PARAM_INCLUDE_REVERSED, "Whether to include reversed transactions in the count (default false)");
        return descriptions;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context) {
        if (!isOriginalAmountValid(originalAmount) || !isContextValid(context)) {
            return BigDecimal.ZERO;
        }

        try {
            boolean thresholdReached = hasReachedThreshold(context);
            return thresholdReached ? computePercentageDiscount(originalAmount) : BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("COUNT CALCULATOR: Error calculating count-based discount for account {}", context.getAccountId(), e);
            return BigDecimal.ZERO;
        }
    }

    private boolean isOriginalAmountValid(BigDecimal originalAmount) {
        return originalAmount != null && originalAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isContextValid(DiscountContext context) {
        return context != null && context.getAccountId() != null;
    }

    private boolean hasReachedThreshold(DiscountContext context) {
        if ("ALL".equalsIgnoreCase(directionType)) {
            long countAll = countTransactionsForPeriod(context);
            return countAll >= thresholdCount;
        }
        List<SavingsAccountTransaction> transactions = getTransactionsForPeriod(context);
        if (transactions.isEmpty()) {
            return false;
        }
        long count = transactions.stream().filter(t -> (includeReversed != null && includeReversed) || !t.isReversed())
                .filter(this::matchesDirection).count();
        return count >= thresholdCount;
    }

    private BigDecimal computePercentageDiscount(BigDecimal originalAmount) {
        BigDecimal discount = originalAmount.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (discount.compareTo(originalAmount) > 0) {
            return originalAmount;
        }
        return discount;
    }

    @Override
    public boolean isApplicable(DiscountContext context) {
        return context != null && context.getAccountId() != null && context.getTransactionAmount() != null
                && context.getTransactionAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public boolean isValid(DiscountContext context) {
        return thresholdCount != null && thresholdCount > 0 && periodType != null && !periodType.trim().isEmpty() && directionType != null
                && !directionType.trim().isEmpty() && discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) > 0
                && discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        this.thresholdCount = parseThresholdCount(parameters);
        this.periodType = parseStringParam(parameters, PARAM_PERIOD_TYPE);
        this.directionType = parseStringParam(parameters, PARAM_DIRECTION_TYPE);
        this.discountPercentage = parseDiscountPercentage(parameters);
        this.includeReversed = parseIncludeReversed(parameters);
    }

    private Integer parseThresholdCount(Map<String, Object> parameters) {
        if (!parameters.containsKey(PARAM_THRESHOLD_COUNT)) {
            return null;
        }
        Object val = parameters.get(PARAM_THRESHOLD_COUNT);
        if (val == null) {
            log.warn("COUNT CALCULATOR: thresholdCount is null. Disabling.");
            return null;
        }
        try {
            return Integer.valueOf(val.toString());
        } catch (NumberFormatException e) {
            warnInvalidParam(PARAM_THRESHOLD_COUNT, val);
            return null;
        }
    }

    private String parseStringParam(Map<String, Object> parameters, String key) {
        if (!parameters.containsKey(key)) {
            return null;
        }
        Object val = parameters.get(key);
        return val != null ? val.toString() : null;
    }

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
            warnInvalidParam(PARAM_DISCOUNT_PERCENTAGE, val);
            return null;
        }
    }

    private void warnInvalidParam(String key, Object val) {
        log.warn("COUNT CALCULATOR: Invalid {} '{}'.", key, val);
    }

    private Boolean parseIncludeReversed(Map<String, Object> parameters) {
        if (!parameters.containsKey(PARAM_INCLUDE_REVERSED)) {
            return Boolean.FALSE;
        }
        Object value = parameters.get(PARAM_INCLUDE_REVERSED);
        return switch (value) {
            case Boolean b -> b;
            case String s -> "true".equalsIgnoreCase(s);
            case null -> Boolean.FALSE;
            default -> {
                log.warn("COUNT CALCULATOR: Unexpected includeReversed type: {}. Defaulting to false.", value.getClass().getSimpleName());
                yield Boolean.FALSE;
            }
        };
    }

    private List<SavingsAccountTransaction> getTransactionsForPeriod(DiscountContext context) {
        Long accountId = context.getAccountId();
        LocalDate endDate = context.getTransactionDate() != null ? context.getTransactionDate() : DateUtils.getBusinessLocalDate();
        LocalDate startDate = resolvePeriodStart(endDate);
        return transactionRepository.findTransactionsForPeriod(accountId, startDate, endDate);
    }

    private long countTransactionsForPeriod(DiscountContext context) {
        Long accountId = context.getAccountId();
        LocalDate endDate = context.getTransactionDate() != null ? context.getTransactionDate() : DateUtils.getBusinessLocalDate();
        LocalDate startDate = resolvePeriodStart(endDate);
        boolean include = includeReversed != null && includeReversed;
        return transactionRepository.countTransactionsForPeriod(accountId, startDate, endDate, include);
    }

    private LocalDate resolvePeriodStart(LocalDate endDate) {
        if (periodType == null) {
            log.warn("COUNT CALCULATOR: Null period type, defaulting to DAILY");
            return endDate;
        }
        String pt = periodType.toUpperCase();
        return switch (pt) {
            case "DAILY" -> endDate;
            case "MONTHLY" -> endDate.withDayOfMonth(1);
            case "QUARTERLY" -> {
                int currentQuarter = (endDate.getMonthValue() - 1) / 3; // 0-based
                int quarterStartMonth = currentQuarter * 3 + 1;
                yield LocalDate.of(endDate.getYear(), quarterStartMonth, 1);
            }
            default -> {
                log.warn("COUNT CALCULATOR: Unknown period type: {}, defaulting to DAILY", periodType);
                yield endDate;
            }
        };
    }

    private boolean matchesDirection(SavingsAccountTransaction transaction) {
        return switch (directionType.toUpperCase()) {
            case "INFLOW" -> transaction.isCredit();
            case "OUTFLOW" -> transaction.isDebit();
            case "ALL" -> true;
            default -> {
                log.warn("COUNT CALCULATOR: Unknown direction type: {}, defaulting to ALL", directionType);
                yield true;
            }
        };
    }
}
