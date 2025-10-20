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

package com.paystack.fineract.portfolio.savings.service;

import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencyStatus;
import com.paystack.fineract.portfolio.savings.domain.PeriodBoundaries;
import com.paystack.fineract.portfolio.savings.domain.SavingsProductWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import com.paystack.fineract.portfolio.savings.repository.SavingsProductWithdrawalFrequencySettingRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing withdrawal frequency controls
 * Handles product-level withdrawal frequency settings
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WithdrawalFrequencyService {
    
    private final PaystackSavingsAccountTransactionRepository transactionRepository;
    private final SavingsProductWithdrawalFrequencySettingRepository productSettingRepository;
    private final PeriodCalculationService periodCalculationService;
    
    /**
     * Get all effective withdrawal frequency settings for an account
     * Based on product-level settings only
     */
    public List<WithdrawalFrequencySettingData> getEffectiveSettings(SavingsAccount account) {
        Map<TimePeriod, WithdrawalFrequencySettingData> effectiveSettings = new HashMap<>();
        
        // Get product-level settings only
        List<SavingsProductWithdrawalFrequencySetting> productSettings = 
            productSettingRepository.findBySavingsProductIdAndIsActive(account.savingsProduct().getId(), true);
        
        for (SavingsProductWithdrawalFrequencySetting productSetting : productSettings) {
            effectiveSettings.put(productSetting.getTimePeriod(), convertToSettingData(productSetting));
        }
        
        return new ArrayList<>(effectiveSettings.values());
    }
    
    /**
     * Check if withdrawal is allowed based on ALL applicable rules
     * Withdrawal is blocked if ANY rule is violated
     */
    public boolean isWithdrawalAllowed(SavingsAccount account, LocalDate withdrawalDate) {
        List<WithdrawalFrequencySettingData> settings = getEffectiveSettings(account);
        
        if (settings.isEmpty()) {
            return true; // No restrictions
        }
        
        // Check each time period rule
        for (WithdrawalFrequencySettingData setting : settings) {
            if (setting.isValid()) {
                int currentCount = getWithdrawalCountForPeriod(account, withdrawalDate, setting.getTimePeriod());
                if (currentCount >= setting.getMaxWithdrawals()) {
                    log.info("Withdrawal blocked for account {} - {} limit exceeded: {}/{}", 
                        account.getId(), setting.getTimePeriod(), currentCount, setting.getMaxWithdrawals());
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Get withdrawal status for all time periods
     */
    public WithdrawalFrequencyStatus getWithdrawalStatus(SavingsAccount account, LocalDate checkDate) {
        List<WithdrawalFrequencySettingData> settings = getEffectiveSettings(account);
        List<WithdrawalFrequencyStatus.PeriodStatus> periodStatuses = new ArrayList<>();
        
        for (WithdrawalFrequencySettingData setting : settings) {
            if (setting.isValid()) {
                int currentCount = getWithdrawalCountForPeriod(account, checkDate, setting.getTimePeriod());
                boolean allowed = currentCount < setting.getMaxWithdrawals();
                int remaining = Math.max(0, setting.getMaxWithdrawals() - currentCount);
                
                periodStatuses.add(new WithdrawalFrequencyStatus.PeriodStatus(
                    setting.getTimePeriod(),
                    setting.getMaxWithdrawals(),
                    currentCount,
                    remaining,
                    allowed
                ));
            }
        }
        
        boolean withdrawalAllowed = periodStatuses.stream().allMatch(WithdrawalFrequencyStatus.PeriodStatus::isAllowed);
        return new WithdrawalFrequencyStatus(withdrawalAllowed, periodStatuses);
    }
    
    private int getWithdrawalCountForPeriod(SavingsAccount account, LocalDate withdrawalDate, TimePeriod timePeriod) {
        PeriodBoundaries period = periodCalculationService.calculatePeriod(withdrawalDate, timePeriod);
        
        // Use existing repository method
        List<Integer> withdrawalTypes = List.of(2); // WITHDRAWAL transaction type
        return (int) transactionRepository.countTransactionsForPeriod(
            account.getId(), 
            period.getStartDate(), 
            period.getEndDate(), 
            false, // exclude reversed
            withdrawalTypes
        );
    }
    
    // Product-level operations
    @Transactional
    public void createProductSettings(Long productId, List<WithdrawalFrequencySettingData> settingsData) {
        // Deactivate all existing settings
        productSettingRepository.deactivateByProductId(productId);
        
        // Create new settings
        for (WithdrawalFrequencySettingData data : settingsData) {
            if (data.isValid()) {
                SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(
                    productId, data.getMaxWithdrawals(), data.getTimePeriod());
                productSettingRepository.save(setting);
            }
        }
    }
    
    @Transactional
    public void updateProductSetting(Long productId, TimePeriod timePeriod, WithdrawalFrequencySettingData data) {
        // Deactivate existing setting for this time period
        productSettingRepository.deactivateByProductIdAndTimePeriod(productId, timePeriod);
        
        // Create new setting if data is provided
        if (data.isValid()) {
            SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(
                productId, data.getMaxWithdrawals(), data.getTimePeriod());
            productSettingRepository.save(setting);
        }
    }
    
    // Account-level operations are implemented in the account module service

    // Helper methods
    private WithdrawalFrequencySettingData convertToSettingData(SavingsProductWithdrawalFrequencySetting setting) {
        return new WithdrawalFrequencySettingData(
            setting.getMaxWithdrawals(),
            setting.getTimePeriod(),
            setting.isActive()
        );
    }
    
}