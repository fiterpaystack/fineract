package com.paystack.fineract.discount.service;

import com.paystack.fineract.discount.domain.DiscountApplication;
import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.discount.repository.DiscountApplicationRepository;
import com.paystack.fineract.discount.repository.ProductDiscountRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Discount Service
 * Main service for handling discount rule application
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDiscountService {
    
    private final ProductDiscountRuleRepository ruleRepository;
    private final DiscountApplicationRepository applicationRepository;
    
    /**
     * Apply discount to an amount based on product ID
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(Long productId, BigDecimal originalAmount, Long chargeId) {
        log.debug("Applying discount for product: {}, original amount: {}", productId, originalAmount);
        
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Original amount is null or zero, returning original amount");
            return originalAmount;
        }
        
        List<ProductDiscountRule> rules = ruleRepository.findByProductIdAndActive(productId);
        
        if (rules.isEmpty()) {
            log.debug("No active discount rules found for product: {}", productId);
            return originalAmount;
        }
        
        BigDecimal totalDiscount = BigDecimal.ZERO;
        
        for (ProductDiscountRule rule : rules) {
            BigDecimal discountAmount = rule.calculateDiscount(originalAmount);
            
            if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                totalDiscount = totalDiscount.add(discountAmount);
                
                // Record discount application
                recordDiscountApplication(rule.getId(), chargeId, originalAmount, discountAmount);
                
                log.info("Applied discount of {} for rule: {} to amount: {}", 
                    discountAmount, rule.getName(), originalAmount);
            }
        }
        
        BigDecimal finalAmount = originalAmount.subtract(totalDiscount);
        log.info("Discount application completed. Original: {}, Discount: {}, Final: {}", 
            originalAmount, totalDiscount, finalAmount);
        
        return finalAmount;
    }
    
    /**
     * Record discount application in database
     */
    @Transactional
    private void recordDiscountApplication(Long ruleId, Long chargeId, BigDecimal originalAmount, BigDecimal discountAmount) {
        try {
            DiscountApplication application = DiscountApplication.createNew(
                ruleId, chargeId, originalAmount, discountAmount);
            
            applicationRepository.save(application);
            
            log.debug("Recorded discount application: {}", application.getId());
            
        } catch (Exception e) {
            log.error("Error recording discount application for rule: {}", ruleId, e);
        }
    }
    
    /**
     * Get discount rules for a product
     */
    public List<ProductDiscountRule> getDiscountRules(Long productId) {
        return ruleRepository.findByProductIdAndActive(productId);
    }
    
    /**
     * Create a new discount rule
     */
    @Transactional
    public ProductDiscountRule createDiscountRule(ProductDiscountRule rule) {
        log.info("Creating new discount rule: {}", rule.getName());
        return ruleRepository.save(rule);
    }
    
    /**
     * Update an existing discount rule
     */
    @Transactional
    public ProductDiscountRule updateDiscountRule(ProductDiscountRule rule) {
        log.info("Updating discount rule: {}", rule.getId());
        return ruleRepository.save(rule);
    }
    
    /**
     * Delete a discount rule (soft delete)
     */
    @Transactional
    public void deleteDiscountRule(Long ruleId) {
        log.info("Deleting discount rule: {}", ruleId);
        ProductDiscountRule rule = ruleRepository.findById(ruleId)
            .orElseThrow(() -> new RuntimeException("Discount rule not found: " + ruleId));
        rule.setActive(false);
        ruleRepository.save(rule);
    }
}