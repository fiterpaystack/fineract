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

import com.paystack.fineract.discount.domain.DiscountApplication;
import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.discount.repository.DiscountApplicationRepository;
import com.paystack.fineract.discount.repository.ProductDiscountRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Discount Monitoring Service
 * Provides monitoring and performance metrics for discount engine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountMonitoringService {
    
    private final DiscountApplicationRepository applicationRepository;
    private final ProductDiscountRuleRepository ruleRepository;
    
    /**
     * Record discount application metrics
     */
    public void recordDiscountApplication(BigDecimal originalAmount, BigDecimal discountAmount, 
                                        Long productId, Long ruleId) {
        try {
            log.info("Discount applied - Product: {}, Rule: {}, Original: {}, Discount: {}, Percentage: {}%", 
                productId, ruleId, originalAmount, discountAmount, 
                calculateDiscountPercentage(originalAmount, discountAmount));
            
            // Record metrics for monitoring
            recordMetrics(originalAmount, discountAmount, productId, ruleId);
            
        } catch (Exception e) {
            log.error("Error recording discount application metrics", e);
        }
    }
    
    /**
     * Get performance metrics for discount engine
     */
    public DiscountPerformanceMetrics getPerformanceMetrics(Long productId) {
        DiscountPerformanceMetrics metrics = new DiscountPerformanceMetrics();
        metrics.setProductId(productId);
        
        try {
            // Get rule count
            long ruleCount = ruleRepository.countActiveRulesByProductId(productId);
            metrics.setActiveRuleCount(ruleCount);
            
            // Get application count
            List<DiscountApplication> applications = applicationRepository.findByProductId(productId);
            metrics.setTotalApplications(applications.size());
            
            // Calculate total discount amount
            BigDecimal totalDiscount = applications.stream()
                .map(DiscountApplication::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            metrics.setTotalDiscountAmount(totalDiscount);
            
            // Calculate average discount percentage
            if (!applications.isEmpty()) {
                BigDecimal totalOriginal = applications.stream()
                    .map(DiscountApplication::getOriginalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                
                if (totalOriginal.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal avgPercentage = totalDiscount
                        .divide(totalOriginal, 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
                    metrics.setAverageDiscountPercentage(avgPercentage);
                }
            }
            
            log.debug("Performance metrics calculated for product: {}, applications: {}, total discount: {}", 
                productId, metrics.getTotalApplications(), metrics.getTotalDiscountAmount());
            
        } catch (Exception e) {
            log.error("Error calculating performance metrics for product: {}", productId, e);
        }
        
        return metrics;
    }
    
    /**
     * Get system-wide performance metrics
     */
    public SystemPerformanceMetrics getSystemPerformanceMetrics() {
        SystemPerformanceMetrics metrics = new SystemPerformanceMetrics();
        
        try {
            // Get total active rules
            long totalRules = ruleRepository.count();
            metrics.setTotalActiveRules(totalRules);
            
            // Get total applications
            long totalApplications = applicationRepository.count();
            metrics.setTotalApplications(totalApplications);
            
            // Get total discount amount
            List<DiscountApplication> allApplications = applicationRepository.findAll();
            BigDecimal totalDiscount = allApplications.stream()
                .map(DiscountApplication::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            metrics.setTotalDiscountAmount(totalDiscount);
            
            log.info("System performance metrics - Rules: {}, Applications: {}, Total Discount: {}", 
                totalRules, totalApplications, totalDiscount);
            
        } catch (Exception e) {
            log.error("Error calculating system performance metrics", e);
        }
        
        return metrics;
    }
    
    /**
     * Calculate discount percentage
     */
    private BigDecimal calculateDiscountPercentage(BigDecimal originalAmount, BigDecimal discountAmount) {
        if (originalAmount.compareTo(BigDecimal.ZERO) > 0) {
            return discountAmount.divide(originalAmount, 4, BigDecimal.ROUND_HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Record metrics for monitoring systems
     */
    private void recordMetrics(BigDecimal originalAmount, BigDecimal discountAmount, 
                              Long productId, Long ruleId) {
        // This would integrate with monitoring systems like Micrometer, Prometheus, etc.
        // For now, we'll just log the metrics
        
        log.debug("Metrics recorded - Product: {}, Rule: {}, Original: {}, Discount: {}", 
            productId, ruleId, originalAmount, discountAmount);
    }
    
    /**
     * Discount Performance Metrics Data Class
     */
    public static class DiscountPerformanceMetrics {
        private Long productId;
        private long activeRuleCount;
        private int totalApplications;
        private BigDecimal totalDiscountAmount;
        private BigDecimal averageDiscountPercentage;
        
        // Getters and setters
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public long getActiveRuleCount() { return activeRuleCount; }
        public void setActiveRuleCount(long activeRuleCount) { this.activeRuleCount = activeRuleCount; }
        
        public int getTotalApplications() { return totalApplications; }
        public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
        
        public BigDecimal getTotalDiscountAmount() { return totalDiscountAmount; }
        public void setTotalDiscountAmount(BigDecimal totalDiscountAmount) { this.totalDiscountAmount = totalDiscountAmount; }
        
        public BigDecimal getAverageDiscountPercentage() { return averageDiscountPercentage; }
        public void setAverageDiscountPercentage(BigDecimal averageDiscountPercentage) { this.averageDiscountPercentage = averageDiscountPercentage; }
    }
    
    /**
     * System Performance Metrics Data Class
     */
    public static class SystemPerformanceMetrics {
        private long totalActiveRules;
        private long totalApplications;
        private BigDecimal totalDiscountAmount;
        
        // Getters and setters
        public long getTotalActiveRules() { return totalActiveRules; }
        public void setTotalActiveRules(long totalActiveRules) { this.totalActiveRules = totalActiveRules; }
        
        public long getTotalApplications() { return totalApplications; }
        public void setTotalApplications(long totalApplications) { this.totalApplications = totalApplications; }
        
        public BigDecimal getTotalDiscountAmount() { return totalDiscountAmount; }
        public void setTotalDiscountAmount(BigDecimal totalDiscountAmount) { this.totalDiscountAmount = totalDiscountAmount; }
    }
}
