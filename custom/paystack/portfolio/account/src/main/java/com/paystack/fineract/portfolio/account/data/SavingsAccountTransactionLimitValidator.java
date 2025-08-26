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

package com.paystack.fineract.portfolio.account.data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSetting;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSettingRepository;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationLimitMapping;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationMappingRepository;
import com.paystack.fineract.tier.service.exception.SavingsAccountTransactionLimitSettingNotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SavingsAccountTransactionLimitValidator {

    final SavingsAccountGlobalTransactionLimitSettingRepository savingsAccountGlobalTransactionLimitSettingRepository;
    final SavingsClientClassificationMappingRepository savingsClientClassificationMappingRepository;

    public boolean isDepositTransactionExceedsLimits(Client client, SavingsAccount savingsAccount, LocalDate transactionDate, BigDecimal transactionAmount) {
        Optional<SavingsClientClassificationLimitMapping> mappingOptional = savingsClientClassificationMappingRepository.findByClassificationId(client.getClientClassification().getId());
        if(mappingOptional.isPresent()){
            SavingsClientClassificationLimitMapping mapping = mappingOptional.get();
            Long transactionLimitId = mapping.getSavingsAccountGlobalTransactionLimitSetting().getId();
            SavingsAccountGlobalTransactionLimitSetting globalLimit = savingsAccountGlobalTransactionLimitSettingRepository
                .findById(transactionLimitId)
                .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

            Money maxSingleDepositAmountLimitMoney = Money.of(savingsAccount.getCurrency(), globalLimit.getTransactionLimits().getMaxSingleDepositAmount());
            Money balanceCumulativeLimitMoney = Money.of(savingsAccount.getCurrency(), globalLimit.getTransactionLimits().getBalanceCumulative());
            Money transactionAmountMoney = Money.of(savingsAccount.getCurrency(), transactionAmount);
            Money runningBalance = savingsAccount.getSummary().getAccountBalance(savingsAccount.getCurrency());

            if(transactionAmountMoney.isGreaterThan(maxSingleDepositAmountLimitMoney)) {
                return true;
            }
            if(runningBalance.plus(transactionAmount).isGreaterThan(balanceCumulativeLimitMoney)) {
                return true;
            }
        }
        return true;
    }
}
