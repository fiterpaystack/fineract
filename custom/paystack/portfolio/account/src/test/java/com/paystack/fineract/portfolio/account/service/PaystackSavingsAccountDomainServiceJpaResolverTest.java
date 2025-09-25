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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import com.paystack.fineract.portfolio.discount.service.ProductDiscountService;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributesRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaystackSavingsAccountDomainServiceJpaResolverTest {

    private ClientChargeOverrideReadService clientChargeOverrideReadService;
    private PaystackSavingsAccountDomainServiceJpa service;

    @BeforeEach
    void setUp() {
        // Mocks for heavy constructor dependencies
        SavingsAccountRepositoryWrapper savingsAccountRepository = mock(SavingsAccountRepositoryWrapper.class);
        SavingsAccountTransactionRepository savingsAccountTransactionRepository = mock(SavingsAccountTransactionRepository.class);
        ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper = mock(ApplicationCurrencyRepositoryWrapper.class);
        JournalEntryWritePlatformService journalEntryWritePlatformService = mock(JournalEntryWritePlatformService.class);
        ConfigurationDomainService configurationDomainService = mock(ConfigurationDomainService.class);
        PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository = mock(
                DepositAccountOnHoldTransactionRepository.class);
        BusinessEventNotifierService businessEventNotifierService = mock(BusinessEventNotifierService.class);
        NoteRepository noteRepository = mock(NoteRepository.class);
        SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper = mock(
                SavingsAccountTransactionSummaryWrapper.class);
        SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService = mock(
                SavingsAccountChargePaymentWrapperService.class);
        clientChargeOverrideReadService = mock(ClientChargeOverrideReadService.class);
        PaystackSavingsProductAttributesRepository savingsProductAttributesRepository = mock(
                PaystackSavingsProductAttributesRepository.class);
        FeeSplitService feeSplitService = mock(FeeSplitService.class);
        ProductDiscountService productDiscountService = mock(ProductDiscountService.class);

        service = new PaystackSavingsAccountDomainServiceJpa(savingsAccountRepository, savingsAccountTransactionRepository,
                applicationCurrencyRepositoryWrapper, journalEntryWritePlatformService, configurationDomainService, context,
                depositAccountOnHoldTransactionRepository, businessEventNotifierService, noteRepository, null,
                savingsAccountTransactionSummaryWrapper, savingsAccountChargePaymentWrapperService, clientChargeOverrideReadService,
                savingsProductAttributesRepository, feeSplitService, productDiscountService);
    }

    private BigDecimal invokeResolve(Long clientId, Charge chargeDef, BigDecimal txnAmount) throws Exception {
        Method method = PaystackSavingsAccountDomainServiceJpa.class.getDeclaredMethod("resolveChargePrimaryValue", Long.class,
                org.apache.fineract.portfolio.charge.domain.Charge.class, BigDecimal.class, boolean.class);
        method.setAccessible(true);
        Object result = method.invoke(service, clientId, chargeDef, txnAmount, false);
        return (BigDecimal) result;
    }

    @Test
    void overrideShouldTakePrecedenceOverTiered_smallAmount() throws Exception {
        Long clientId = 1L;
        BigDecimal txn = new BigDecimal("2000");

        ClientChargeOverride ov = Mockito.mock(ClientChargeOverride.class);
        when(ov.getAmount()).thenReturn(new BigDecimal("15"));
        when(clientChargeOverrideReadService.getActiveOverride(Mockito.eq(clientId), Mockito.anyLong())).thenReturn(Optional.of(ov));

        org.apache.fineract.portfolio.charge.domain.Charge chargeDef = mock(org.apache.fineract.portfolio.charge.domain.Charge.class);
        when(chargeDef.getId()).thenReturn(10L);
        when(chargeDef.getHasVaryingCharge()).thenReturn(true);
        when(chargeDef.calculateChargeAmount(txn)).thenReturn(new BigDecimal("10"));

        BigDecimal resolved = invokeResolve(clientId, chargeDef, txn);
        assertThat(resolved).isEqualByComparingTo("15");
    }

    @Test
    void overrideShouldTakePrecedenceOverTiered_largeAmount() throws Exception {
        Long clientId = 1L;
        BigDecimal txn = new BigDecimal("100000");

        ClientChargeOverride ov = Mockito.mock(ClientChargeOverride.class);
        when(ov.getAmount()).thenReturn(new BigDecimal("15"));
        when(clientChargeOverrideReadService.getActiveOverride(Mockito.eq(clientId), Mockito.anyLong())).thenReturn(Optional.of(ov));

        org.apache.fineract.portfolio.charge.domain.Charge chargeDef = mock(org.apache.fineract.portfolio.charge.domain.Charge.class);
        when(chargeDef.getId()).thenReturn(11L);
        when(chargeDef.getHasVaryingCharge()).thenReturn(true);
        when(chargeDef.calculateChargeAmount(txn)).thenReturn(new BigDecimal("50"));

        BigDecimal resolved = invokeResolve(clientId, chargeDef, txn);
        assertThat(resolved).isEqualByComparingTo("15");
    }

    @Test
    void tieredShouldApplyWhenNoOverride_lowTier() throws Exception {
        Long clientId = 2L;
        BigDecimal txn = new BigDecimal("4000");
        when(clientChargeOverrideReadService.getActiveOverride(Mockito.eq(clientId), Mockito.anyLong())).thenReturn(Optional.empty());

        org.apache.fineract.portfolio.charge.domain.Charge chargeDef = mock(org.apache.fineract.portfolio.charge.domain.Charge.class);
        when(chargeDef.getId()).thenReturn(12L);
        when(chargeDef.getHasVaryingCharge()).thenReturn(true);
        when(chargeDef.calculateChargeAmount(txn)).thenReturn(new BigDecimal("10"));

        BigDecimal resolved = invokeResolve(clientId, chargeDef, txn);
        assertThat(resolved).isEqualByComparingTo("10");
    }

    @Test
    void tieredShouldApplyWhenNoOverride_midTier() throws Exception {
        Long clientId = 3L;
        BigDecimal txn = new BigDecimal("25000");
        when(clientChargeOverrideReadService.getActiveOverride(Mockito.eq(clientId), Mockito.anyLong())).thenReturn(Optional.empty());

        org.apache.fineract.portfolio.charge.domain.Charge chargeDef = mock(org.apache.fineract.portfolio.charge.domain.Charge.class);
        when(chargeDef.getId()).thenReturn(13L);
        when(chargeDef.getHasVaryingCharge()).thenReturn(true);
        when(chargeDef.calculateChargeAmount(txn)).thenReturn(new BigDecimal("25"));

        BigDecimal resolved = invokeResolve(clientId, chargeDef, txn);
        assertThat(resolved).isEqualByComparingTo("25");
    }

    @Test
    void fallbackToProductAmountWhenNoOverrideAndNoTier() throws Exception {
        Long clientId = 4L;
        BigDecimal txn = new BigDecimal("5000");
        when(clientChargeOverrideReadService.getActiveOverride(Mockito.eq(clientId), Mockito.anyLong())).thenReturn(Optional.empty());

        org.apache.fineract.portfolio.charge.domain.Charge chargeDef = mock(org.apache.fineract.portfolio.charge.domain.Charge.class);
        when(chargeDef.getId()).thenReturn(14L);
        when(chargeDef.getHasVaryingCharge()).thenReturn(false);
        when(chargeDef.getAmount()).thenReturn(new BigDecimal("7"));

        // resolvePrimaryAmount fallback branch returns product charge amount
        when(clientChargeOverrideReadService.resolvePrimaryAmount(clientId, chargeDef, null)).thenReturn(new BigDecimal("7"));

        // resolvePrimaryAmount(client, charge, null) should fallback to charge.getAmount()
        BigDecimal resolved = invokeResolve(clientId, chargeDef, txn);
        assertThat(resolved).isEqualByComparingTo("7");
    }
}
