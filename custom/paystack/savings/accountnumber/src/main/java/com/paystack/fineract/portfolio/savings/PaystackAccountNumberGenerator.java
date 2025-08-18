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
package com.paystack.fineract.portfolio.savings;

import com.paystack.fineract.portfolio.savings.service.ProductSequenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.savings.DepositAccountType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary // ensure this bean is used instead of the default one
public class PaystackAccountNumberGenerator extends AccountNumberGenerator {

    private final ProductSequenceService sequenceService;

    public PaystackAccountNumberGenerator(ConfigurationReadPlatformService configurationReadPlatformService,
            ClientRepository clientRepository, LoanRepository loanRepository, SavingsAccountRepository savingsAccountRepository,
            ProductSequenceService sequenceService) {
        super(configurationReadPlatformService, clientRepository, loanRepository, savingsAccountRepository);
        this.sequenceService = sequenceService;
        log.info("ProductScopedSavingsAccountNumberGenerator active: Savings account numbers will be product-scoped");
    }

    @Override
    public String generate(SavingsAccount account, AccountNumberFormat format) {
        if (account.savingsProduct().depositAccountType().equals(DepositAccountType.SAVINGS_DEPOSIT)) {
            // Acquire next per-product sequence and build the formatted account number
            Long productId;
            productId = account.savingsProduct().getId();

            long next = sequenceService.nextSavingsSeq(productId);
            String candidate = sequenceService.buildNubanSavingsAccountNo(productId, next);
            log.debug("Generated savings accountNo '{}' for product {} (seq={})", candidate, productId, next);
            return candidate;
        } else {
            return super.generate(account, format);
        }
    }
}
