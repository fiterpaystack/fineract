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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencyStatus;
import com.paystack.fineract.portfolio.savings.domain.PeriodBoundaries;
import com.paystack.fineract.portfolio.savings.domain.SavingsProductWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import com.paystack.fineract.portfolio.savings.repository.SavingsProductWithdrawalFrequencySettingRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for WithdrawalFrequencyService Note: This test focuses on the core functionality and mocks cross-module
 * dependencies
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WithdrawalFrequencyServiceTest {

    @Mock
    private PaystackSavingsAccountTransactionRepository transactionRepository;

    @Mock
    private SavingsProductWithdrawalFrequencySettingRepository productSettingRepository;

    @Mock
    private PeriodCalculationService periodCalculationService;

    @InjectMocks
    private WithdrawalFrequencyService service;

    private SavingsAccount mockAccount;
    private SavingsProduct mockProduct;
    private LocalDate testDate;
    private PeriodBoundaries mockPeriodBoundaries;

    @BeforeEach
    void setUp() {
        mockProduct = mock(SavingsProduct.class);
        when(mockProduct.getId()).thenReturn(1L);

        mockAccount = mock(SavingsAccount.class);
        when(mockAccount.getId()).thenReturn(100L);
        when(mockAccount.savingsProduct()).thenReturn(mockProduct);

        testDate = LocalDate.of(2024, 1, 15);

        // Mock period boundaries - only set up when needed
        mockPeriodBoundaries = mock(PeriodBoundaries.class);
        when(mockPeriodBoundaries.getStartDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(mockPeriodBoundaries.getEndDate()).thenReturn(LocalDate.of(2024, 1, 31));
    }

    @Test
    void testGetEffectiveSettings_NoSettings() {
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true))
            .thenReturn(Collections.emptyList());

        List<WithdrawalFrequencySettingData> settings = service.getEffectiveSettings(mockAccount);

        assertTrue(settings.isEmpty());
    }

    @Test
    void testGetEffectiveSettings_ProductSettingsOnly() {
        SavingsProductWithdrawalFrequencySetting productSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true)).thenReturn(Arrays.asList(productSetting));

        List<WithdrawalFrequencySettingData> settings = service.getEffectiveSettings(mockAccount);

        assertEquals(1, settings.size());
        assertEquals(5, settings.get(0).getMaxWithdrawals());
        assertEquals(TimePeriod.MONTHLY, settings.get(0).getTimePeriod());
    }

    @Test
    void testIsWithdrawalAllowed_NoSettings() {
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true))
            .thenReturn(Collections.emptyList());

        boolean allowed = service.isWithdrawalAllowed(mockAccount, testDate);

        assertTrue(allowed);
    }

    @Test
    void testIsWithdrawalAllowed_WithinLimits() {
        SavingsProductWithdrawalFrequencySetting productSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true)).thenReturn(Arrays.asList(productSetting));
        when(periodCalculationService.calculatePeriod(any(LocalDate.class), any(TimePeriod.class))).thenReturn(mockPeriodBoundaries);
        when(transactionRepository.countTransactionsForPeriod(anyLong(), any(), any(), anyBoolean(), anyList())).thenReturn(3L); // Within
                                                                                                                                 // limit
                                                                                                                                 // of
                                                                                                                                 // 5

        boolean allowed = service.isWithdrawalAllowed(mockAccount, testDate);

        assertTrue(allowed);
    }

    @Test
    void testIsWithdrawalAllowed_ExceedsLimit() {
        SavingsProductWithdrawalFrequencySetting productSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true)).thenReturn(Arrays.asList(productSetting));
        when(periodCalculationService.calculatePeriod(any(LocalDate.class), any(TimePeriod.class))).thenReturn(mockPeriodBoundaries);
        when(transactionRepository.countTransactionsForPeriod(anyLong(), any(), any(), anyBoolean(), anyList())).thenReturn(5L); // At
                                                                                                                                 // limit
                                                                                                                                 // of
                                                                                                                                 // 5

        boolean allowed = service.isWithdrawalAllowed(mockAccount, testDate);

        assertFalse(allowed);
    }

    @Test
    void testGetWithdrawalStatus_NoSettings() {
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true))
            .thenReturn(Collections.emptyList());

        WithdrawalFrequencyStatus status = service.getWithdrawalStatus(mockAccount, testDate);

        assertTrue(status.isWithdrawalAllowed());
        assertTrue(status.getPeriodStatuses().isEmpty());
    }

    @Test
    void testGetWithdrawalStatus_WithSettings() {
        SavingsProductWithdrawalFrequencySetting productSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndIsActive(1L, true)).thenReturn(Arrays.asList(productSetting));
        when(periodCalculationService.calculatePeriod(any(LocalDate.class), any(TimePeriod.class))).thenReturn(mockPeriodBoundaries);
        when(transactionRepository.countTransactionsForPeriod(anyLong(), any(), any(), anyBoolean(), anyList())).thenReturn(3L);

        WithdrawalFrequencyStatus status = service.getWithdrawalStatus(mockAccount, testDate);

        assertTrue(status.isWithdrawalAllowed());
        assertEquals(1, status.getPeriodStatuses().size());

        WithdrawalFrequencyStatus.PeriodStatus periodStatus = status.getPeriodStatuses().get(0);
        assertEquals(TimePeriod.MONTHLY, periodStatus.getTimePeriod());
        assertEquals(5, periodStatus.getMaxWithdrawals());
        assertEquals(3, periodStatus.getCurrentCount());
        assertEquals(2, periodStatus.getRemaining());
        assertTrue(periodStatus.isAllowed());
    }

    @Test
    void testCreateProductSettings_NewSettings() {
        List<WithdrawalFrequencySettingData> settingsData = Arrays.asList(new WithdrawalFrequencySettingData(5, TimePeriod.MONTHLY, true),
                new WithdrawalFrequencySettingData(2, TimePeriod.WEEKLY, true));

        // Mock existing settings (empty for new product)
        when(productSettingRepository.findBySavingsProductId(1L)).thenReturn(Collections.emptyList());

        service.createProductSettings(1L, settingsData);

        // Should create 2 new settings
        verify(productSettingRepository, times(2)).save(any(SavingsProductWithdrawalFrequencySetting.class));
    }

    @Test
    void testCreateProductSettings_UpdateExisting() {
        List<WithdrawalFrequencySettingData> settingsData = Arrays.asList(new WithdrawalFrequencySettingData(3, TimePeriod.MONTHLY, true),
                new WithdrawalFrequencySettingData(1, TimePeriod.DAILY, true));

        // Mock existing settings
        SavingsProductWithdrawalFrequencySetting existingMonthly = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        SavingsProductWithdrawalFrequencySetting existingWeekly = createProductSetting(1L, 2, TimePeriod.WEEKLY);
        when(productSettingRepository.findBySavingsProductId(1L)).thenReturn(Arrays.asList(existingMonthly, existingWeekly));

        service.createProductSettings(1L, settingsData);

        // Should update existing MONTHLY setting
        assertEquals(3, existingMonthly.getMaxWithdrawals());
        assertTrue(existingMonthly.isActive());

        // Should create new DAILY setting
        // Should deactivate existing WEEKLY setting
        assertFalse(existingWeekly.isActive());

        verify(productSettingRepository, times(3)).save(any(SavingsProductWithdrawalFrequencySetting.class));
    }

    @Test
    void testCreateProductSettings_EmptyList() {
        // Mock existing settings
        SavingsProductWithdrawalFrequencySetting existingMonthly = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        SavingsProductWithdrawalFrequencySetting existingWeekly = createProductSetting(1L, 2, TimePeriod.WEEKLY);
        when(productSettingRepository.findBySavingsProductId(1L)).thenReturn(Arrays.asList(existingMonthly, existingWeekly));

        service.createProductSettings(1L, Collections.emptyList());

        // Should deactivate all existing settings
        assertFalse(existingMonthly.isActive());
        assertFalse(existingWeekly.isActive());

        verify(productSettingRepository, times(2)).save(any(SavingsProductWithdrawalFrequencySetting.class));
    }

    @Test
    void testUpdateProductSetting_ExistingSetting() {
        SavingsProductWithdrawalFrequencySetting existingSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndTimePeriod(1L, TimePeriod.MONTHLY)).thenReturn(Optional.of(existingSetting));

        WithdrawalFrequencySettingData newData = new WithdrawalFrequencySettingData(3, TimePeriod.MONTHLY, true);
        service.updateProductSetting(1L, TimePeriod.MONTHLY, newData);

        // Should update existing setting
        assertEquals(3, existingSetting.getMaxWithdrawals());
        assertTrue(existingSetting.isActive());
        verify(productSettingRepository).save(existingSetting);
    }

    @Test
    void testUpdateProductSetting_NewSetting() {
        when(productSettingRepository.findBySavingsProductIdAndTimePeriod(1L, TimePeriod.MONTHLY))
            .thenReturn(Optional.empty());

        WithdrawalFrequencySettingData newData = new WithdrawalFrequencySettingData(3, TimePeriod.MONTHLY, true);
        service.updateProductSetting(1L, TimePeriod.MONTHLY, newData);

        // Should create new setting
        verify(productSettingRepository).save(any(SavingsProductWithdrawalFrequencySetting.class));
    }

    @Test
    void testUpdateProductSetting_DeactivateExisting() {
        SavingsProductWithdrawalFrequencySetting existingSetting = createProductSetting(1L, 5, TimePeriod.MONTHLY);
        when(productSettingRepository.findBySavingsProductIdAndTimePeriod(1L, TimePeriod.MONTHLY)).thenReturn(Optional.of(existingSetting));

        service.updateProductSetting(1L, TimePeriod.MONTHLY, null);

        // Should deactivate existing setting
        verify(productSettingRepository).deactivateByProductIdAndTimePeriod(1L, TimePeriod.MONTHLY);
    }

    private SavingsProductWithdrawalFrequencySetting createProductSetting(Long productId, Integer maxWithdrawals, TimePeriod timePeriod) {
        SavingsProductWithdrawalFrequencySetting setting = new SavingsProductWithdrawalFrequencySetting();
        setting.setSavingsProductId(productId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(true);
        return setting;
    }
}
