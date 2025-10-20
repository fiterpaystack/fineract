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

package com.paystack.fineract.portfolio.account.service;

import com.paystack.fineract.portfolio.account.domain.SavingsAccountWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.account.repository.SavingsAccountWithdrawalFrequencySettingRepository;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountWithdrawalFrequencyService {

    private final SavingsAccountWithdrawalFrequencySettingRepository accountSettingRepository;

    @Transactional
    public void createAccountSettings(Long accountId, List<WithdrawalFrequencySettingData> settingsData) {
        // Deactivate all existing active settings
        accountSettingRepository.deactivateByAccountId(accountId);

        // Insert new active settings
        for (WithdrawalFrequencySettingData data : settingsData) {
            if (data != null && data.isValid()) {
                SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
                        accountId, data.getMaxWithdrawals(), data.getTimePeriod());
                accountSettingRepository.save(setting);
            }
        }
        log.info("Applied {} withdrawal frequency settings for account {}", settingsData.size(), accountId);
    }

    @Transactional
    public void removeAccountSetting(Long accountId, TimePeriod timePeriod) {
        accountSettingRepository.deactivateByAccountIdAndTimePeriod(accountId, timePeriod);
        log.info("Deactivated withdrawal frequency setting for account {} and period {}", accountId, timePeriod);
    }

    @Transactional
    public void removeAllAccountSettings(Long accountId) {
        accountSettingRepository.deactivateByAccountId(accountId);
        log.info("Deactivated all withdrawal frequency settings for account {}", accountId);
    }
}


