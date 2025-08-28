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

import com.paystack.fineract.portfolio.account.domain.ChargeStakeholderSplit;
import com.paystack.fineract.portfolio.account.domain.ChargeStakeholderSplitRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAudit;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAuditRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetail;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetailRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.client.domain.ClientChargePaidBy;
import org.apache.fineract.portfolio.client.domain.ClientTransaction;
import org.apache.fineract.portfolio.fund.domain.Fund;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeeSplitServiceTest {

    @Mock
    private ChargeStakeholderSplitRepository splitRepository;

    @Mock
    private FeeSplitAuditRepository auditRepository;

    @Mock
    private FeeSplitDetailRepository detailRepository;

    @Mock
    private AccountingProcessorHelper accountingHelper;

    @InjectMocks
    private FeeSplitService feeSplitService;

    private Charge charge;
    private ClientTransaction clientTransaction;
    private List<ChargeStakeholderSplit> splits;
    private BigDecimal totalFeeAmount;

    @BeforeEach
    void setUp() {
        // Setup test data
        charge = mock(Charge.class);
        when(charge.getId()).thenReturn(1L);
        when(charge.isEnableFeeSplit()).thenReturn(true);

        clientTransaction = mock(ClientTransaction.class);
        when(clientTransaction.getId()).thenReturn(1L);

        totalFeeAmount = new BigDecimal("100.00");

        // Create mock splits
        ChargeStakeholderSplit split1 = mock(ChargeStakeholderSplit.class);
        when(split1.isPercentageSplit()).thenReturn(true);
        when(split1.getSplitValue()).thenReturn(new BigDecimal("50.00"));
        when(split1.calculateSplitAmount(totalFeeAmount)).thenReturn(new BigDecimal("50.00"));

        ChargeStakeholderSplit split2 = mock(ChargeStakeholderSplit.class);
        when(split2.isPercentageSplit()).thenReturn(true);
        when(split2.getSplitValue()).thenReturn(new BigDecimal("30.00"));
        when(split2.calculateSplitAmount(totalFeeAmount)).thenReturn(new BigDecimal("30.00"));

        splits = Arrays.asList(split1, split2);
    }

    @Test
    void testProcessFeeSplit_WhenFeeSplitEnabled_ShouldProcessSplits() {
        // Given
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(splits);
        when(auditRepository.save(any(FeeSplitAudit.class))).thenReturn(mock(FeeSplitAudit.class));
        when(accountingHelper.persistJournalEntry(any(JournalEntry.class))).thenReturn(mock(JournalEntry.class));

        // When
        feeSplitService.processFeeSplit(clientTransaction, totalFeeAmount);

        // Then
        verify(splitRepository).findActiveSplitsByChargeId(1L);
        verify(auditRepository).save(any(FeeSplitAudit.class));
        verify(accountingHelper, times(2)).persistJournalEntry(any(JournalEntry.class));
    }

    @Test
    void testProcessFeeSplit_WhenFeeSplitDisabled_ShouldNotProcess() {
        // Given
        when(charge.isEnableFeeSplit()).thenReturn(false);

        // When
        feeSplitService.processFeeSplit(clientTransaction, totalFeeAmount);

        // Then
        verify(splitRepository, never()).findActiveSplitsByChargeId(anyLong());
        verify(auditRepository, never()).save(any(FeeSplitAudit.class));
    }

    @Test
    void testProcessFeeSplit_WhenNoSplitsConfigured_ShouldNotProcess() {
        // Given
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(Collections.emptyList());

        // When
        feeSplitService.processFeeSplit(clientTransaction, totalFeeAmount);

        // Then
        verify(splitRepository).findActiveSplitsByChargeId(1L);
        verify(auditRepository, never()).save(any(FeeSplitAudit.class));
    }

    @Test
    void testProcessFeeSplit_WhenTotalPercentageExceeds100_ShouldThrowException() {
        // Given
        ChargeStakeholderSplit invalidSplit = mock(ChargeStakeholderSplit.class);
        when(invalidSplit.isPercentageSplit()).thenReturn(true);
        when(invalidSplit.getSplitValue()).thenReturn(new BigDecimal("150.00"));
        
        List<ChargeStakeholderSplit> invalidSplits = Arrays.asList(invalidSplit);
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(invalidSplits);

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            feeSplitService.processFeeSplit(clientTransaction, totalFeeAmount);
        });
    }

    @Test
    void testProcessFeeSplit_WhenTotalFlatAmountExceedsFee_ShouldThrowException() {
        // Given
        ChargeStakeholderSplit invalidSplit = mock(ChargeStakeholderSplit.class);
        when(invalidSplit.isFlatAmountSplit()).thenReturn(true);
        when(invalidSplit.getSplitValue()).thenReturn(new BigDecimal("150.00"));
        
        List<ChargeStakeholderSplit> invalidSplits = Arrays.asList(invalidSplit);
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(invalidSplits);

        // When & Then
        assertThrows(PlatformApiDataValidationException.class, () -> {
            feeSplitService.processFeeSplit(clientTransaction, totalFeeAmount);
        });
    }

    @Test
    void testProcessFeeSplitForSavings_WhenFeeSplitEnabled_ShouldProcessSplits() {
        // Given
        SavingsAccountTransaction savingsTransaction = mock(SavingsAccountTransaction.class);
        when(savingsTransaction.getId()).thenReturn(1L);
        
        when(splitRepository.findActiveSplitsByChargeId(1L)).thenReturn(splits);
        when(auditRepository.save(any(FeeSplitAudit.class))).thenReturn(mock(FeeSplitAudit.class));
        when(accountingHelper.persistJournalEntry(any(JournalEntry.class))).thenReturn(mock(JournalEntry.class));

        // When
        feeSplitService.processFeeSplitForSavings(savingsTransaction, totalFeeAmount);

        // Then
        verify(splitRepository).findActiveSplitsByChargeId(1L);
        verify(auditRepository).save(any(FeeSplitAudit.class));
        verify(accountingHelper, times(2)).persistJournalEntry(any(JournalEntry.class));
    }
}
