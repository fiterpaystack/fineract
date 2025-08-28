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

import java.math.BigDecimal;
import java.util.Map;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChargeSplitTest {

    @Mock
    private Charge charge;

    @Mock
    private Fund fund;

    @Mock
    private GLAccount glAccount;

    @Mock
    private JsonCommand command;

    private ChargeSplit split;

    @BeforeEach
    void setUp() {
        when(charge.getId()).thenReturn(1L);
        when(fund.getId()).thenReturn(1L);
        when(fund.getName()).thenReturn("Test Fund");
        when(glAccount.getId()).thenReturn(1L);
    }

    @Test
    void testCreateNew_WithValidData_ShouldCreateSplit() {
        // When
        split = ChargeSplit.createNew(charge, fund, "PERCENTAGE", 
                new BigDecimal("50.00"), glAccount);

        // Then
        assertNotNull(split);
        assertEquals(charge, split.getCharge());
        assertEquals(fund, split.getFund());
        assertEquals("PERCENTAGE", split.getSplitType());
        assertEquals(new BigDecimal("50.00"), split.getSplitValue());
        assertEquals(glAccount, split.getGlAccount());
        assertTrue(split.isActive());
    }

    @Test
    void testFromJson_WithValidCommand_ShouldCreateSplit() {
        // Given
        when(command.stringValueOfParameterNamed("splitType")).thenReturn("FLAT_AMOUNT");
        when(command.bigDecimalValueOfParameterNamed("splitValue")).thenReturn(new BigDecimal("25.00"));

        // When
        split = ChargeSplit.fromJson(command, charge, fund, glAccount);

        // Then
        assertNotNull(split);
        assertEquals(charge, split.getCharge());
        assertEquals(fund, split.getFund());
        assertEquals("FLAT_AMOUNT", split.getSplitType());
        assertEquals(new BigDecimal("25.00"), split.getSplitValue());
        assertEquals(glAccount, split.getGlAccount());
    }

    @Test
    void testIsPercentageSplit_WhenPercentageType_ShouldReturnTrue() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "PERCENTAGE", 
                new BigDecimal("50.00"), glAccount);

        // When & Then
        assertTrue(split.isPercentageSplit());
        assertFalse(split.isFlatAmountSplit());
    }

    @Test
    void testIsFlatAmountSplit_WhenFlatAmountType_ShouldReturnTrue() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "FLAT_AMOUNT", 
                new BigDecimal("25.00"), glAccount);

        // When & Then
        assertTrue(split.isFlatAmountSplit());
        assertFalse(split.isPercentageSplit());
    }

    @Test
    void testCalculateSplitAmount_WhenPercentage_ShouldCalculateCorrectly() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "PERCENTAGE", 
                new BigDecimal("50.00"), glAccount);
        BigDecimal totalFeeAmount = new BigDecimal("100.00");

        // When
        BigDecimal result = split.calculateSplitAmount(totalFeeAmount);

        // Then
        assertEquals(new BigDecimal("50.00"), result);
    }

    @Test
    void testCalculateSplitAmount_WhenFlatAmount_ShouldReturnSplitValue() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "FLAT_AMOUNT", 
                new BigDecimal("25.00"), glAccount);
        BigDecimal totalFeeAmount = new BigDecimal("100.00");

        // When
        BigDecimal result = split.calculateSplitAmount(totalFeeAmount);

        // Then
        assertEquals(new BigDecimal("25.00"), result);
    }

    @Test
    void testUpdate_WithValidChanges_ShouldUpdateFields() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "PERCENTAGE", 
                new BigDecimal("50.00"), glAccount);
        
        when(command.isChangeInStringParameterNamed("splitType", "PERCENTAGE")).thenReturn(true);
        when(command.stringValueOfParameterNamed("splitType")).thenReturn("FLAT_AMOUNT");
        when(command.isChangeInBigDecimalParameterNamed("splitValue", new BigDecimal("50.00"))).thenReturn(true);
        when(command.bigDecimalValueOfParameterNamed("splitValue")).thenReturn(new BigDecimal("30.00"));
        when(command.isChangeInBooleanParameterNamed("active", true)).thenReturn(true);
        when(command.booleanPrimitiveValueOfParameterNamed("active")).thenReturn(false);

        // When
        Map<String, Object> changes = split.update(command);

        // Then
        assertNotNull(changes);
        assertTrue(changes.containsKey("splitType"));
        assertTrue(changes.containsKey("splitValue"));
        assertTrue(changes.containsKey("active"));
        assertEquals("FLAT_AMOUNT", changes.get("splitType"));
        assertEquals(new BigDecimal("30.00"), changes.get("splitValue"));
        assertEquals(false, changes.get("active"));
    }

    @Test
    void testUpdate_WithNoChanges_ShouldReturnEmptyMap() {
        // Given
        split = ChargeStakeholderSplit.createNew(charge, fund, "PERCENTAGE", 
                new BigDecimal("50.00"), glAccount);
        
        when(command.isChangeInStringParameterNamed("splitType", "PERCENTAGE")).thenReturn(false);
        when(command.isChangeInBigDecimalParameterNamed("splitValue", new BigDecimal("50.00"))).thenReturn(false);
        when(command.isChangeInBooleanParameterNamed("active", true)).thenReturn(false);

        // When
        Map<String, Object> changes = split.update(command);

        // Then
        assertNotNull(changes);
        assertTrue(changes.isEmpty());
    }
}
