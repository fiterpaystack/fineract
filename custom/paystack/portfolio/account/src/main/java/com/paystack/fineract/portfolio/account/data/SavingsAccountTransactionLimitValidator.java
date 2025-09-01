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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountSubStatusEnum;
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
        if(client.getClientClassification() != null) {
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

                if (transactionAmountMoney.isGreaterThan(maxSingleDepositAmountLimitMoney)) {
                    markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Max Single Deposit Amount Limit", transactionAmount);
                }
                if (runningBalance.plus(transactionAmount).isGreaterThan(balanceCumulativeLimitMoney)) {
                    markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit", transactionAmount);
                }
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
        String noteWithoutTransaction = "Savings Account : " + account.getId() + " is blocked for debit transactions  because the transaction limit : "
                + limitName + " exceeds the limit set by Client's classification %s";
        String note = String.format(noteWithoutTransaction, transactionAmount != null ? "with transaction value : " + transactionAmount : "");
        final Note newNote = Note.savingNote(account, note);
        this.noteRepository.saveAndFlush(newNote);
    }

    public void markSavingsAccountAsUnBlockDebitWithNote(SavingsAccount account, String limitName) {
        // make account UNBLOCK_DEBIT if the limit reached
        final Map<String, Object> changes = account.unblockDebits();
        if (!changes.isEmpty()) {
            this.savingsAccountRepository.save(account);
        }

        // UNBLOCKDEBIT NOTE
        final String note = "Savings Account : " + account.getId() + " is un blocked for debit transactions  because the transaction limit : "
                + limitName + " is within the limit set by Client's classification limit";
        final Note newNote = Note.savingNote(account, note);
        this.noteRepository.saveAndFlush(newNote);
    }

    public void updateSavingsAccountsForClassification(Client client, Long newClassificationId) {
        final List<SavingsAccount> clientSavingAccounts = this.savingsAccountRepository.findSavingAccountByClientId(client.getId());
        for (final SavingsAccount savingsAccount : clientSavingAccounts) {
            Optional<SavingsClientClassificationLimitMapping> mappingOptional = savingsClientClassificationMappingRepository
                    .findByClassificationId(newClassificationId);
            if (mappingOptional.isPresent()) {
                SavingsClientClassificationLimitMapping mapping = mappingOptional.get();
                Long transactionLimitId = mapping.getSavingsAccountGlobalTransactionLimitSetting().getId();
                SavingsAccountGlobalTransactionLimitSetting globalLimit = savingsAccountGlobalTransactionLimitSettingRepository
                        .findById(transactionLimitId)
                        .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

                Money balanceCumulativeLimitMoney = Money.of(savingsAccount.getCurrency(),
                        globalLimit.getTransactionLimits().getBalanceCumulative());
                Money runningBalance = savingsAccount.getSummary().getAccountBalance(savingsAccount.getCurrency());

                // if the account is blocked debit, then check the balance cumulative limit against the new classification to unblock
                SavingsAccountSubStatusEnum currentSubStatus = SavingsAccountSubStatusEnum.fromInt(savingsAccount.getSubStatus());
                if ( currentSubStatus.hasStateOf(SavingsAccountSubStatusEnum.BLOCK_DEBIT) && (runningBalance.isLessThan(balanceCumulativeLimitMoney) || runningBalance.isEqualTo(balanceCumulativeLimitMoney))) {
                    markSavingsAccountAsUnBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit");
                }

                // check the balance cumulative limit against the new classification to block
                if ( !currentSubStatus.hasStateOf(SavingsAccountSubStatusEnum.BLOCK_DEBIT) && runningBalance.isGreaterThan(balanceCumulativeLimitMoney)) {
                    markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit", null);
                }
            }
        }
    }
}
