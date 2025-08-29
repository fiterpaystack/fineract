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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.paystack.fineract.portfolio.account.domain.ChargeStakeholderSplit;
import com.paystack.fineract.portfolio.account.domain.ChargeStakeholderSplitRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.apache.fineract.portfolio.fund.domain.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChargeStakeholderSplitServiceTest {

    @Mock
    private ChargeStakeholderSplitRepository splitRepository;

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @Mock
    private FundRepository fundRepository;

    @Mock
    private GLAccountRepository glAccountRepository;

    @InjectMocks
    private ChargeStakeholderSplitService splitService;

    private JsonCommand command;
    private Charge charge;
    private Fund fund;
    private GLAccount glAccount;
    private ChargeStakeholderSplit split;

    @BeforeEach
    void setUp() {
        // Setup test data
        charge = mock(Charge.class);
        when(charge.getId()).thenReturn(1L);

        fund = mock(Fund.class);
        when(fund.getId()).thenReturn(1L);
        when(fund.getName()).thenReturn("Test Fund");

        glAccount = mock(GLAccount.class);
        when(glAccount.getId()).thenReturn(1L);

        split = mock(ChargeStakeholderSplit.class);
        when(split.getId()).thenReturn(1L);
        when(split.getCharge()).thenReturn(charge);
        when(split.getFund()).thenReturn(fund);
        when(split.getGlAccount()).thenReturn(glAccount);

        command = mock(JsonCommand.class);
        when(command.longValueOfParameterNamed("chargeId")).thenReturn(1L);
        when(command.longValueOfParameterNamed("fundId")).thenReturn(1L);
        when(command.longValueOfParameterNamed("glAccountId")).thenReturn(1L);
        when(command.stringValueOfParameterNamed("splitType")).thenReturn("PERCENTAGE");
        when(command.bigDecimalValueOfParameterNamed("splitValue")).thenReturn(new BigDecimal("50.00"));
    }

    @Test
    void testCreateSplit_WhenValidData_ShouldCreateSplit() {
        // Given
        when(chargeRepository.findOneWithNotFoundDetection(1L)).thenReturn(charge);
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(glAccountRepository.findById(1L)).thenReturn(Optional.of(glAccount));
        when(splitRepository.findByChargeIdAndFundId(1L, 1L)).thenReturn(Optional.empty());
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(Collections.emptyList());
        when(splitRepository.save(any(ChargeStakeholderSplit.class))).thenReturn(split);

        // When
        CommandProcessingResult result = splitService.createSplit(command);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.entityId());
        assertEquals(1L, result.chargeId());
        verify(splitRepository).save(any(ChargeStakeholderSplit.class));
    }

    @Test
    void testCreateSplit_WhenFundNotFound_ShouldThrowException() {
        // Given
        when(chargeRepository.findOneWithNotFoundDetection(1L)).thenReturn(charge);
        when(fundRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            splitService.createSplit(command);
        });
    }

    @Test
    void testCreateSplit_WhenGLAccountNotFound_ShouldThrowException() {
        // Given
        when(chargeRepository.findOneWithNotFoundDetection(1L)).thenReturn(charge);
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(glAccountRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            splitService.createSplit(command);
        });
    }

    @Test
    void testCreateSplit_WhenSplitAlreadyExists_ShouldThrowException() {
        // Given
        when(chargeRepository.findOneWithNotFoundDetection(1L)).thenReturn(charge);
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(glAccountRepository.findById(1L)).thenReturn(Optional.of(glAccount));
        when(splitRepository.findByChargeIdAndFundId(1L, 1L)).thenReturn(Optional.of(split));

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            splitService.createSplit(command);
        });
    }

    @Test
    void testUpdateSplit_WhenValidData_ShouldUpdateSplit() {
        // Given
        Map<String, Object> changes = Map.of("splitValue", new BigDecimal("60.00"));
        when(splitRepository.findById(1L)).thenReturn(Optional.of(split));
        when(split.update(command)).thenReturn(changes);
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(Collections.emptyList());
        when(splitRepository.save(split)).thenReturn(split);

        // When
        CommandProcessingResult result = splitService.updateSplit(1L, command);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.entityId());
        assertEquals(1L, result.chargeId());
        assertEquals(changes, result.changes());
        verify(splitRepository).save(split);
    }

    @Test
    void testUpdateSplit_WhenSplitNotFound_ShouldThrowException() {
        // Given
        when(splitRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            splitService.updateSplit(1L, command);
        });
    }

    @Test
    void testDeleteSplit_WhenValidData_ShouldDeleteSplit() {
        // Given
        when(splitRepository.findById(1L)).thenReturn(Optional.of(split));

        // When
        CommandProcessingResult result = splitService.deleteSplit(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.entityId());
        assertEquals(1L, result.chargeId());
        verify(splitRepository).delete(split);
    }

    @Test
    void testDeleteSplit_WhenSplitNotFound_ShouldThrowException() {
        // Given
        when(splitRepository.findById(1L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            splitService.deleteSplit(1L);
        });
    }

    @Test
    void testGetSplitsByChargeId_ShouldReturnSplits() {
        // Given
        List<ChargeStakeholderSplit> expectedSplits = Arrays.asList(split);
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(expectedSplits);

        // When
        List<ChargeStakeholderSplit> result = splitService.getSplitsByChargeId(1L);

        // Then
        assertEquals(expectedSplits, result);
        verify(splitRepository).findActiveSplitsByChargeId(1L);
    }

    @Test
    void testGetSplitsByFundId_ShouldReturnSplits() {
        // Given
        List<ChargeStakeholderSplit> expectedSplits = Arrays.asList(split);
        when(splitRepository.findByFundIdAndActive(1L, true)).thenReturn(expectedSplits);

        // When
        List<ChargeStakeholderSplit> result = splitService.getSplitsByFundId(1L);

        // Then
        assertEquals(expectedSplits, result);
        verify(splitRepository).findByFundIdAndActive(1L, true);
    }
}
