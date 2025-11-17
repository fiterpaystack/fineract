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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for WithdrawalFrequencySettingData
 */
class WithdrawalFrequencySettingDataTest {

    @Test
    void testConstructor_ValidParameters() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        assertEquals(5, data.getMaxWithdrawals());
        assertEquals(TimePeriod.MONTHLY, data.getTimePeriod());
        assertTrue(data.getIsActive());
    }

    @Test
    void testFromJson_ValidJson() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", 3);
        json.addProperty("timePeriod", "WEEKLY");
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNotNull(data);
        assertEquals(3, data.getMaxWithdrawals());
        assertEquals(TimePeriod.WEEKLY, data.getTimePeriod());
        assertTrue(data.getIsActive());
    }

    @Test
    void testFromJson_ValidJsonWithLowerCaseTimePeriod() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", 2);
        json.addProperty("timePeriod", "daily");
        json.addProperty("isActive", false);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNotNull(data);
        assertEquals(2, data.getMaxWithdrawals());
        assertEquals(TimePeriod.DAILY, data.getTimePeriod());
        assertFalse(data.getIsActive());
    }

    @Test
    void testFromJson_MissingMaxWithdrawals() {
        JsonObject json = new JsonObject();
        json.addProperty("timePeriod", "MONTHLY");
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNull(data);
    }

    @Test
    void testFromJson_MissingTimePeriod() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", 5);
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNull(data);
    }

    @Test
    void testFromJson_InvalidTimePeriod() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", 5);
        json.addProperty("timePeriod", "INVALID");
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNull(data);
    }

    @Test
    void testFromJson_ZeroMaxWithdrawals() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", 0);
        json.addProperty("timePeriod", "MONTHLY");
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNull(data);
    }

    @Test
    void testFromJson_NegativeMaxWithdrawals() {
        JsonObject json = new JsonObject();
        json.addProperty("maxWithdrawals", -1);
        json.addProperty("timePeriod", "MONTHLY");
        json.addProperty("isActive", true);

        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(json);

        assertNull(data);
    }

    @Test
    void testFromJson_NullJson() {
        WithdrawalFrequencySettingData data = WithdrawalFrequencySettingData.fromJson(null);

        assertNull(data);
    }

    @Test
    void testIsValid_ValidData() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        assertTrue(data.isValid());
    }

    @Test
    void testIsValid_NullMaxWithdrawals() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(null, TimePeriod.MONTHLY, true);

        assertFalse(data.isValid());
    }

    @Test
    void testIsValid_ZeroMaxWithdrawals() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(0, TimePeriod.MONTHLY, true);

        assertFalse(data.isValid());
    }

    @Test
    void testIsValid_NegativeMaxWithdrawals() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(-1, TimePeriod.MONTHLY, true);

        assertFalse(data.isValid());
    }

    @Test
    void testIsValid_NullTimePeriod() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, null, true);

        assertFalse(data.isValid());
    }

    @Test
    void testIsValid_NullActive() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, null);

        assertFalse(data.isValid());
    }

    @Test
    void testGettersAndSetters() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData();

        data.setMaxWithdrawals(10);
        data.setTimePeriod(TimePeriod.DAILY);
        data.setIsActive(false);

        assertEquals(10, data.getMaxWithdrawals());
        assertEquals(TimePeriod.DAILY, data.getTimePeriod());
        assertFalse(data.getIsActive());
    }

    @Test
    void testToString() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        String toString = data.toString();

        assertTrue(toString.contains("WithdrawalFrequencySettingData"));
        assertTrue(toString.contains("maxWithdrawals=5"));
        assertTrue(toString.contains("timePeriod=MONTHLY"));
        assertTrue(toString.contains("isActive=true"));
    }

    @Test
    void testEquals_SameData() {
        WithdrawalFrequencySettingData data1 = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);
        WithdrawalFrequencySettingData data2 = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        assertEquals(data1, data2);
        assertEquals(data1.hashCode(), data2.hashCode());
    }

    @Test
    void testEquals_DifferentData() {
        WithdrawalFrequencySettingData data1 = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);
        WithdrawalFrequencySettingData data2 = new WithdrawalFrequencySettingData(3, TimePeriod.WEEKLY, false);

        assertNotEquals(data1, data2);
    }

    @Test
    void testEquals_Null() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        assertNotEquals(data, null);
    }

    @Test
    void testEquals_DifferentType() {
        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true);

        assertNotEquals(data, "not a data object");
    }
}
