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

package com.paystack.fineract.portfolio.savings.data;

import static org.junit.jupiter.api.Assertions.*;

import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WithdrawalFrequencyStatus
 */
class WithdrawalFrequencyStatusTest {

    @Test
    void testConstructor_WithPeriodStatuses() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus1 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 2, 5, 3, true);
        WithdrawalFrequencyStatus.PeriodStatus periodStatus2 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.WEEKLY, 1, 3, 2, true);
        
        List<WithdrawalFrequencyStatus.PeriodStatus> periodStatuses = Arrays.asList(periodStatus1, periodStatus2);
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(true, periodStatuses);
        
        assertTrue(status.isWithdrawalAllowed());
        assertEquals(2, status.getPeriodStatuses().size());
        assertEquals(periodStatus1, status.getPeriodStatuses().get(0));
        assertEquals(periodStatus2, status.getPeriodStatuses().get(1));
    }

    @Test
    void testConstructor_EmptyPeriodStatuses() {
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(true, Collections.emptyList());
        
        assertTrue(status.isWithdrawalAllowed());
        assertTrue(status.getPeriodStatuses().isEmpty());
        assertNull(status.getMostRestrictivePeriod());
    }

    @Test
    void testGetMostRestrictivePeriod_SinglePeriod() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 5, 4, 1, false);
        
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(false, Arrays.asList(periodStatus));
        
        assertEquals(periodStatus, status.getMostRestrictivePeriod());
    }

    @Test
    void testGetMostRestrictivePeriod_MultiplePeriods() {
        WithdrawalFrequencyStatus.PeriodStatus dailyStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 2, 1, 1, false);
        WithdrawalFrequencyStatus.PeriodStatus weeklyStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.WEEKLY, 5, 2, 3, false);
        WithdrawalFrequencyStatus.PeriodStatus monthlyStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 20, 10, 10, false);
        
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(false, 
            Arrays.asList(dailyStatus, weeklyStatus, monthlyStatus));
        
        // DAILY should be most restrictive (lowest remaining)
        assertEquals(dailyStatus, status.getMostRestrictivePeriod());
    }

    @Test
    void testGetMostRestrictivePeriod_NoPeriods() {
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(true, Collections.emptyList());
        
        assertNull(status.getMostRestrictivePeriod());
    }

    @Test
    void testPeriodStatus_Constructor() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.WEEKLY, 5, 3, 2, false);
        
        assertEquals(TimePeriod.WEEKLY, periodStatus.getTimePeriod());
        assertEquals(5, periodStatus.getMaxWithdrawals());
        assertEquals(3, periodStatus.getCurrentCount());
        assertEquals(2, periodStatus.getRemaining());
        assertFalse(periodStatus.isAllowed());
    }

    @Test
    void testPeriodStatus_IsLimitExceeded() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 5, 5, 0, false);
        
        assertTrue(periodStatus.isLimitExceeded());
    }

    @Test
    void testPeriodStatus_IsNotLimitExceeded() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 3, 5, 2, true);
        
        assertFalse(periodStatus.isLimitExceeded());
    }

    @Test
    void testPeriodStatus_GetUsagePercentage() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 5, 3, 2, true);
        
        assertEquals(60.0, periodStatus.getUsagePercentage(), 0.01);
    }

    @Test
    void testPeriodStatus_GetUsagePercentage_ZeroMax() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 3, 0, 0, false);
        
        assertEquals(0.0, periodStatus.getUsagePercentage(), 0.01);
    }

    @Test
    void testPeriodStatus_ToString() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 5, 2, 3, true);
        
        String toString = periodStatus.toString();
        
        assertTrue(toString.contains("PeriodStatus"));
        assertTrue(toString.contains("timePeriod=DAILY"));
        assertTrue(toString.contains("currentCount=2"));
        assertTrue(toString.contains("maxWithdrawals=5"));
        assertTrue(toString.contains("remaining=3"));
        assertTrue(toString.contains("allowed=true"));
    }

    @Test
    void testPeriodStatus_Equals() {
        WithdrawalFrequencyStatus.PeriodStatus status1 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 2, 5, 3, true);
        WithdrawalFrequencyStatus.PeriodStatus status2 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 2, 5, 3, true);
        
        assertEquals(status1, status2);
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void testPeriodStatus_NotEquals() {
        WithdrawalFrequencyStatus.PeriodStatus status1 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.DAILY, 2, 5, 3, true);
        WithdrawalFrequencyStatus.PeriodStatus status2 = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.WEEKLY, 2, 5, 3, true);
        
        assertNotEquals(status1, status2);
    }

    @Test
    void testToString() {
        WithdrawalFrequencyStatus.PeriodStatus periodStatus = new WithdrawalFrequencyStatus.PeriodStatus(
            TimePeriod.MONTHLY, 4, 5, 1, true);
        
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus(true, Arrays.asList(periodStatus));
        
        String toString = status.toString();
        
        assertTrue(toString.contains("WithdrawalFrequencyStatus"));
        assertTrue(toString.contains("withdrawalAllowed=true"));
        assertTrue(toString.contains("periodStatuses="));
    }

    @Test
    void testGettersAndSetters() {
        WithdrawalFrequencyStatus status = new WithdrawalFrequencyStatus();
        
        status.setWithdrawalAllowed(false);
        status.setPeriodStatuses(Arrays.asList(
            new WithdrawalFrequencyStatus.PeriodStatus(TimePeriod.DAILY, 1, 2, 1, true)
        ));
        
        assertFalse(status.isWithdrawalAllowed());
        assertEquals(1, status.getPeriodStatuses().size());
    }
}
