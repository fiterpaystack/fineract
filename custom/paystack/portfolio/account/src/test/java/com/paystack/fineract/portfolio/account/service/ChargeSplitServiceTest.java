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

import com.paystack.fineract.portfolio.account.data.ChargeSplitData;
import com.paystack.fineract.portfolio.account.domain.ChargeSplit;
import com.paystack.fineract.portfolio.account.domain.ChargeSplitRepository;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChargeSplitServiceTest {

    @Mock
    private ChargeSplitRepository splitRepository;

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @Mock
    private FundRepository fundRepository;

    @Mock
    private GLAccountRepository glAccountRepository;

    @InjectMocks
    private ChargeSplitService splitService;

    private JsonCommand command;
    private Charge charge;
    private Fund fund;
    private GLAccount glAccount;
    private ChargeSplit split;

    @BeforeEach
    void setUp() {
        // Setup test data
        charge = mock(Charge.class);
        when(charge.getId()).thenReturn(1L);
        when(charge.getName()).thenReturn("Test Charge");

        fund = mock(Fund.class);
        when(fund.getId()).thenReturn(1L);
        when(fund.getName()).thenReturn("Test Fund");

        glAccount = mock(GLAccount.class);
        when(glAccount.getId()).thenReturn(1L);
        when(glAccount.getName()).thenReturn("Test GL Account");

        split = mock(ChargeSplit.class);
        when(split.getId()).thenReturn(1L);
        when(split.getCharge()).thenReturn(charge);
        when(split.getFund()).thenReturn(fund);
        when(split.getGlAccount()).thenReturn(glAccount);

        command = mock(JsonCommand.class);
        when(command.entityId()).thenReturn(1L);
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
        when(splitRepository.save(any(ChargeSplit.class))).thenReturn(split);

        // When
        CommandProcessingResult result = splitService.createSplit(command);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getResourceId());
        verify(splitRepository).save(any(ChargeSplit.class));
    }

    @Test
    void testCreateSplit_WhenSplitAlreadyExists_ShouldThrowException() {
        // Given
        when(chargeRepository.findOneWithNotFoundDetection(1L)).thenReturn(charge);
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(glAccountRepository.findById(1L)).thenReturn(Optional.of(glAccount));
        when(splitRepository.findByChargeIdAndFundId(1L, 1L)).thenReturn(Optional.of(split));

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> splitService.createSplit(command));
        verify(splitRepository, never()).save(any(ChargeSplit.class));
    }

    @Test
    void testGetSplitsByChargeId_ShouldReturnSplits() {
        // Given
        List<ChargeSplit> splits = Arrays.asList(split);
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(splits);

        // When
        List<ChargeSplitData> result = splitService.getSplitsByChargeId(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(splitRepository).findActiveSplitsByChargeId(1L);
    }

    @Test
    void testGetSplitsByFundId_ShouldReturnSplits() {
        // Given
        List<ChargeSplit> splits = Arrays.asList(split);
        when(splitRepository.findByFundIdAndActive(1L, true)).thenReturn(splits);

        // When
        List<ChargeSplitData> result = splitService.getSplitsByFundId(1L);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(splitRepository).findByFundIdAndActive(1L, true);
    }

    @Test
    void testSaveSplit_ShouldSaveAndReturnSplit() {
        // Given
        when(splitRepository.save(split)).thenReturn(split);

        // When
        ChargeSplit result = splitService.saveSplit(split);

        // Then
        assertNotNull(result);
        assertEquals(split, result);
        verify(splitRepository).save(split);
    }
}
