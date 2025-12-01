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

import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.repository.DiscountApplicationRepository;
import com.paystack.fineract.portfolio.discount.repository.DiscountRuleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Discount Rule Read Platform Service Handles read operations for discount rules
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountRuleReadPlatformService {

    private final DiscountRuleRepository ruleRepository;
    private final DiscountApplicationRepository applicationRepository;

    /**
     * Retrieve all discount rules
     */
    @Transactional(readOnly = true)
    public List<DiscountRuleData> retrieveAllDiscountRules() {
        List<DiscountRule> rules = ruleRepository.findAll();
        return rules.stream().map(this::mapToData).collect(Collectors.toList());
    }

    /**
     * Retrieve discount rule by ID
     */
    @Transactional(readOnly = true)
    public DiscountRuleData retrieveDiscountRule(Long ruleId) {
        DiscountRule rule = ruleRepository.findById(ruleId).orElseThrow(() -> new RuntimeException("Discount rule not found: " + ruleId));
        return mapToData(rule);
    }

    /**
     * Retrieve discount rules by entity
     */
    @Transactional(readOnly = true)
    public List<DiscountRuleData> retrieveDiscountRulesByEntity(String entityType, Long entityId) {
        // This method is now handled by the assignment service
        // Keeping for backward compatibility but delegating to assignment service
        return List.of();
    }

    /**
     * Map domain entity to data transfer object
     */
    private DiscountRuleData mapToData(DiscountRule rule) {
        DiscountRuleData data = new DiscountRuleData();
        data.setId(rule.getId());
        data.setName(rule.getName());
        data.setDescription(rule.getDescription());
        data.setActive(rule.isActive());
        data.setRulePriority(rule.getRulePriority());
        data.setRuleType(rule.getRuleType());
        data.setRuleParametersJson(rule.getRuleParametersJson());
        data.setCreatedOnUtc(rule.getCreatedDate().orElse(null));
        data.setLastModifiedOnUtc(rule.getLastModifiedDate().orElse(null));
        data.setCreatedBy(rule.getCreatedBy().orElse(null));
        data.setLastModifiedBy(rule.getLastModifiedBy().orElse(null));
        
        // Populate statistics
        Long applicationCount = applicationRepository.countByDiscountRuleId(rule.getId());
        data.setApplicationCount(applicationCount);
        
        BigDecimal totalDiscountAmount = applicationRepository.getTotalDiscountAmountByRule(rule.getId());
        data.setTotalDiscountAmount(totalDiscountAmount != null ? totalDiscountAmount : BigDecimal.ZERO);
        
        return data;
    }
}
