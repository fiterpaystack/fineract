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

import static org.junit.jupiter.api.Assertions.*;

import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SavingsAccountWithdrawalFrequencySetting entity
 */
class SavingsAccountWithdrawalFrequencySettingTest {

    private final Long accountId = 1L;
    private final Integer maxWithdrawals = 3;
    private final TimePeriod timePeriod = TimePeriod.WEEKLY;

    @Test
    void testCreate_ValidParameters() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertEquals(accountId, setting.getSavingsAccountId());
        assertEquals(maxWithdrawals, setting.getMaxWithdrawals());
        assertEquals(timePeriod, setting.getTimePeriod());
        assertTrue(setting.isActive());
    }

    @Test
    void testCreate_NullAccountId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> SavingsAccountWithdrawalFrequencySetting.create(null, maxWithdrawals, timePeriod));
        assertEquals("Account ID cannot be null", exception.getMessage());
    }

    @Test
    void testCreate_NullMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> SavingsAccountWithdrawalFrequencySetting.create(accountId, null, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_ZeroMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> SavingsAccountWithdrawalFrequencySetting.create(accountId, 0, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_NegativeMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> SavingsAccountWithdrawalFrequencySetting.create(accountId, -1, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_NullTimePeriod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> SavingsAccountWithdrawalFrequencySetting.create(accountId, maxWithdrawals, null));
        assertEquals("Time period cannot be null", exception.getMessage());
    }

    @Test
    void testIsActive_DefaultTrue() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        assertTrue(setting.isActive());
    }

    @Test
    void testIsActive_ExplicitlySet() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        setting.setIsActive(false);
        assertFalse(setting.isActive());
    }

    @Test
    void testDeactivate() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertTrue(setting.isActive());
        setting.deactivate();
        assertFalse(setting.isActive());
    }

    @Test
    void testIsLimitExceeded_NotExceeded() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertFalse(setting.isLimitExceeded(0));
        assertFalse(setting.isLimitExceeded(1));
        assertFalse(setting.isLimitExceeded(2));
    }

    @Test
    void testIsLimitExceeded_Exceeded() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertTrue(setting.isLimitExceeded(3));
        assertTrue(setting.isLimitExceeded(4));
        assertTrue(setting.isLimitExceeded(10));
    }

    @Test
    void testGetRemainingWithdrawals_WithinLimit() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertEquals(3, setting.getRemainingWithdrawals(0));
        assertEquals(2, setting.getRemainingWithdrawals(1));
        assertEquals(1, setting.getRemainingWithdrawals(2));
    }

    @Test
    void testGetRemainingWithdrawals_AtLimit() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertEquals(0, setting.getRemainingWithdrawals(3));
    }

    @Test
    void testGetRemainingWithdrawals_OverLimit() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        
        assertEquals(0, setting.getRemainingWithdrawals(4));
        assertEquals(0, setting.getRemainingWithdrawals(10));
    }

    @Test
    void testToString() {
        SavingsAccountWithdrawalFrequencySetting setting = SavingsAccountWithdrawalFrequencySetting.create(
            accountId, maxWithdrawals, timePeriod);
        setting.setId(456L);
        
        String toString = setting.toString();
        
        assertTrue(toString.contains("SavingsAccountWithdrawalFrequencySetting"));
        assertTrue(toString.contains("id=456"));
        assertTrue(toString.contains("savingsAccountId=1"));
        assertTrue(toString.contains("maxWithdrawals=3"));
        assertTrue(toString.contains("timePeriod=WEEKLY"));
        assertTrue(toString.contains("isActive=true"));
    }

    @Test
    void testGettersAndSetters() {
        SavingsAccountWithdrawalFrequencySetting setting = new SavingsAccountWithdrawalFrequencySetting();
        
        setting.setSavingsAccountId(accountId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(false);
        
        assertEquals(accountId, setting.getSavingsAccountId());
        assertEquals(maxWithdrawals, setting.getMaxWithdrawals());
        assertEquals(timePeriod, setting.getTimePeriod());
        assertEquals(false, setting.getIsActive());
    }
}
