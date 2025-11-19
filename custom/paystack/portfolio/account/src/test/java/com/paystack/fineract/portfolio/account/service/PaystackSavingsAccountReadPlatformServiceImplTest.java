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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.account.repository.SavingsAccountWithdrawalFrequencySettingRepository;
import com.paystack.fineract.portfolio.savings.repository.SavingsProductWithdrawalFrequencySettingRepository;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaystackSavingsAccountReadPlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private SavingsAccountAssembler savingsAccountAssembler;
    @Mock
    private PaginationHelper paginationHelper;
    @Mock
    private ColumnValidator columnValidator;
    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;
    @Mock
    private SavingsAccountWithdrawalFrequencySettingRepository accountSettingRepository;
    @Mock
    private SavingsProductWithdrawalFrequencySettingRepository productSettingRepository;
    @Mock
    private PaystackAccountNameService paystackAccountNameService;

    private PaystackSavingsAccountReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaystackSavingsAccountReadPlatformServiceImpl(context, jdbcTemplate, savingsAccountAssembler, paginationHelper,
                columnValidator, sqlGenerator, savingsAccountRepositoryWrapper, accountSettingRepository, productSettingRepository,
                paystackAccountNameService);
    }

    @Test
    void applyAccountNameShouldPopulateWhenValuePresent() throws Exception {
        SavingsAccountData account = org.mockito.Mockito.mock(SavingsAccountData.class);
        when(account.getId()).thenReturn(10L);
        when(paystackAccountNameService.fetchAccountName(10L)).thenReturn(Optional.of("Custom"));

        invokeApplyAccountName(account);

        verify(account).setAccountName("Custom");
    }

    @Test
    void applyAccountNameShouldSkipWhenNoValue() throws Exception {
        SavingsAccountData account = org.mockito.Mockito.mock(SavingsAccountData.class);
        when(account.getId()).thenReturn(11L);
        when(paystackAccountNameService.fetchAccountName(11L)).thenReturn(Optional.empty());

        invokeApplyAccountName(account);

        verify(account, never()).setAccountName(org.mockito.Mockito.anyString());
    }

    @Test
    void enrichAccountNamesShouldMapValues() throws Exception {
        SavingsAccountData account1 = org.mockito.Mockito.mock(SavingsAccountData.class);
        SavingsAccountData account2 = org.mockito.Mockito.mock(SavingsAccountData.class);
        when(account1.getId()).thenReturn(1L);
        when(account2.getId()).thenReturn(2L);
        when(paystackAccountNameService.fetchAccountNames(List.of(1L, 2L))).thenReturn(Map.of(1L, "Name One"));

        invokeEnrichAccountNames(List.of(account1, account2));

        verify(account1).setAccountName("Name One");
        verify(account2, never()).setAccountName(org.mockito.Mockito.anyString());
    }

    @Test
    void enrichAccountNamesShouldSkipEmptyCollection() throws Exception {
        invokeEnrichAccountNames(List.of());
        verify(paystackAccountNameService, never()).fetchAccountNames(org.mockito.Mockito.anyCollection());
    }

    private void invokeApplyAccountName(SavingsAccountData account) throws Exception {
        Method m = PaystackSavingsAccountReadPlatformServiceImpl.class.getDeclaredMethod("applyAccountName", SavingsAccountData.class);
        m.setAccessible(true);
        m.invoke(service, account);
    }

    private void invokeEnrichAccountNames(Collection<SavingsAccountData> accounts) throws Exception {
        Method m = PaystackSavingsAccountReadPlatformServiceImpl.class.getDeclaredMethod("enrichAccountNames", Collection.class);
        m.setAccessible(true);
        m.invoke(service, accounts);
    }
}
