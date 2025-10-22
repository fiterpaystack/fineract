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

package com.paystack.fineract.portfolio.savings.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.paystack.fineract.portfolio.savings.domain.SavingsProductWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Unit tests for SavingsProductWithdrawalFrequencySettingRepository
 */
@ExtendWith(MockitoExtension.class)
class SavingsProductWithdrawalFrequencySettingRepositoryTest {

    @Mock
    private SavingsProductWithdrawalFrequencySettingRepository repository;

    private final Long productId = 1L;
    private final TimePeriod timePeriod = TimePeriod.MONTHLY;
    private final Boolean isActive = true;

    @BeforeEach
    void setUp() {
        // Verify that the repository extends JpaRepository
        assertTrue(repository instanceof JpaRepository);
    }

    @Test
    void testFindBySavingsProductIdAndIsActive() {
        List<SavingsProductWithdrawalFrequencySetting> expectedSettings = Arrays.asList(
            createSetting(1L, 5, TimePeriod.MONTHLY),
            createSetting(1L, 2, TimePeriod.WEEKLY)
        );

        when(repository.findBySavingsProductIdAndIsActive(productId, isActive))
            .thenReturn(expectedSettings);

        List<SavingsProductWithdrawalFrequencySetting> result = repository
            .findBySavingsProductIdAndIsActive(productId, isActive);

        assertEquals(2, result.size());
        verify(repository).findBySavingsProductIdAndIsActive(productId, isActive);
    }

    @Test
    void testFindBySavingsProductIdAndTimePeriodAndIsActive() {
        SavingsProductWithdrawalFrequencySetting expectedSetting = createSetting(productId, 5, timePeriod);

        when(repository.findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive))
            .thenReturn(Optional.of(expectedSetting));

        Optional<SavingsProductWithdrawalFrequencySetting> result = repository
            .findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);

        assertTrue(result.isPresent());
        assertEquals(expectedSetting, result.get());
        verify(repository).findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);
    }

    @Test
    void testFindBySavingsProductIdAndTimePeriod() {
        SavingsProductWithdrawalFrequencySetting expectedSetting = createSetting(productId, 5, timePeriod);

        when(repository.findBySavingsProductIdAndTimePeriod(productId, timePeriod))
            .thenReturn(Optional.of(expectedSetting));

        Optional<SavingsProductWithdrawalFrequencySetting> result = repository
            .findBySavingsProductIdAndTimePeriod(productId, timePeriod);

        assertTrue(result.isPresent());
        assertEquals(expectedSetting, result.get());
        verify(repository).findBySavingsProductIdAndTimePeriod(productId, timePeriod);
    }

    @Test
    void testFindBySavingsProductIdAndTimePeriodAndIsActive_NotFound() {
        when(repository.findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive))
            .thenReturn(Optional.empty());

        Optional<SavingsProductWithdrawalFrequencySetting> result = repository
            .findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);

        assertFalse(result.isPresent());
        verify(repository).findBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);
    }

    @Test
    void testFindBySavingsProductId() {
        List<SavingsProductWithdrawalFrequencySetting> expectedSettings = Arrays.asList(
            createSetting(productId, 5, TimePeriod.MONTHLY),
            createSetting(productId, 2, TimePeriod.WEEKLY)
        );

        when(repository.findBySavingsProductId(productId))
            .thenReturn(expectedSettings);

        List<SavingsProductWithdrawalFrequencySetting> result = repository.findBySavingsProductId(productId);

        assertEquals(2, result.size());
        verify(repository).findBySavingsProductId(productId);
    }

    @Test
    void testExistsBySavingsProductIdAndTimePeriodAndIsActive() {
        when(repository.existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive))
            .thenReturn(true);

        boolean exists = repository.existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);

        assertTrue(exists);
        verify(repository).existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);
    }

    @Test
    void testExistsBySavingsProductIdAndTimePeriodAndIsActive_NotExists() {
        when(repository.existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive))
            .thenReturn(false);

        boolean exists = repository.existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);

        assertFalse(exists);
        verify(repository).existsBySavingsProductIdAndTimePeriodAndIsActive(productId, timePeriod, isActive);
    }

    @Test
    void testDeactivateByProductId() {
        repository.deactivateByProductId(productId);

        verify(repository).deactivateByProductId(productId);
    }

    @Test
    void testDeactivateByProductIdAndTimePeriod() {
        repository.deactivateByProductIdAndTimePeriod(productId, timePeriod);

        verify(repository).deactivateByProductIdAndTimePeriod(productId, timePeriod);
    }

    @Test
    void testCountBySavingsProductIdAndIsActive() {
        when(repository.countBySavingsProductIdAndIsActive(productId, isActive))
            .thenReturn(3L);

        long count = repository.countBySavingsProductIdAndIsActive(productId, isActive);

        assertEquals(3L, count);
        verify(repository).countBySavingsProductIdAndIsActive(productId, isActive);
    }

    @Test
    void testFindByTimePeriodAndIsActive() {
        List<SavingsProductWithdrawalFrequencySetting> expectedSettings = Arrays.asList(
            createSetting(1L, 5, timePeriod),
            createSetting(2L, 3, timePeriod)
        );

        when(repository.findByTimePeriodAndIsActive(timePeriod, isActive))
            .thenReturn(expectedSettings);

        List<SavingsProductWithdrawalFrequencySetting> result = repository
            .findByTimePeriodAndIsActive(timePeriod, isActive);

        assertEquals(2, result.size());
        verify(repository).findByTimePeriodAndIsActive(timePeriod, isActive);
    }

    @Test
    void testFindBySavingsProductIdAndIsActive_EmptyResult() {
        when(repository.findBySavingsProductIdAndIsActive(productId, isActive))
            .thenReturn(Collections.emptyList());

        List<SavingsProductWithdrawalFrequencySetting> result = repository
            .findBySavingsProductIdAndIsActive(productId, isActive);

        assertTrue(result.isEmpty());
        verify(repository).findBySavingsProductIdAndIsActive(productId, isActive);
    }

    private SavingsProductWithdrawalFrequencySetting createSetting(Long productId, Integer maxWithdrawals, TimePeriod timePeriod) {
        SavingsProductWithdrawalFrequencySetting setting = new SavingsProductWithdrawalFrequencySetting();
        setting.setSavingsProductId(productId);
        setting.setMaxWithdrawals(maxWithdrawals);
        setting.setTimePeriod(timePeriod);
        setting.setIsActive(true);
        return setting;
    }
}
