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

package com.paystack.fineract.portfolio.account.repository;

import com.paystack.fineract.portfolio.account.domain.SavingsAccountWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for SavingsAccountWithdrawalFrequencySetting
 * Handles database operations for account-level withdrawal frequency settings
 */
@Repository
public interface SavingsAccountWithdrawalFrequencySettingRepository extends JpaRepository<SavingsAccountWithdrawalFrequencySetting, Long> {
    
    /**
     * Find all active settings for a savings account
     */
    List<SavingsAccountWithdrawalFrequencySetting> findBySavingsAccountIdAndIsActive(Long accountId, Boolean isActive);
    
    /**
     * Find a specific setting for an account and time period
     */
    Optional<SavingsAccountWithdrawalFrequencySetting> findBySavingsAccountIdAndTimePeriodAndIsActive(
        Long accountId, TimePeriod timePeriod, Boolean isActive);
    
    /**
     * Find all settings for an account (active and inactive)
     */
    List<SavingsAccountWithdrawalFrequencySetting> findBySavingsAccountId(Long accountId);
    
    /**
     * Check if a setting exists for an account and time period
     */
    boolean existsBySavingsAccountIdAndTimePeriodAndIsActive(Long accountId, TimePeriod timePeriod, Boolean isActive);
    
    /**
     * Deactivate all settings for an account
     */
    @Modifying
    @Query("UPDATE SavingsAccountWithdrawalFrequencySetting s SET s.isActive = false WHERE s.savingsAccountId = :accountId")
    void deactivateByAccountId(@Param("accountId") Long accountId);
    
    /**
     * Deactivate a specific setting for an account and time period
     */
    @Modifying
    @Query("UPDATE SavingsAccountWithdrawalFrequencySetting s SET s.isActive = false WHERE s.savingsAccountId = :accountId AND s.timePeriod = :timePeriod")
    void deactivateByAccountIdAndTimePeriod(@Param("accountId") Long accountId, @Param("timePeriod") TimePeriod timePeriod);
    
    /**
     * Count active settings for an account
     */
    long countBySavingsAccountIdAndIsActive(Long accountId, Boolean isActive);
    
    /**
     * Find settings by time period across all accounts
     */
    List<SavingsAccountWithdrawalFrequencySetting> findByTimePeriodAndIsActive(TimePeriod timePeriod, Boolean isActive);
    
    /**
     * Find settings for multiple accounts
     */
    List<SavingsAccountWithdrawalFrequencySetting> findBySavingsAccountIdInAndIsActive(List<Long> accountIds, Boolean isActive);
}
