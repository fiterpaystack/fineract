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

import java.math.BigDecimal;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Formats discount rule parameters into human-readable condition descriptions
 * for audit trail and reporting purposes
 */
@Service
@Slf4j
public class DiscountConditionFormatter {

    /**
     * Format triggered conditions based on rule type and parameters
     */
    public String formatConditions(String ruleType, Map<String, Object> parameters) {
        if (ruleType == null) {
            return "No conditions specified";
        }
        if (parameters == null || parameters.isEmpty()) {
            // For specific rule types, return more specific message
            if ("FLAT".equalsIgnoreCase(ruleType)) {
                return "Flat discount: Amount not specified";
            }
            return "No conditions specified";
        }

        return switch (ruleType.toUpperCase()) {
            case "ACCOUNT_BALANCE" -> formatAccountBalanceConditions(parameters);
            case "TRANSACTION_COUNT" -> formatTransactionCountConditions(parameters);
            case "TRANSACTION_FLOW" -> formatTransactionFlowConditions(parameters);
            case "TIME_BASED" -> formatTimeBasedConditions(parameters);
            case "PERCENTAGE" -> formatPercentageConditions(parameters);
            case "FLAT" -> formatFlatConditions(parameters);
            default -> formatGenericConditions(parameters);
        };
    }

    private String formatAccountBalanceConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder();
        
        if (parameters.containsKey("minimumAverageBalance")) {
            BigDecimal minBalance = getBigDecimal(parameters, "minimumAverageBalance");
            condition.append("Average balance >= ").append(formatCurrency(minBalance));
        }
        
        if (parameters.containsKey("discountPercentage")) {
            BigDecimal percentage = getBigDecimal(parameters, "discountPercentage");
            if (condition.length() > 0) {
                condition.append(", ");
            }
            condition.append("Discount: ").append(percentage).append("%");
        }
        
        if (parameters.containsKey("maxDiscountAmount")) {
            BigDecimal maxDiscount = getBigDecimal(parameters, "maxDiscountAmount");
            if (condition.length() > 0) {
                condition.append(", ");
            }
            condition.append("Max discount: ").append(formatCurrency(maxDiscount));
        }
        
        return condition.length() > 0 ? condition.toString() : "Balance-based discount";
    }

    private String formatTransactionCountConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder();
        
        if (parameters.containsKey("thresholdCount")) {
            Integer threshold = getInteger(parameters, "thresholdCount");
            condition.append("Transaction count >= ").append(threshold);
        }
        
        if (parameters.containsKey("periodType")) {
            String periodType = getString(parameters, "periodType");
            if (condition.length() > 0) {
                condition.append(" (").append(periodType).append(")");
            } else {
                condition.append("Period: ").append(periodType);
            }
        }
        
        if (parameters.containsKey("directionType")) {
            String direction = getString(parameters, "directionType");
            if (condition.length() > 0) {
                condition.append(", Direction: ").append(direction);
            } else {
                condition.append("Direction: ").append(direction);
            }
        }
        
        if (parameters.containsKey("discountPercentage")) {
            BigDecimal percentage = getBigDecimal(parameters, "discountPercentage");
            if (condition.length() > 0) {
                condition.append(", Discount: ").append(percentage).append("%");
            } else {
                condition.append("Discount: ").append(percentage).append("%");
            }
        }
        
        return condition.length() > 0 ? condition.toString() : "Transaction count-based discount";
    }

    private String formatTransactionFlowConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder("Transaction flow: ");
        
        if (parameters.containsKey("directionType")) {
            condition.append(getString(parameters, "directionType"));
        }
        
        if (parameters.containsKey("discountPercentage")) {
            BigDecimal percentage = getBigDecimal(parameters, "discountPercentage");
            condition.append(", Discount: ").append(percentage).append("%");
        }
        
        return condition.toString();
    }

    private String formatTimeBasedConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder("Time-based: ");
        
        if (parameters.containsKey("dayOfWeek")) {
            condition.append("Day: ").append(getString(parameters, "dayOfWeek"));
        }
        
        if (parameters.containsKey("timeRange")) {
            if (condition.length() > 12) {
                condition.append(", ");
            }
            condition.append("Time: ").append(getString(parameters, "timeRange"));
        }
        
        if (parameters.containsKey("discountPercentage")) {
            BigDecimal percentage = getBigDecimal(parameters, "discountPercentage");
            condition.append(", Discount: ").append(percentage).append("%");
        }
        
        return condition.toString();
    }

    private String formatPercentageConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder();
        
        if (parameters.containsKey("percentage")) {
            BigDecimal percentage = getBigDecimal(parameters, "percentage");
            condition.append("Percentage: ").append(percentage).append("%");
        }
        
        if (parameters.containsKey("minimumTransactionAmount")) {
            BigDecimal minAmount = getBigDecimal(parameters, "minimumTransactionAmount");
            if (condition.length() > 0) {
                condition.append(", Min amount: ").append(formatCurrency(minAmount));
            } else {
                condition.append("Min amount: ").append(formatCurrency(minAmount));
            }
        }
        
        if (parameters.containsKey("maximumTransactionAmount")) {
            BigDecimal maxAmount = getBigDecimal(parameters, "maximumTransactionAmount");
            if (condition.length() > 0) {
                condition.append(", Max amount: ").append(formatCurrency(maxAmount));
            } else {
                condition.append("Max amount: ").append(formatCurrency(maxAmount));
            }
        }
        
        if (parameters.containsKey("maxDiscountAmount")) {
            BigDecimal maxDiscount = getBigDecimal(parameters, "maxDiscountAmount");
            if (condition.length() > 0) {
                condition.append(", Max discount: ").append(formatCurrency(maxDiscount));
            } else {
                condition.append("Max discount: ").append(formatCurrency(maxDiscount));
            }
        }
        
        return condition.length() > 0 ? condition.toString() : "Percentage discount";
    }

    private String formatFlatConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder("Flat discount: ");
        
        if (parameters.containsKey("amount")) {
            BigDecimal amount = getBigDecimal(parameters, "amount");
            condition.append(formatCurrency(amount));
        } else {
            condition.append("Amount not specified");
        }
        
        return condition.toString();
    }

    private String formatGenericConditions(Map<String, Object> parameters) {
        StringBuilder condition = new StringBuilder();
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (!first) {
                condition.append(", ");
            }
            condition.append(entry.getKey()).append(": ").append(entry.getValue());
            first = false;
        }
        
        return condition.length() > 0 ? condition.toString() : "Generic discount conditions";
    }

    private BigDecimal getBigDecimal(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return BigDecimal.valueOf(((Number) value).doubleValue());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse BigDecimal for key {}: {}", key, value, e);
            return null;
        }
    }

    private Integer getInteger(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            log.warn("Failed to parse Integer for key {}: {}", key, value, e);
            return null;
        }
    }

    private String getString(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        return value != null ? value.toString() : null;
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        return amount.toPlainString();
    }
}

