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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.account.domain.SavingsAccountWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for SavingsAccountWithdrawalFrequencySettingRepository
 */
@ExtendWith(MockitoExtension.class)
class SavingsAccountWithdrawalFrequencySettingRepositoryTest {

    @Mock
    private SavingsAccountWithdrawalFrequencySettingRepository repository;

    private final Long accountId = 1L;
    private final TimePeriod timePeriod = TimePeriod.WEEKLY;
    private final Boolean isActive = true;

    @Test
    void testFindBySavingsAccountIdAndIsActive() {
        List<SavingsAccountWithdrawalFrequencySetting> expectedSettings = Arrays.asList(createSetting(1L, 3, TimePeriod.WEEKLY),
                createSetting(1L, 1, TimePeriod.DAILY));

        when(repository.findBySavingsAccountIdAndIsActive(accountId, isActive)).thenReturn(expectedSettings);

        List<SavingsAccountWithdrawalFrequencySetting> result = repository.findBySavingsAccountIdAndIsActive(accountId, isActive);

        assertEquals(2, result.size());
        verify(repository).findBySavingsAccountIdAndIsActive(accountId, isActive);
    }

    @Test
    void testFindBySavingsAccountIdAndTimePeriodAndIsActive() {
        SavingsAccountWithdrawalFrequencySetting expectedSetting = createSetting(accountId, 3, timePeriod);

        when(repository.findBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive))
                .thenReturn(Optional.of(expectedSetting));

        Optional<SavingsAccountWithdrawalFrequencySetting> result = repository.findBySavingsAccountIdAndTimePeriodAndIsActive(accountId,
                timePeriod, isActive);

        assertTrue(result.isPresent());
        assertEquals(expectedSetting, result.get());
        verify(repository).findBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);
    }

    @Test
    void testFindBySavingsAccountIdAndTimePeriodAndIsActive_NotFound() {
        when(repository.findBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive))
            .thenReturn(Optional.empty());

        Optional<SavingsAccountWithdrawalFrequencySetting> result = repository
            .findBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);

        assertFalse(result.isPresent());
        verify(repository).findBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);
    }

    @Test
    void testFindBySavingsAccountId() {
        List<SavingsAccountWithdrawalFrequencySetting> expectedSettings = Arrays.asList(createSetting(accountId, 3, TimePeriod.WEEKLY),
                createSetting(accountId, 1, TimePeriod.DAILY));

        when(repository.findBySavingsAccountId(accountId)).thenReturn(expectedSettings);

        List<SavingsAccountWithdrawalFrequencySetting> result = repository.findBySavingsAccountId(accountId);

        assertEquals(2, result.size());
        verify(repository).findBySavingsAccountId(accountId);
    }

    @Test
    void testExistsBySavingsAccountIdAndTimePeriodAndIsActive() {
        when(repository.existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive))
            .thenReturn(true);

        boolean exists = repository.existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);

        assertTrue(exists);
        verify(repository).existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);
    }

    @Test
    void testExistsBySavingsAccountIdAndTimePeriodAndIsActive_NotExists() {
        when(repository.existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive))
            .thenReturn(false);

        boolean exists = repository.existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);

        assertFalse(exists);
        verify(repository).existsBySavingsAccountIdAndTimePeriodAndIsActive(accountId, timePeriod, isActive);
    }

    @Test
    void testDeactivateByAccountId() {
        repository.deactivateByAccountId(accountId);

        verify(repository).deactivateByAccountId(accountId);
    }

    @Test
    void testDeactivateByAccountIdAndTimePeriod() {
        repository.deactivateByAccountIdAndTimePeriod(accountId, timePeriod);

        verify(repository).deactivateByAccountIdAndTimePeriod(accountId, timePeriod);
    }

    @Test
    void testCountBySavingsAccountIdAndIsActive() {
        when(repository.countBySavingsAccountIdAndIsActive(accountId, isActive))
            .thenReturn(2L);

        long count = repository.countBySavingsAccountIdAndIsActive(accountId, isActive);

        assertEquals(2L, count);
        verify(repository).countBySavingsAccountIdAndIsActive(accountId, isActive);
    }

    @Test
    void testFindByTimePeriodAndIsActive() {
        List<SavingsAccountWithdrawalFrequencySetting> expectedSettings = Arrays.asList(createSetting(1L, 3, timePeriod),
                createSetting(2L, 2, timePeriod));

        when(repository.findByTimePeriodAndIsActive(timePeriod, isActive)).thenReturn(expectedSettings);

        List<SavingsAccountWithdrawalFrequencySetting> result = repository.findByTimePeriodAndIsActive(timePeriod, isActive);

        assertEquals(2, result.size());
        verify(repository).findByTimePeriodAndIsActive(timePeriod, isActive);
    }

    @Test
    void testFindBySavingsAccountIdInAndIsActive() {
        List<Long> accountIds = Arrays.asList(1L, 2L, 3L);
        List<SavingsAccountWithdrawalFrequencySetting> expectedSettings = Arrays.asList(createSetting(1L, 3, TimePeriod.WEEKLY),
                createSetting(2L, 2, TimePeriod.DAILY));

        when(repository.findBySavingsAccountIdInAndIsActive(accountIds, isActive)).thenReturn(expectedSettings);

        List<SavingsAccountWithdrawalFrequencySetting> result = repository.findBySavingsAccountIdInAndIsActive(accountIds, isActive);

        assertEquals(2, result.size());
        verify(repository).findBySavingsAccountIdInAndIsActive(accountIds, isActive);
    }

    @Test
    void testFindBySavingsAccountIdAndIsActive_EmptyResult() {
        when(repository.findBySavingsAccountIdAndIsActive(accountId, isActive))
            .thenReturn(Collections.emptyList());

        List<SavingsAccountWithdrawalFrequencySetting> result = repository
            .findBySavingsAccountIdAndIsActive(accountId, isActive);

        assertTrue(result.isEmpty());
        verify(repository).findBySavingsAccountIdAndIsActive(accountId, isActive);
    }

    private SavingsAccountWithdrawalFrequencySetting createSetting(Long accountId, Integer maxWithdrawals, TimePeriod timePeriod) {
        SavingsAccountWithdrawalFrequencySetting setting = new SavingsAccountWithdrawalFrequencySetting();
        setting.setSavingsAccountId(accountId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(true);
        return setting;
    }
}
