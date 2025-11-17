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

import com.paystack.fineract.portfolio.account.domain.SavingsAccountWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.account.repository.SavingsAccountWithdrawalFrequencySettingRepository;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import com.paystack.fineract.portfolio.savings.domain.SavingsProductWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import com.paystack.fineract.portfolio.savings.repository.SavingsProductWithdrawalFrequencySettingRepository;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.PaginationHelper;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.utils.ColumnValidator;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountReadPlatformServiceImpl;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public class PaystackSavingsAccountReadPlatformServiceImpl extends SavingsAccountReadPlatformServiceImpl {

    private final SavingsAccountWithdrawalFrequencySettingRepository accountSettingRepository;
    private final SavingsProductWithdrawalFrequencySettingRepository productSettingRepository;
    private final PaystackAccountNameService paystackAccountNameService;

    public PaystackSavingsAccountReadPlatformServiceImpl(PlatformSecurityContext context, JdbcTemplate jdbcTemplate,
            SavingsAccountAssembler savingAccountAssembler, PaginationHelper paginationHelper, ColumnValidator columnValidator,
            DatabaseSpecificSQLGenerator sqlGenerator, SavingsAccountRepositoryWrapper savingsAccountRepositoryWrapper,
            SavingsAccountWithdrawalFrequencySettingRepository accountSettingRepository,
            SavingsProductWithdrawalFrequencySettingRepository productSettingRepository,
            PaystackAccountNameService paystackAccountNameService) {
        super(context, jdbcTemplate, savingAccountAssembler, paginationHelper, columnValidator, sqlGenerator,
                savingsAccountRepositoryWrapper);
        this.accountSettingRepository = accountSettingRepository;
        this.productSettingRepository = productSettingRepository;
        this.paystackAccountNameService = paystackAccountNameService;
    }

    @Override
    public SavingsAccountData retrieveOne(Long savingsId) {
        SavingsAccountData account = super.retrieveOne(savingsId);
        applyAccountName(account);
        try {
            // Account-level settings
            List<SavingsAccountWithdrawalFrequencySetting> accountSettings = accountSettingRepository
                    .findBySavingsAccountIdAndIsActive(savingsId, true);
            List<WithdrawalFrequencySettingData> dtoAccount = accountSettings.stream()
                    .map(s -> new WithdrawalFrequencySettingData(s.getMaxWithdrawals(), s.getTimePeriod(), s.getIsActive())).toList();

            // Product-level settings (fallback)
            Long productId = account.getSavingsProductId();
            List<SavingsProductWithdrawalFrequencySetting> productSettings = productSettingRepository
                    .findBySavingsProductIdAndIsActive(productId, true);
            List<WithdrawalFrequencySettingData> dtoProduct = productSettings.stream()
                    .map(s -> new WithdrawalFrequencySettingData(s.getMaxWithdrawals(), s.getTimePeriod(), s.isActive())).toList();

            // Effective = account overrides win, else product
            Map<TimePeriod, WithdrawalFrequencySettingData> byPeriod = new EnumMap<>(TimePeriod.class);
            for (WithdrawalFrequencySettingData p : dtoProduct) {
                byPeriod.put(p.getTimePeriod(), p);
            }
            for (WithdrawalFrequencySettingData a : dtoAccount) {
                byPeriod.put(a.getTimePeriod(), a);
            }
            List<WithdrawalFrequencySettingData> dtoEffective = byPeriod.values().stream().toList();

            Map<String, Object> wf = new HashMap<>();
            wf.put("effective", dtoEffective);
            wf.put("account", dtoAccount);
            wf.put("product", dtoProduct);

            Map<String, Object> additional = new HashMap<>();
            additional.put("withdrawalFrequencySettings", wf);
            account.setAdditionalAttributes(additional);
            log.info("Additional attributes set: {}", additional);
        } catch (Exception e) {
            log.warn("Failed to load withdrawal settings for account {}", savingsId, e);
        }
        return account;
    }

    @Override
    public Collection<SavingsAccountData> retrieveAllForLookup(final Long clientId) {
        Collection<SavingsAccountData> accounts = super.retrieveAllForLookup(clientId);
        enrichAccountNames(accounts);
        return accounts;
    }

    @Override
    public Collection<SavingsAccountData> retrieveActiveForLookup(final Long clientId, DepositAccountType depositAccountType) {
        Collection<SavingsAccountData> accounts = super.retrieveActiveForLookup(clientId, depositAccountType);
        enrichAccountNames(accounts);
        return accounts;
    }

    @Override
    public Collection<SavingsAccountData> retrieveActiveForLookup(final Long clientId, DepositAccountType depositAccountType,
            String currencyCode) {
        Collection<SavingsAccountData> accounts = super.retrieveActiveForLookup(clientId, depositAccountType, currencyCode);
        enrichAccountNames(accounts);
        return accounts;
    }

    @Override
    public Page<SavingsAccountData> retrieveAll(SearchParameters searchParameters) {
        Page<SavingsAccountData> page = super.retrieveAll(searchParameters);
        enrichAccountNames(page.getPageItems());
        return page;
    }

    private void applyAccountName(SavingsAccountData account) {
        if (account == null) {
            return;
        }
        paystackAccountNameService.fetchAccountName(account.getId()).ifPresent(account::setAccountName);
    }

    private void enrichAccountNames(Collection<SavingsAccountData> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return;
        }
        Map<Long, String> names = paystackAccountNameService.fetchAccountNames(accounts.stream().map(SavingsAccountData::getId).toList());
        accounts.forEach(account -> {
            String resolved = names.get(account.getId());
            if (resolved != null) {
                account.setAccountName(resolved);
            }
        });
    }
}
