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

package com.paystack.fineract.portfolio.account.domain;

import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Savings Account Withdrawal Frequency Setting Entity
 * Represents withdrawal frequency limits configured at the account level
 * These settings override product-level settings for the same time period
 * Each account can have multiple settings for different time periods (daily, weekly, monthly, yearly)
 */
@Entity
@Table(name = "m_savings_account_withdrawal_frequency_setting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"savings_account_id", "time_period"}, 
                                           name = "uq_sawfs_account_period"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavingsAccountWithdrawalFrequencySetting extends AbstractAuditableWithUTCDateTimeCustom<Long> {
    
    @Column(name = "savings_account_id", nullable = false)
    private Long savingsAccountId;
    
    @Column(name = "max_withdrawals", nullable = false)
    private Integer maxWithdrawals;
    
    @Column(name = "time_period", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TimePeriod timePeriod;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    /**
     * Create a new withdrawal frequency setting for a savings account
     */
    public static SavingsAccountWithdrawalFrequencySetting create(Long accountId, Integer maxWithdrawals, TimePeriod timePeriod) {
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID cannot be null");
        }
        if (maxWithdrawals == null || maxWithdrawals <= 0) {
            throw new IllegalArgumentException("Max withdrawals must be a positive integer");
        }
        if (timePeriod == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }
        
        SavingsAccountWithdrawalFrequencySetting setting = new SavingsAccountWithdrawalFrequencySetting();
        setting.setSavingsAccountId(accountId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(true);
        return setting;
    }
    
    /**
     * Check if this setting is currently active
     */
    public boolean isActive() {
        return Boolean.TRUE.equals(this.isActive);
    }
    
    /**
     * Deactivate this setting
     */
    public void deactivate() {
        this.isActive = false;
    }
    
    /**
     * Check if the given number of withdrawals exceeds the limit
     */
    public boolean isLimitExceeded(int currentWithdrawals) {
        return currentWithdrawals >= this.maxWithdrawals;
    }
    
    /**
     * Get the remaining number of withdrawals allowed
     */
    public int getRemainingWithdrawals(int currentWithdrawals) {
        return Math.max(0, this.maxWithdrawals - currentWithdrawals);
    }
    
    @Override
    public String toString() {
        return "SavingsAccountWithdrawalFrequencySetting{" +
                "id=" + getId() +
                ", savingsAccountId=" + savingsAccountId +
                ", maxWithdrawals=" + maxWithdrawals +
                ", timePeriod=" + timePeriod +
                ", isActive=" + isActive +
                '}';
    }
}
