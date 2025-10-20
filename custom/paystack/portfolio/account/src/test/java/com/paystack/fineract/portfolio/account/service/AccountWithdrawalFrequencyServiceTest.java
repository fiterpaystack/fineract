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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.paystack.fineract.portfolio.account.repository.SavingsAccountWithdrawalFrequencySettingRepository;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class AccountWithdrawalFrequencyServiceTest {

    @Mock
    private SavingsAccountWithdrawalFrequencySettingRepository repository;

    @InjectMocks
    private AccountWithdrawalFrequencyService service;

    private Long accountId;

    @BeforeEach
    void setUp() {
        accountId = 123L;
    }

    @Test
    void createAccountSettings_deactivatesAndSaves() {
        WithdrawalFrequencySettingData monthly = new WithdrawalFrequencySettingData(2, TimePeriod.MONTHLY, true);
        WithdrawalFrequencySettingData weekly = new WithdrawalFrequencySettingData(1, TimePeriod.WEEKLY, true);

        service.createAccountSettings(accountId, List.of(monthly, weekly));

        verify(repository, times(1)).deactivateByAccountId(eq(accountId));
        verify(repository, times(2)).save(any());
    }

    @Test
    void removeAccountSetting_deactivatesSpecific() {
        service.removeAccountSetting(accountId, TimePeriod.DAILY);
        verify(repository, times(1)).deactivateByAccountIdAndTimePeriod(eq(accountId), eq(TimePeriod.DAILY));
    }

    @Test
    void removeAllAccountSettings_deactivatesAll() {
        service.removeAllAccountSettings(accountId);
        verify(repository, times(1)).deactivateByAccountId(eq(accountId));
    }
}


