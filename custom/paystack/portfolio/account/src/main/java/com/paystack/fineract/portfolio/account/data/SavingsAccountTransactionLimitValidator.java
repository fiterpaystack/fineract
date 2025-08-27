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

import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSetting;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSettingRepository;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationLimitMapping;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationMappingRepository;
import com.paystack.fineract.tier.service.exception.SavingsAccountTransactionLimitSettingNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SavingsAccountTransactionLimitValidator {

    final SavingsAccountGlobalTransactionLimitSettingRepository savingsAccountGlobalTransactionLimitSettingRepository;
    final SavingsClientClassificationMappingRepository savingsClientClassificationMappingRepository;
    final SavingsAccountRepository savingsAccountRepository;
    final NoteRepository noteRepository;

    public void isDepositTransactionExceedsLimits(Client client, SavingsAccount savingsAccount, LocalDate transactionDate,
            BigDecimal transactionAmount) {
        Optional<SavingsClientClassificationLimitMapping> mappingOptional = savingsClientClassificationMappingRepository
                .findByClassificationId(client.getClientClassification().getId());
        if (mappingOptional.isPresent()) {
            SavingsClientClassificationLimitMapping mapping = mappingOptional.get();
            Long transactionLimitId = mapping.getSavingsAccountGlobalTransactionLimitSetting().getId();
            SavingsAccountGlobalTransactionLimitSetting globalLimit = savingsAccountGlobalTransactionLimitSettingRepository
                    .findById(transactionLimitId)
                    .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

            Money maxSingleDepositAmountLimitMoney = Money.of(savingsAccount.getCurrency(),
                    globalLimit.getTransactionLimits().getMaxSingleDepositAmount());
            Money balanceCumulativeLimitMoney = Money.of(savingsAccount.getCurrency(),
                    globalLimit.getTransactionLimits().getBalanceCumulative());
            Money transactionAmountMoney = Money.of(savingsAccount.getCurrency(), transactionAmount);
            Money runningBalance = savingsAccount.getSummary().getAccountBalance(savingsAccount.getCurrency());

            if (transactionAmountMoney.isGreaterThanOrEqualTo(maxSingleDepositAmountLimitMoney)) {
                markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Max Single Deposit Amount Limit", transactionAmount);
            }
            if (runningBalance.plus(transactionAmount).isGreaterThanOrEqualTo(balanceCumulativeLimitMoney)) {
                markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit", transactionAmount);
            }
        }
    }

    public void markSavingsAccountAsBlockDebitWithNote(SavingsAccount account, String limitName, BigDecimal transactionAmount) {
        // make account BLOCK_DEBIT if the limit reached
        final Map<String, Object> changes = account.blockDebits(account.getSubStatus());
        if (!changes.isEmpty()) {
            this.savingsAccountRepository.save(account);
        }
        // BLOCKDEBIT NOTE
        final String note = "Savings Account : " + account.getId() + " is blocked for debit transactions  because the transaction limit : "
                + limitName + " exceeds the limit set by Client's classification with transaction value : " + transactionAmount;
        final Note newNote = Note.savingNote(account, note);
        this.noteRepository.saveAndFlush(newNote);
    }
}
