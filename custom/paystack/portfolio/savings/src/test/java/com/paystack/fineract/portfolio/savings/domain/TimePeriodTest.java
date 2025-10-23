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
 * Unit tests for TimePeriod enum
 */
class TimePeriodTest {

    @Test
    void testFromString_ValidValues() {
        assertEquals(TimePeriod.DAILY, TimePeriod.fromString("DAILY"));
        assertEquals(TimePeriod.DAILY, TimePeriod.fromString("daily"));
        assertEquals(TimePeriod.DAILY, TimePeriod.fromString("Daily"));

        assertEquals(TimePeriod.WEEKLY, TimePeriod.fromString("WEEKLY"));
        assertEquals(TimePeriod.WEEKLY, TimePeriod.fromString("weekly"));
        assertEquals(TimePeriod.WEEKLY, TimePeriod.fromString("Weekly"));

        assertEquals(TimePeriod.MONTHLY, TimePeriod.fromString("MONTHLY"));
        assertEquals(TimePeriod.MONTHLY, TimePeriod.fromString("monthly"));
        assertEquals(TimePeriod.MONTHLY, TimePeriod.fromString("Monthly"));

        assertEquals(TimePeriod.YEARLY, TimePeriod.fromString("YEARLY"));
        assertEquals(TimePeriod.YEARLY, TimePeriod.fromString("yearly"));
        assertEquals(TimePeriod.YEARLY, TimePeriod.fromString("Yearly"));
    }

    @Test
    void testFromString_NullValue() {
        assertNull(TimePeriod.fromString(null));
    }

    @Test
    void testFromString_InvalidValue() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> TimePeriod.fromString("INVALID"));

        assertTrue(exception.getMessage().contains("Invalid time period: INVALID"));
        assertTrue(exception.getMessage().contains("DAILY, WEEKLY, MONTHLY, YEARLY"));
    }

    @Test
    void testGetDisplayName() {
        assertEquals("Daily", TimePeriod.DAILY.getDisplayName());
        assertEquals("Weekly", TimePeriod.WEEKLY.getDisplayName());
        assertEquals("Monthly", TimePeriod.MONTHLY.getDisplayName());
        assertEquals("Yearly", TimePeriod.YEARLY.getDisplayName());
    }

    @Test
    void testIsMoreRestrictiveThan() {
        // DAILY is more restrictive than all others
        assertTrue(TimePeriod.DAILY.isMoreRestrictiveThan(TimePeriod.WEEKLY));
        assertTrue(TimePeriod.DAILY.isMoreRestrictiveThan(TimePeriod.MONTHLY));
        assertTrue(TimePeriod.DAILY.isMoreRestrictiveThan(TimePeriod.YEARLY));
        assertFalse(TimePeriod.DAILY.isMoreRestrictiveThan(TimePeriod.DAILY));
        assertTrue(TimePeriod.DAILY.isMoreRestrictiveThan(null));

        // WEEKLY is more restrictive than MONTHLY and YEARLY
        assertFalse(TimePeriod.WEEKLY.isMoreRestrictiveThan(TimePeriod.DAILY));
        assertTrue(TimePeriod.WEEKLY.isMoreRestrictiveThan(TimePeriod.MONTHLY));
        assertTrue(TimePeriod.WEEKLY.isMoreRestrictiveThan(TimePeriod.YEARLY));
        assertFalse(TimePeriod.WEEKLY.isMoreRestrictiveThan(TimePeriod.WEEKLY));

        // MONTHLY is more restrictive than YEARLY only
        assertFalse(TimePeriod.MONTHLY.isMoreRestrictiveThan(TimePeriod.DAILY));
        assertFalse(TimePeriod.MONTHLY.isMoreRestrictiveThan(TimePeriod.WEEKLY));
        assertTrue(TimePeriod.MONTHLY.isMoreRestrictiveThan(TimePeriod.YEARLY));
        assertFalse(TimePeriod.MONTHLY.isMoreRestrictiveThan(TimePeriod.MONTHLY));

        // YEARLY is not more restrictive than any
        assertFalse(TimePeriod.YEARLY.isMoreRestrictiveThan(TimePeriod.DAILY));
        assertFalse(TimePeriod.YEARLY.isMoreRestrictiveThan(TimePeriod.WEEKLY));
        assertFalse(TimePeriod.YEARLY.isMoreRestrictiveThan(TimePeriod.MONTHLY));
        assertFalse(TimePeriod.YEARLY.isMoreRestrictiveThan(TimePeriod.YEARLY));
    }

    @Test
    void testGetOrder() {
        assertEquals(0, TimePeriod.DAILY.getOrder());
        assertEquals(1, TimePeriod.WEEKLY.getOrder());
        assertEquals(2, TimePeriod.MONTHLY.getOrder());
        assertEquals(3, TimePeriod.YEARLY.getOrder());
    }

    @Test
    void testValues() {
        TimePeriod[] values = TimePeriod.values();
        assertEquals(4, values.length);
        assertEquals(TimePeriod.DAILY, values[0]);
        assertEquals(TimePeriod.WEEKLY, values[1]);
        assertEquals(TimePeriod.MONTHLY, values[2]);
        assertEquals(TimePeriod.YEARLY, values[3]);
    }

    @Test
    void testValueOf() {
        assertEquals(TimePeriod.DAILY, TimePeriod.valueOf("DAILY"));
        assertEquals(TimePeriod.WEEKLY, TimePeriod.valueOf("WEEKLY"));
        assertEquals(TimePeriod.MONTHLY, TimePeriod.valueOf("MONTHLY"));
        assertEquals(TimePeriod.YEARLY, TimePeriod.valueOf("YEARLY"));
    }
}
