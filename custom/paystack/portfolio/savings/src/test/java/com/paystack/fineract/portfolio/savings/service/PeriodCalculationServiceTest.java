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

import static org.junit.jupiter.api.Assertions.*;

import com.paystack.fineract.portfolio.savings.domain.PeriodBoundaries;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PeriodCalculationService
 */
class PeriodCalculationServiceTest {

    private PeriodCalculationService service;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        service = new PeriodCalculationService();
        // Use a specific date for consistent testing (Wednesday, January 15, 2024)
        testDate = LocalDate.of(2024, 1, 15);
    }

    @Test
    void testCalculatePeriod_Daily() {
        PeriodBoundaries period = service.calculatePeriod(testDate, TimePeriod.DAILY);
        
        assertEquals(testDate, period.getStartDate());
        assertEquals(testDate, period.getEndDate());
        assertEquals(1, period.getDaysInPeriod());
    }

    @Test
    void testCalculatePeriod_Weekly() {
        PeriodBoundaries period = service.calculatePeriod(testDate, TimePeriod.WEEKLY);
        
        // January 15, 2024 is a Monday, so week should be Monday to Sunday
        LocalDate expectedStart = LocalDate.of(2024, 1, 15); // Monday
        LocalDate expectedEnd = LocalDate.of(2024, 1, 21);   // Sunday
        
        assertEquals(expectedStart, period.getStartDate());
        assertEquals(expectedEnd, period.getEndDate());
        assertEquals(7, period.getDaysInPeriod());
    }

    @Test
    void testCalculatePeriod_Weekly_NonMonday() {
        // Test with a Wednesday
        LocalDate wednesday = LocalDate.of(2024, 1, 17);
        PeriodBoundaries period = service.calculatePeriod(wednesday, TimePeriod.WEEKLY);
        
        // Should start from Monday of that week
        LocalDate expectedStart = LocalDate.of(2024, 1, 15); // Monday
        LocalDate expectedEnd = LocalDate.of(2024, 1, 21);   // Sunday
        
        assertEquals(expectedStart, period.getStartDate());
        assertEquals(expectedEnd, period.getEndDate());
    }

    @Test
    void testCalculatePeriod_Monthly() {
        PeriodBoundaries period = service.calculatePeriod(testDate, TimePeriod.MONTHLY);
        
        LocalDate expectedStart = LocalDate.of(2024, 1, 1);  // First day of month
        LocalDate expectedEnd = LocalDate.of(2024, 1, 31);   // Last day of month
        
        assertEquals(expectedStart, period.getStartDate());
        assertEquals(expectedEnd, period.getEndDate());
        assertEquals(31, period.getDaysInPeriod());
    }

    @Test
    void testCalculatePeriod_Yearly() {
        PeriodBoundaries period = service.calculatePeriod(testDate, TimePeriod.YEARLY);
        
        LocalDate expectedStart = LocalDate.of(2024, 1, 1);  // January 1st
        LocalDate expectedEnd = LocalDate.of(2024, 12, 31);  // December 31st
        
        assertEquals(expectedStart, period.getStartDate());
        assertEquals(expectedEnd, period.getEndDate());
        assertEquals(366, period.getDaysInPeriod()); // 2024 is a leap year
    }

    @Test
    void testCalculatePeriod_NullDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.calculatePeriod(null, TimePeriod.DAILY));
        assertEquals("Date cannot be null", exception.getMessage());
    }

    @Test
    void testCalculatePeriod_NullTimePeriod() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> service.calculatePeriod(testDate, null));
        assertEquals("Time period cannot be null", exception.getMessage());
    }

    @Test
    void testAreInSamePeriod_SameDate() {
        assertTrue(service.areInSamePeriod(testDate, testDate, TimePeriod.DAILY));
        assertTrue(service.areInSamePeriod(testDate, testDate, TimePeriod.WEEKLY));
        assertTrue(service.areInSamePeriod(testDate, testDate, TimePeriod.MONTHLY));
        assertTrue(service.areInSamePeriod(testDate, testDate, TimePeriod.YEARLY));
    }

    @Test
    void testAreInSamePeriod_DifferentDatesSamePeriod() {
        LocalDate sameDay = testDate;
        LocalDate sameWeek = testDate.plusDays(2);
        LocalDate sameMonth = testDate.plusDays(10);
        LocalDate sameYear = testDate.plusMonths(6);
        
        assertTrue(service.areInSamePeriod(testDate, sameDay, TimePeriod.DAILY));
        assertTrue(service.areInSamePeriod(testDate, sameWeek, TimePeriod.WEEKLY));
        assertTrue(service.areInSamePeriod(testDate, sameMonth, TimePeriod.MONTHLY));
        assertTrue(service.areInSamePeriod(testDate, sameYear, TimePeriod.YEARLY));
    }

    @Test
    void testAreInSamePeriod_DifferentPeriods() {
        LocalDate differentDay = testDate.plusDays(1);
        LocalDate differentWeek = testDate.plusDays(8);
        LocalDate differentMonth = testDate.plusMonths(1);
        LocalDate differentYear = testDate.plusYears(1);
        
        assertFalse(service.areInSamePeriod(testDate, differentDay, TimePeriod.DAILY));
        assertFalse(service.areInSamePeriod(testDate, differentWeek, TimePeriod.WEEKLY));
        assertFalse(service.areInSamePeriod(testDate, differentMonth, TimePeriod.MONTHLY));
        assertFalse(service.areInSamePeriod(testDate, differentYear, TimePeriod.YEARLY));
    }

    @Test
    void testAreInSamePeriod_NullValues() {
        assertFalse(service.areInSamePeriod(null, testDate, TimePeriod.DAILY));
        assertFalse(service.areInSamePeriod(testDate, null, TimePeriod.DAILY));
        assertFalse(service.areInSamePeriod(testDate, testDate, null));
    }

    @Test
    void testGetDaysInPeriod() {
        assertEquals(1, service.getDaysInPeriod(testDate, TimePeriod.DAILY));
        assertEquals(7, service.getDaysInPeriod(testDate, TimePeriod.WEEKLY));
        assertEquals(31, service.getDaysInPeriod(testDate, TimePeriod.MONTHLY));
        assertEquals(366, service.getDaysInPeriod(testDate, TimePeriod.YEARLY));
    }

    @Test
    void testGetPeriodStart() {
        assertEquals(testDate, service.getPeriodStart(testDate, TimePeriod.DAILY));
        
        // For weekly, should return Monday of the week
        LocalDate expectedMonday = LocalDate.of(2024, 1, 15);
        assertEquals(expectedMonday, service.getPeriodStart(testDate, TimePeriod.WEEKLY));
        
        // For monthly, should return first day of month
        LocalDate expectedFirstDay = LocalDate.of(2024, 1, 1);
        assertEquals(expectedFirstDay, service.getPeriodStart(testDate, TimePeriod.MONTHLY));
        
        // For yearly, should return January 1st
        LocalDate expectedJan1 = LocalDate.of(2024, 1, 1);
        assertEquals(expectedJan1, service.getPeriodStart(testDate, TimePeriod.YEARLY));
    }

    @Test
    void testGetPeriodEnd() {
        assertEquals(testDate, service.getPeriodEnd(testDate, TimePeriod.DAILY));
        
        // For weekly, should return Sunday of the week
        LocalDate expectedSunday = LocalDate.of(2024, 1, 21);
        assertEquals(expectedSunday, service.getPeriodEnd(testDate, TimePeriod.WEEKLY));
        
        // For monthly, should return last day of month
        LocalDate expectedLastDay = LocalDate.of(2024, 1, 31);
        assertEquals(expectedLastDay, service.getPeriodEnd(testDate, TimePeriod.MONTHLY));
        
        // For yearly, should return December 31st
        LocalDate expectedDec31 = LocalDate.of(2024, 12, 31);
        assertEquals(expectedDec31, service.getPeriodEnd(testDate, TimePeriod.YEARLY));
    }

    @Test
    void testEdgeCases_LeapYear() {
        LocalDate leapYearDate = LocalDate.of(2024, 2, 15);
        PeriodBoundaries period = service.calculatePeriod(leapYearDate, TimePeriod.MONTHLY);
        
        // February 2024 has 29 days (leap year)
        assertEquals(29, period.getDaysInPeriod());
    }

    @Test
    void testEdgeCases_NonLeapYear() {
        LocalDate nonLeapYearDate = LocalDate.of(2023, 2, 15);
        PeriodBoundaries period = service.calculatePeriod(nonLeapYearDate, TimePeriod.MONTHLY);
        
        // February 2023 has 28 days (non-leap year)
        assertEquals(28, period.getDaysInPeriod());
    }
}
