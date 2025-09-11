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

package com.paystack.fineract.discount.service;

import com.paystack.fineract.discount.domain.DiscountContext;
import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.discount.repository.ProductDiscountRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Enhanced Product Discount Service with Caching
 * Provides optimized discount application with caching support
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedProductDiscountService {
    
    private final ProductDiscountRuleRepository ruleRepository;
    private final DiscountConditionEvaluator conditionEvaluator;
    private final DiscountCacheService cacheService;
    private final DiscountMonitoringService monitoringService;
    
    /**
     * Apply discount with caching optimization
     */
    public BigDecimal applyDiscountWithCache(BigDecimal originalAmount, DiscountContext context) {
        log.debug("Applying discount with cache for context: {}", context);
        
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }
        
        // Get cached applicable rules
        List<ProductDiscountRule> applicableRules = cacheService.getCachedApplicableRules(context.getProductId());
        
        if (applicableRules.isEmpty()) {
            log.debug("No applicable discount rules found for product: {}", context.getProductId());
            return originalAmount;
        }
        
        BigDecimal totalDiscount = BigDecimal.ZERO;
        Long appliedRuleId = null;
        
        for (ProductDiscountRule rule : applicableRules) {
            if (rule.isEligibleForApplication(context)) {
                DiscountEvaluationResult evaluation = conditionEvaluator.evaluate(rule, context);
                if (evaluation.isEligible()) {
                    BigDecimal discountAmount = calculateDiscount(originalAmount, rule, context);
                    
                    if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                        totalDiscount = totalDiscount.add(discountAmount);
                        appliedRuleId = rule.getId();
                        
                        log.info("Applied discount of {} for rule: {} to amount: {}", 
                            discountAmount, rule.getName(), originalAmount);
                    }
                }
            }
        }
        
        BigDecimal finalAmount = originalAmount.subtract(totalDiscount);
        
        // Record monitoring metrics
        if (totalDiscount.compareTo(BigDecimal.ZERO) > 0) {
            monitoringService.recordDiscountApplication(originalAmount, totalDiscount, 
                context.getProductId(), appliedRuleId);
        }
        
        log.info("Discount application completed. Original: {}, Discount: {}, Final: {}", 
            originalAmount, totalDiscount, finalAmount);
        
        return finalAmount;
    }
    
    /**
     * Calculate discount amount for a rule
     */
    private BigDecimal calculateDiscount(BigDecimal originalAmount, ProductDiscountRule rule, DiscountContext context) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        
        switch (rule.getDiscountType()) {
            case PERCENTAGE:
                discountAmount = originalAmount.multiply(rule.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
                break;
            case FLAT:
                discountAmount = rule.getDiscountValue();
                break;
        }
        
        // Apply maximum discount limit
        if (rule.getMaxDiscountAmount() != null && 
            discountAmount.compareTo(rule.getMaxDiscountAmount()) > 0) {
            discountAmount = rule.getMaxDiscountAmount();
        }
        
        // Ensure discount doesn't exceed original amount
        if (discountAmount.compareTo(originalAmount) > 0) {
            discountAmount = originalAmount;
        }
        
        return discountAmount;
    }
    
    /**
     * Get performance metrics for a product
     */
    public DiscountMonitoringService.DiscountPerformanceMetrics getPerformanceMetrics(Long productId) {
        return monitoringService.getPerformanceMetrics(productId);
    }
    
    /**
     * Get system-wide performance metrics
     */
    public DiscountMonitoringService.SystemPerformanceMetrics getSystemPerformanceMetrics() {
        return monitoringService.getSystemPerformanceMetrics();
    }
    
    /**
     * Warm up cache for a product
     */
    public void warmUpProductCache(Long productId) {
        cacheService.warmUpCache(productId);
    }
    
    /**
     * Evict cache for a product
     */
    public void evictProductCache(Long productId) {
        cacheService.evictProductCache(productId);
    }
}
