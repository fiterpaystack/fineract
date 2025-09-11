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

import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.discount.repository.ProductDiscountRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Discount Cache Service
 * Provides caching functionality for discount rules to improve performance
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountCacheService {
    
    private final ProductDiscountRuleRepository ruleRepository;
    
    /**
     * Get cached discount rules for a product
     */
    @Cacheable(value = "discountRules", key = "#productId")
    public List<ProductDiscountRule> getCachedDiscountRules(Long productId) {
        log.debug("Loading discount rules from database for product: {}", productId);
        return ruleRepository.findByProductIdAndActiveAndWithinValidityPeriod(
            productId, true, LocalDate.now());
    }
    
    /**
     * Get cached applicable rules for a product
     */
    @Cacheable(value = "applicableRules", key = "#productId")
    public List<ProductDiscountRule> getCachedApplicableRules(Long productId) {
        log.debug("Loading applicable discount rules from database for product: {}", productId);
        return ruleRepository.findApplicableRules(productId, LocalDate.now());
    }
    
    /**
     * Evict cache for a product when rules are updated
     */
    @CacheEvict(value = {"discountRules", "applicableRules"}, key = "#productId")
    public void evictProductCache(Long productId) {
        log.debug("Evicting cache for product: {}", productId);
    }
    
    /**
     * Evict all discount rule caches
     */
    @CacheEvict(value = {"discountRules", "applicableRules"}, allEntries = true)
    public void evictAllCache() {
        log.debug("Evicting all discount rule caches");
    }
    
    /**
     * Warm up cache for a product
     */
    public void warmUpCache(Long productId) {
        log.debug("Warming up cache for product: {}", productId);
        getCachedDiscountRules(productId);
        getCachedApplicableRules(productId);
    }
    
    /**
     * Warm up cache for all active products
     */
    public void warmUpAllCache() {
        log.debug("Warming up cache for all products");
        // This would typically get all active product IDs and warm up their caches
        // Implementation depends on how products are managed in the system
    }
}
