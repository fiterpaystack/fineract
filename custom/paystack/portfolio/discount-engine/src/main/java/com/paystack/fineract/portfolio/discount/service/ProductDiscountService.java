package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.factory.DiscountRuleCalculatorFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Product Discount Service
 * Handles discount application logic for products and charges
 * Implements charge-first priority: charge rules take precedence over product rules
 * Uses the consolidated DiscountRuleService for rule management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDiscountService {

    private final DiscountRuleService discountRuleService;
    private final DiscountRuleCalculatorFactory calculatorFactory;
    private final ThreadLocal<Set<String>> appliedDiscounts = ThreadLocal.withInitial(HashSet::new);
    
    /**
     * Apply discount to an amount based on discount context
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(BigDecimal originalAmount, com.paystack.fineract.portfolio.discount.domain.DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }
        
        if (context == null || context.getProductId() == null) {
            return originalAmount;
        }
        
        return applyDiscount(context.getProductId(), originalAmount, context.getChargeId());
    }
    
    /**
     * Apply discount for a specific product and charge
     * Implements charge-first priority: check charge rules first, fall back to product rules
     * Uses the new calculator system when available
     * FIXED: Added proper ThreadLocal cleanup to prevent memory leaks
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(Long productId, BigDecimal originalAmount, Long chargeId) {
        String discountKey = productId + ":" + chargeId + ":" + originalAmount;
        
        try {
            if (appliedDiscounts.get().contains(discountKey)) {
                log.warn("🎯 DISCOUNT ENGINE: Duplicate discount application detected for product: {}, charge: {}, amount: {}", 
                        productId, chargeId, originalAmount);
                return originalAmount;
            }
            
            appliedDiscounts.get().add(discountKey);
            
            
            // Create discount context for the new calculator system
            DiscountContext context = createDiscountContext(productId, chargeId, originalAmount);
            
            // 1. Check for charge-level rules first
            List<com.paystack.fineract.portfolio.discount.domain.DiscountRule> chargeRules = 
                discountRuleService.getAssignedDiscountRules("CHARGE", chargeId);
            
            if (!chargeRules.isEmpty()) {
                return applyRulesWithCalculator(chargeRules, originalAmount, context);
            }
            
            // 2. Fall back to product-level rules
            return discountRuleService.applyDiscountWithCalculator("SAVINGS_PRODUCT", productId, originalAmount, context);
            
        } finally {
            // Clean up ThreadLocal after each discount application to prevent memory leaks
            appliedDiscounts.remove();
        }
    }
    
    public void clearAppliedDiscounts() {
        appliedDiscounts.remove();
    }
    
    @PreDestroy
    public void cleanup() {
        appliedDiscounts.remove();
    }
    
    /**
     * Create discount context for calculator system
     */
    private DiscountContext createDiscountContext(Long productId, Long chargeId, BigDecimal originalAmount) {
        DiscountContext context = new DiscountContext();
        context.setProductId(productId);
        context.setChargeId(chargeId);
        context.setTransactionAmount(originalAmount);
        context.setTransactionDate(java.time.LocalDate.now());
        // Add more context fields as needed
        return context;
    }
    
    /**
     * Apply discount rules to an amount using the new calculator system
     */
    private BigDecimal applyRulesWithCalculator(List<com.paystack.fineract.portfolio.discount.domain.DiscountRule> rules, BigDecimal originalAmount, DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }
        
        BigDecimal totalDiscount = BigDecimal.ZERO;
        java.time.LocalDate evaluationDate = java.time.LocalDate.now();
        
        for (com.paystack.fineract.portfolio.discount.domain.DiscountRule rule : rules) {
            if (rule.isValidForDate(evaluationDate)) {
                BigDecimal discountAmount = calculateDiscountWithRule(rule, originalAmount, context);
                if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    totalDiscount = totalDiscount.add(discountAmount);
                }
            }
        }
        
        BigDecimal finalAmount = originalAmount.subtract(totalDiscount);
        
        return finalAmount;
    }
    
    /**
     * Calculate discount for a specific rule using the calculator system
     * FIXED: Eliminated circular dependency by using direct calculator invocation
     */
    private BigDecimal calculateDiscountWithRule(com.paystack.fineract.portfolio.discount.domain.DiscountRule rule, BigDecimal originalAmount, DiscountContext context) {
        if (rule.getRuleType() != null && rule.getRuleParametersJson() != null) {
            try {
                // Direct calculator usage - no recursive service calls
                DiscountRuleCalculator calculator = calculatorFactory.createCalculator(
                    rule.getRuleType(), 
                    rule.getRuleParameters()
                );
                
                if (calculator.isApplicable(context) && calculator.isValid(context)) {
                    return calculator.calculateDiscount(originalAmount, context);
                }
            } catch (Exception e) {
                log.warn("🎯 DISCOUNT ENGINE: Failed to use calculator for rule {}: {}", 
                    rule.getName(), e.getMessage());
            }
        }
        
        // Fall back to zero if calculator fails
        return BigDecimal.ZERO;
    }
    
}
