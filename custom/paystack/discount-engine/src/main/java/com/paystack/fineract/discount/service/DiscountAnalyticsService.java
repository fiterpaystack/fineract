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
import com.paystack.fineract.discount.domain.DiscountContext;
import com.paystack.fineract.discount.repository.DiscountApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Discount Analytics Service
 * Provides analytics and reporting for discount applications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountAnalyticsService {
    
    private final DiscountApplicationRepository applicationRepository;
    
    /**
     * Generate analytics report for a product
     */
    public DiscountAnalyticsReport generateAnalyticsReport(
            LocalDate fromDate, LocalDate toDate, Long productId) {
        
        log.debug("Generating analytics report for product: {} from {} to {}", 
            productId, fromDate, toDate);
        
        DiscountAnalyticsReport report = new DiscountAnalyticsReport();
        report.setFromDate(fromDate);
        report.setToDate(toDate);
        report.setProductId(productId);
        
        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(23, 59, 59);
        
        List<DiscountApplication> applications = applicationRepository
            .findByDateRangeAndProductId(startDateTime, endDateTime, productId);
        
        report.setTotalApplications(applications.size());
        
        if (!applications.isEmpty()) {
            BigDecimal totalOriginalAmount = applications.stream()
                .map(DiscountApplication::getOriginalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalDiscountAmount = applications.stream()
                .map(DiscountApplication::getDiscountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalFinalAmount = applications.stream()
                .map(DiscountApplication::getFinalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            report.setTotalOriginalAmount(totalOriginalAmount);
            report.setTotalDiscountAmount(totalDiscountAmount);
            report.setTotalFinalAmount(totalFinalAmount);
            
            // Calculate average discount percentage
            if (totalOriginalAmount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal averageDiscountPercentage = totalDiscountAmount
                    .divide(totalOriginalAmount, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
                report.setAverageDiscountPercentage(averageDiscountPercentage);
            } else {
                report.setAverageDiscountPercentage(BigDecimal.ZERO);
            }
        } else {
            report.setTotalOriginalAmount(BigDecimal.ZERO);
            report.setTotalDiscountAmount(BigDecimal.ZERO);
            report.setTotalFinalAmount(BigDecimal.ZERO);
            report.setAverageDiscountPercentage(BigDecimal.ZERO);
        }
        
        log.info("Analytics report generated. Total applications: {}, Total discount: {}", 
            report.getTotalApplications(), report.getTotalDiscountAmount());
        
        return report;
    }
    
    /**
     * Get discount applications for a product
     */
    public List<DiscountApplication> getDiscountApplications(Long productId) {
        return applicationRepository.findByProductId(productId);
    }
    
    /**
     * Get discount applications by date range
     */
    public List<DiscountApplication> getDiscountApplicationsByDateRange(
            LocalDate fromDate, LocalDate toDate) {
        
        LocalDateTime startDateTime = fromDate.atStartOfDay();
        LocalDateTime endDateTime = toDate.atTime(23, 59, 59);
        
        return applicationRepository.findByApplicationDateBetween(startDateTime, endDateTime);
    }
    
    /**
     * Calculate total discount amount for a product
     */
    public BigDecimal getTotalDiscountAmount(Long productId) {
        Double totalAmount = applicationRepository.sumDiscountAmountByProductId(productId);
        return totalAmount != null ? BigDecimal.valueOf(totalAmount) : BigDecimal.ZERO;
    }
    
    /**
     * Count discount applications for a rule
     */
    public long countApplicationsByRule(Long ruleId) {
        return applicationRepository.countByDiscountRuleId(ruleId);
    }
    
    /**
     * Discount Analytics Report Data Class
     */
    public static class DiscountAnalyticsReport {
        private LocalDate fromDate;
        private LocalDate toDate;
        private Long productId;
        private int totalApplications;
        private BigDecimal totalOriginalAmount;
        private BigDecimal totalDiscountAmount;
        private BigDecimal totalFinalAmount;
        private BigDecimal averageDiscountPercentage;
        
        // Getters and setters
        public LocalDate getFromDate() { return fromDate; }
        public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
        
        public LocalDate getToDate() { return toDate; }
        public void setToDate(LocalDate toDate) { this.toDate = toDate; }
        
        public Long getProductId() { return productId; }
        public void setProductId(Long productId) { this.productId = productId; }
        
        public int getTotalApplications() { return totalApplications; }
        public void setTotalApplications(int totalApplications) { this.totalApplications = totalApplications; }
        
        public BigDecimal getTotalOriginalAmount() { return totalOriginalAmount; }
        public void setTotalOriginalAmount(BigDecimal totalOriginalAmount) { this.totalOriginalAmount = totalOriginalAmount; }
        
        public BigDecimal getTotalDiscountAmount() { return totalDiscountAmount; }
        public void setTotalDiscountAmount(BigDecimal totalDiscountAmount) { this.totalDiscountAmount = totalDiscountAmount; }
        
        public BigDecimal getTotalFinalAmount() { return totalFinalAmount; }
        public void setTotalFinalAmount(BigDecimal totalFinalAmount) { this.totalFinalAmount = totalFinalAmount; }
        
        public BigDecimal getAverageDiscountPercentage() { return averageDiscountPercentage; }
        public void setAverageDiscountPercentage(BigDecimal averageDiscountPercentage) { this.averageDiscountPercentage = averageDiscountPercentage; }
    }
}
