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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccount;
import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccountRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaystackAccountNameServiceTest {

    private static final long SAVINGS_ID = 123L;

    @Mock
    private PaystackSavingsAccountRepository paystackSavingsAccountRepository;

    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;

    @InjectMocks
    private PaystackAccountNameService paystackAccountNameService;

    @Captor
    private ArgumentCaptor<PaystackSavingsAccount> savingsAccountCaptor;

    private SavingsAccount savingsAccount;
    private Client client;

    @BeforeEach
    void setUp() {
        savingsAccount = mock(SavingsAccount.class);
        client = mock(Client.class);
        when(savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(SAVINGS_ID)).thenReturn(savingsAccount);
        when(savingsAccount.getClient()).thenReturn(client);
        when(client.getDisplayName()).thenReturn("Client Display");
    }

    @Test
    void syncAccountNameShouldCreateExtensionWhenMissing() {
        when(paystackSavingsAccountRepository.findById(SAVINGS_ID)).thenReturn(Optional.empty());

        paystackAccountNameService.syncAccountName(SAVINGS_ID, "Custom Name");

        verify(paystackSavingsAccountRepository).save(savingsAccountCaptor.capture());
        PaystackSavingsAccount saved = savingsAccountCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(SAVINGS_ID);
        assertThat(saved.getSavingsAccount()).isEqualTo(savingsAccount);
        assertThat(saved.getAccountName()).isEqualTo("Custom Name");
        assertThat(saved.getTotalVatAmountDerived()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void syncAccountNameShouldFallbackToClientNameWhenNullProvided() {
        PaystackSavingsAccount existing = new PaystackSavingsAccount();
        existing.setId(SAVINGS_ID);
        existing.setSavingsAccount(savingsAccount);
        existing.setTotalVatAmountDerived(BigDecimal.valueOf(42));
        when(paystackSavingsAccountRepository.findById(SAVINGS_ID)).thenReturn(Optional.of(existing));

        paystackAccountNameService.syncAccountName(SAVINGS_ID, null);

        assertThat(existing.getAccountName()).isEqualTo("Client Display");
        verify(paystackSavingsAccountRepository).save(existing);
    }

    @Test
    void fetchAccountNamesShouldMapStoredValues() {
        PaystackSavingsAccount account = new PaystackSavingsAccount();
        account.setId(SAVINGS_ID);
        account.setAccountName("Stored Name");
        when(paystackSavingsAccountRepository.findByIdIn(any())).thenReturn(List.of(account));

        Map<Long, String> names = paystackAccountNameService.fetchAccountNames(List.of(SAVINGS_ID));

        assertThat(names).containsEntry(SAVINGS_ID, "Stored Name");
    }
}
