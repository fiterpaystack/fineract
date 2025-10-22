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
        // Load existing settings (active and inactive) for upsert/deactivate logic
        List<SavingsProductWithdrawalFrequencySetting> existing = productSettingRepository.findBySavingsProductId(productId);

        Map<TimePeriod, SavingsProductWithdrawalFrequencySetting> existingByPeriod = new HashMap<>();
        for (SavingsProductWithdrawalFrequencySetting s : existing) {
            existingByPeriod.putIfAbsent(s.getTimePeriod(), s); // prefer the first found
        }

        // Track incoming periods to deactivate missing ones
        Map<TimePeriod, WithdrawalFrequencySettingData> incomingByPeriod = new HashMap<>();
        for (WithdrawalFrequencySettingData data : settingsData) {
            if (data != null && data.isValid()) {
                incomingByPeriod.put(data.getTimePeriod(), data);
            }
        }

        // Upsert incoming settings
        for (Map.Entry<TimePeriod, WithdrawalFrequencySettingData> entry : incomingByPeriod.entrySet()) {
            TimePeriod period = entry.getKey();
            WithdrawalFrequencySettingData data = entry.getValue();

            SavingsProductWithdrawalFrequencySetting existingSetting = existingByPeriod.get(period);
            if (existingSetting != null) {
                existingSetting.setMaxWithdrawals(data.getMaxWithdrawals());
                existingSetting.setIsActive(Boolean.TRUE.equals(data.getIsActive()));
                productSettingRepository.save(existingSetting);
            } else {
                SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(
                    productId, data.getMaxWithdrawals(), data.getTimePeriod());
                productSettingRepository.save(setting);
            }
        }

        // Deactivate settings that are not present in the incoming list
        for (SavingsProductWithdrawalFrequencySetting s : existing) {
            if (!incomingByPeriod.containsKey(s.getTimePeriod()) && Boolean.TRUE.equals(s.getIsActive())) {
                s.setIsActive(false);
                productSettingRepository.save(s);
            }
        }
    }
    
    @Transactional
    public void updateProductSetting(Long productId, TimePeriod timePeriod, WithdrawalFrequencySettingData data) {
        // Upsert the single period setting
        productSettingRepository.findBySavingsProductIdAndTimePeriod(productId, timePeriod)
            .ifPresentOrElse(existing -> {
                if (data != null && data.isValid()) {
                    existing.setMaxWithdrawals(data.getMaxWithdrawals());
                    existing.setIsActive(Boolean.TRUE.equals(data.getIsActive()));
                    productSettingRepository.save(existing);
                } else {
                    productSettingRepository.deactivateByProductIdAndTimePeriod(productId, timePeriod);
                }
            }, () -> {
                if (data != null && data.isValid()) {
                    SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(
                        productId, data.getMaxWithdrawals(), data.getTimePeriod());
                    productSettingRepository.save(setting);
                }
            });
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