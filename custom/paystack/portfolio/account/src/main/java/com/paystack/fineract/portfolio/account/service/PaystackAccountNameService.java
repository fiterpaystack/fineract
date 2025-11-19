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

import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccount;
import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccountRepository;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaystackAccountNameService {

    private final PaystackSavingsAccountRepository paystackSavingsAccountRepository;
    private final SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper;

    /**
     * Upserts the account name for the given savings account. When the provided {@code requestedAccountName} is blank
     * or null, the client's display name is used as the default, falling back to the account number if no client name
     * is available.
     */
    @Transactional
    public void syncAccountName(Long savingsAccountId, String requestedAccountName) {
        if (savingsAccountId == null) {
            return;
        }

        SavingsAccount savingsAccount = savingsAccountRepositoryWrapper.findOneWithNotFoundDetection(savingsAccountId);
        PaystackSavingsAccount extension = paystackSavingsAccountRepository.findById(savingsAccountId).orElseGet(() -> {
            PaystackSavingsAccount account = new PaystackSavingsAccount();
            account.setId(savingsAccountId);
            account.setSavingsAccount(savingsAccount);
            account.setTotalVatAmountDerived(BigDecimal.ZERO);
            return account;
        });

        extension.setAccountName(resolveAccountName(savingsAccount, requestedAccountName));
        if (extension.getTotalVatAmountDerived() == null) {
            extension.setTotalVatAmountDerived(BigDecimal.ZERO);
        }

        paystackSavingsAccountRepository.save(extension);
    }

    /**
     * Resolves the stored account name for a single account.
     */
    @Transactional(readOnly = true)
    public Optional<String> fetchAccountName(Long savingsAccountId) {
        if (savingsAccountId == null) {
            return Optional.empty();
        }
        return paystackSavingsAccountRepository.findById(savingsAccountId).map(PaystackSavingsAccount::getAccountName)
                .filter(StringUtils::hasText);
    }

    /**
     * Resolves stored account names for a batch of accounts.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> fetchAccountNames(Collection<Long> savingsAccountIds) {
        if (savingsAccountIds == null || savingsAccountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        return paystackSavingsAccountRepository.findByIdIn(savingsAccountIds).stream()
                .filter(acc -> StringUtils.hasText(acc.getAccountName())).collect(Collectors.toMap(PaystackSavingsAccount::getId,
                        PaystackSavingsAccount::getAccountName, (existing, replacement) -> replacement));
    }

    /**
     * Calculates the default account name that should be used when no custom name is provided.
     */
    public String resolveAccountName(SavingsAccount savingsAccount, String requestedAccountName) {
        if (StringUtils.hasText(requestedAccountName)) {
            return requestedAccountName.trim();
        }

        Client client = savingsAccount.getClient();
        if (client != null && StringUtils.hasText(client.getDisplayName())) {
            return client.getDisplayName();
        }

        return savingsAccount.getAccountNumber();
    }
}
