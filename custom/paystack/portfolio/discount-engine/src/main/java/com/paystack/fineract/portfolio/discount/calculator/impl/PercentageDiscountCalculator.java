package com.paystack.fineract.portfolio.discount.calculator.impl;

import com.paystack.fineract.portfolio.discount.annotation.DiscountRuleType;
import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Percentage-based discount calculator
 */
@DiscountRuleType(value = "PERCENTAGE", category = "BASIC")
@Service
@Slf4j
public class PercentageDiscountCalculator implements DiscountRuleCalculator {
    
    private BigDecimal percentage;
    private BigDecimal maxDiscountAmount;
    
    // Condition parameters
    private BigDecimal minimumTransactionAmount;
    private BigDecimal maximumTransactionAmount;
    
    @Override
    public String getRuleType() {
        return "PERCENTAGE";
    }
    
    @Override
    public String getRuleCategory() {
        return "BASIC";
    }
    
    @Override
    public String getRuleDescription() {
        return "Applies a percentage discount to the transaction amount";
    }
    
    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList("percentage");
    }
    
    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList("maxDiscountAmount", "minimumTransactionAmount", "maximumTransactionAmount");
    }
    
    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("percentage", "Discount percentage (e.g., 10 for 10%)");
        descriptions.put("maxDiscountAmount", "Maximum discount amount allowed");
        descriptions.put("minimumTransactionAmount", "Minimum transaction amount required for discount");
        descriptions.put("maximumTransactionAmount", "Maximum transaction amount for discount eligibility");
        return descriptions;
    }
    
    @Override
    public BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal discount = originalAmount.multiply(percentage).divide(BigDecimal.valueOf(100));
        
        // Apply maximum discount limit if set
        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            discount = maxDiscountAmount;
        }
        
        // Ensure discount doesn't exceed original amount
        if (discount.compareTo(originalAmount) > 0) {
            discount = originalAmount;
        }
        
        return discount;
    }
    
    @Override
    public boolean isApplicable(DiscountContext context) {
        if (context == null || context.getTransactionAmount() == null) {
            return false;
        }
        
        BigDecimal amount = context.getTransactionAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        
        // Check minimum transaction amount condition
        if (minimumTransactionAmount != null && amount.compareTo(minimumTransactionAmount) < 0) {
            return false;
        }
        
        // Check maximum transaction amount condition
        if (maximumTransactionAmount != null && amount.compareTo(maximumTransactionAmount) > 0) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean isValid(DiscountContext context) {
        return percentage != null && 
               percentage.compareTo(BigDecimal.ZERO) > 0 && 
               percentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }
    
    @Override
    public void configure(Map<String, Object> parameters) {
        if (parameters.containsKey("percentage")) {
            this.percentage = new BigDecimal(parameters.get("percentage").toString());
        }
        
        if (parameters.containsKey("maxDiscountAmount")) {
            this.maxDiscountAmount = new BigDecimal(parameters.get("maxDiscountAmount").toString());
        }
        
        if (parameters.containsKey("minimumTransactionAmount")) {
            this.minimumTransactionAmount = new BigDecimal(parameters.get("minimumTransactionAmount").toString());
        }
        
        if (parameters.containsKey("maximumTransactionAmount")) {
            this.maximumTransactionAmount = new BigDecimal(parameters.get("maximumTransactionAmount").toString());
        }
    }
    
    /**
     * Check if transaction time is within business hours (9 AM - 5 PM)
     */
    private boolean isBusinessHours(java.time.LocalDateTime transactionTime) {
        if (transactionTime == null) return true;
        
        int hour = transactionTime.getHour();
        return hour >= 9 && hour < 17;
    }
    
    /**
     * Check if transaction time is on a weekday (Monday-Friday)
     */
    private boolean isWeekday(java.time.LocalDateTime transactionTime) {
        if (transactionTime == null) return true;
        
        java.time.DayOfWeek dayOfWeek = transactionTime.getDayOfWeek();
        return dayOfWeek.getValue() >= 1 && dayOfWeek.getValue() <= 5;
    }
}
