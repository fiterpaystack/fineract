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

package com.paystack.fineract.portfolio.savings.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for SavingsProductWithdrawalFrequencySetting entity
 */
class SavingsProductWithdrawalFrequencySettingTest {

    private final Long productId = 1L;
    private final Integer maxWithdrawals = 5;
    private final TimePeriod timePeriod = TimePeriod.MONTHLY;

    @Test
    void testCreate_ValidParameters() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertEquals(productId, setting.getSavingsProductId());
        assertEquals(maxWithdrawals, setting.getMaxWithdrawals());
        assertEquals(timePeriod, setting.getTimePeriod());
        assertTrue(setting.isActive());
    }

    @Test
    void testCreate_NullProductId() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SavingsProductWithdrawalFrequencySetting.create(null, maxWithdrawals, timePeriod));
        assertEquals("Product ID cannot be null", exception.getMessage());
    }

    @Test
    void testCreate_NullMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SavingsProductWithdrawalFrequencySetting.create(productId, null, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_ZeroMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SavingsProductWithdrawalFrequencySetting.create(productId, 0, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_NegativeMaxWithdrawals() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SavingsProductWithdrawalFrequencySetting.create(productId, -1, timePeriod));
        assertEquals("Max withdrawals must be a positive integer", exception.getMessage());
    }

    @Test
    void testCreate_NullTimePeriod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals, null));
        assertEquals("Time period cannot be null", exception.getMessage());
    }

    @Test
    void testIsActive_DefaultTrue() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);
        assertTrue(setting.isActive());
    }

    @Test
    void testIsActive_ExplicitlySet() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);
        setting.setIsActive(false);
        assertFalse(setting.isActive());
    }

    @Test
    void testDeactivate() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertTrue(setting.isActive());
        setting.deactivate();
        assertFalse(setting.isActive());
    }

    @Test
    void testIsLimitExceeded_NotExceeded() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertFalse(setting.isLimitExceeded(0));
        assertFalse(setting.isLimitExceeded(1));
        assertFalse(setting.isLimitExceeded(4));
    }

    @Test
    void testIsLimitExceeded_Exceeded() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertTrue(setting.isLimitExceeded(5));
        assertTrue(setting.isLimitExceeded(6));
        assertTrue(setting.isLimitExceeded(10));
    }

    @Test
    void testGetRemainingWithdrawals_WithinLimit() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertEquals(5, setting.getRemainingWithdrawals(0));
        assertEquals(4, setting.getRemainingWithdrawals(1));
        assertEquals(1, setting.getRemainingWithdrawals(4));
    }

    @Test
    void testGetRemainingWithdrawals_AtLimit() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertEquals(0, setting.getRemainingWithdrawals(5));
    }

    @Test
    void testGetRemainingWithdrawals_OverLimit() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);

        assertEquals(0, setting.getRemainingWithdrawals(6));
        assertEquals(0, setting.getRemainingWithdrawals(10));
    }

    @Test
    void testToString() {
        SavingsProductWithdrawalFrequencySetting setting = SavingsProductWithdrawalFrequencySetting.create(productId, maxWithdrawals,
                timePeriod);
        setting.setId(123L);

        String toString = setting.toString();

        assertTrue(toString.contains("SavingsProductWithdrawalFrequencySetting"));
        assertTrue(toString.contains("id=123"));
        assertTrue(toString.contains("savingsProductId=1"));
        assertTrue(toString.contains("maxWithdrawals=5"));
        assertTrue(toString.contains("timePeriod=MONTHLY"));
        assertTrue(toString.contains("isActive=true"));
    }

    @Test
    void testGettersAndSetters() {
        SavingsProductWithdrawalFrequencySetting setting = new SavingsProductWithdrawalFrequencySetting();

        setting.setSavingsProductId(productId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(false);

        assertEquals(productId, setting.getSavingsProductId());
        assertEquals(maxWithdrawals, setting.getMaxWithdrawals());
        assertEquals(timePeriod, setting.getTimePeriod());
        assertEquals(false, setting.getIsActive());
    }
}
