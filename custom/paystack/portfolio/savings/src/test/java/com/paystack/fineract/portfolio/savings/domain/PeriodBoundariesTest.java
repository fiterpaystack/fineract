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

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PeriodBoundaries value object
 */
class PeriodBoundariesTest {

    private final LocalDate startDate = LocalDate.of(2024, 1, 1);
    private final LocalDate endDate = LocalDate.of(2024, 1, 31);
    private final LocalDate midDate = LocalDate.of(2024, 1, 15);

    @Test
    void testConstructor_ValidDates() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        assertEquals(startDate, period.getStartDate());
        assertEquals(endDate, period.getEndDate());
    }

    @Test
    void testConstructor_SameStartAndEndDate() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, startDate);
        assertEquals(startDate, period.getStartDate());
        assertEquals(startDate, period.getEndDate());
    }

    @Test
    void testConstructor_NullStartDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PeriodBoundaries(null, endDate));
        assertEquals("Start date and end date cannot be null", exception.getMessage());
    }

    @Test
    void testConstructor_NullEndDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PeriodBoundaries(startDate, null));
        assertEquals("Start date and end date cannot be null", exception.getMessage());
    }

    @Test
    void testConstructor_StartDateAfterEndDate() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> new PeriodBoundaries(endDate, startDate));
        assertEquals("Start date cannot be after end date", exception.getMessage());
    }

    @Test
    void testContains_DateWithinPeriod() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        
        assertTrue(period.contains(startDate));
        assertTrue(period.contains(endDate));
        assertTrue(period.contains(midDate));
    }

    @Test
    void testContains_DateBeforePeriod() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        LocalDate beforeStart = startDate.minusDays(1);
        
        assertFalse(period.contains(beforeStart));
    }

    @Test
    void testContains_DateAfterPeriod() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        LocalDate afterEnd = endDate.plusDays(1);
        
        assertFalse(period.contains(afterEnd));
    }

    @Test
    void testContains_NullDate() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        assertFalse(period.contains(null));
    }

    @Test
    void testGetDaysInPeriod() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        // January 2024 has 31 days
        assertEquals(31, period.getDaysInPeriod());
    }

    @Test
    void testGetDaysInPeriod_SingleDay() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, startDate);
        assertEquals(1, period.getDaysInPeriod());
    }

    @Test
    void testOverlaps_OverlappingPeriods() {
        PeriodBoundaries period1 = new PeriodBoundaries(startDate, endDate);
        PeriodBoundaries period2 = new PeriodBoundaries(midDate, endDate.plusDays(10));
        
        assertTrue(period1.overlaps(period2));
        assertTrue(period2.overlaps(period1));
    }

    @Test
    void testOverlaps_AdjacentPeriods() {
        PeriodBoundaries period1 = new PeriodBoundaries(startDate, midDate);
        PeriodBoundaries period2 = new PeriodBoundaries(midDate.plusDays(1), endDate);
        
        assertFalse(period1.overlaps(period2));
        assertFalse(period2.overlaps(period1));
    }

    @Test
    void testOverlaps_NonOverlappingPeriods() {
        PeriodBoundaries period1 = new PeriodBoundaries(startDate, midDate);
        PeriodBoundaries period2 = new PeriodBoundaries(endDate.plusDays(1), endDate.plusDays(10));
        
        assertFalse(period1.overlaps(period2));
        assertFalse(period2.overlaps(period1));
    }

    @Test
    void testOverlaps_NullPeriod() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        assertFalse(period.overlaps(null));
    }

    @Test
    void testEquals_SamePeriod() {
        PeriodBoundaries period1 = new PeriodBoundaries(startDate, endDate);
        PeriodBoundaries period2 = new PeriodBoundaries(startDate, endDate);
        
        assertEquals(period1, period2);
        assertEquals(period1.hashCode(), period2.hashCode());
    }

    @Test
    void testEquals_DifferentPeriods() {
        PeriodBoundaries period1 = new PeriodBoundaries(startDate, endDate);
        PeriodBoundaries period2 = new PeriodBoundaries(startDate, midDate);
        
        assertNotEquals(period1, period2);
    }

    @Test
    void testEquals_Null() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        assertNotEquals(period, null);
    }

    @Test
    void testEquals_DifferentType() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        assertNotEquals(period, "not a period");
    }

    @Test
    void testToString() {
        PeriodBoundaries period = new PeriodBoundaries(startDate, endDate);
        String toString = period.toString();
        
        assertTrue(toString.contains("PeriodBoundaries"));
        assertTrue(toString.contains(startDate.toString()));
        assertTrue(toString.contains(endDate.toString()));
    }
}
