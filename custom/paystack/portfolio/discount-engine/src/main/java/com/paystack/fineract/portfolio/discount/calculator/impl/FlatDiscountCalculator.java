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
 * Flat amount discount calculator
 */
@DiscountRuleType(value = "FLAT", category = "BASIC")
@Service
@Slf4j
public class FlatDiscountCalculator implements DiscountRuleCalculator {
    
    private BigDecimal flatAmount;
    
    // Condition parameters
    private BigDecimal minimumTransactionAmount;
    private BigDecimal maximumTransactionAmount;
    
    @Override
    public String getRuleType() {
        return "FLAT";
    }
    
    @Override
    public String getRuleCategory() {
        return "BASIC";
    }
    
    @Override
    public String getRuleDescription() {
        return "Applies a fixed flat amount discount";
    }
    
    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList("flatAmount");
    }
    
    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList("minimumTransactionAmount", "maximumTransactionAmount");
    }
    
    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("flatAmount", "Fixed discount amount to apply");
        descriptions.put("minimumTransactionAmount", "Minimum transaction amount required for discount");
        descriptions.put("maximumTransactionAmount", "Maximum transaction amount for discount eligibility");
        return descriptions;
    }
    
    @Override
    public BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Ensure discount doesn't exceed original amount
        if (flatAmount.compareTo(originalAmount) > 0) {
            return originalAmount;
        }
        
        return flatAmount;
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
        return flatAmount != null && flatAmount.compareTo(BigDecimal.ZERO) > 0;
    }
    
    @Override
    public void configure(Map<String, Object> parameters) {
        if (parameters.containsKey("flatAmount")) {
            this.flatAmount = new BigDecimal(parameters.get("flatAmount").toString());
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
}
