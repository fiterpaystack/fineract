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

import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.service.KafkaNotificationService;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSetting;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSettingRepository;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationLimitMapping;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationMappingRepository;
import com.paystack.fineract.tier.service.exception.SavingsAccountTransactionLimitSettingNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountSubStatusEnum;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class SavingsAccountTransactionLimitValidator {

    private static final String BLOCK_DEBIT = "BLOCK_DEBIT";
    private static final String UNBLOCK_DEBIT = "UNBLOCK_DEBIT";

    final SavingsAccountGlobalTransactionLimitSettingRepository savingsAccountGlobalTransactionLimitSettingRepository;
    final SavingsClientClassificationMappingRepository savingsClientClassificationMappingRepository;
    final SavingsAccountRepository savingsAccountRepository;
    final NoteRepository noteRepository;

    @Autowired
    private KafkaNotificationRepository kafkaNotificationRepository;

    @Autowired
    private KafkaNotificationService kafkaNotificationService;

    public void isDepositTransactionExceedsLimits(SavingsAccountTransaction deposit) {
        Client client = deposit.getSavingsAccount().getClient();
        SavingsAccount savingsAccount = deposit.getSavingsAccount();
        BigDecimal transactionAmount = deposit.getAmount();
        if (client.getClientClassification() != null) {
            Optional<SavingsClientClassificationLimitMapping> mappingOptional = savingsClientClassificationMappingRepository
                    .findByClassificationId(client.getClientClassification().getId());
            if (mappingOptional.isPresent()) {
                SavingsClientClassificationLimitMapping mapping = mappingOptional.get();
                Long transactionLimitId = mapping.getSavingsAccountGlobalTransactionLimitSetting().getId();
                SavingsAccountGlobalTransactionLimitSetting globalLimit = null;
                if (transactionLimitId != null) {
                    globalLimit = savingsAccountGlobalTransactionLimitSettingRepository.findById(transactionLimitId)
                            .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

                    Money maxSingleDepositAmountLimitMoney = Money.of(savingsAccount.getCurrency(),
                            globalLimit.getTransactionLimits().getMaxSingleDepositAmount());
                    Money balanceCumulativeLimitMoney = Money.of(savingsAccount.getCurrency(),
                            globalLimit.getTransactionLimits().getBalanceCumulative());
                    Money transactionAmountMoney = Money.of(savingsAccount.getCurrency(), transactionAmount);
                    Money runningBalance = savingsAccount.getSummary().getAccountBalance(savingsAccount.getCurrency());

                    if (transactionAmountMoney.isGreaterThan(maxSingleDepositAmountLimitMoney)) {
                        markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Max Single Deposit Amount Limit", deposit);
                    }
                    if (runningBalance.plus(transactionAmount).isGreaterThan(balanceCumulativeLimitMoney)) {
                        markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit", deposit);
                    }
                }
            }
        }
    }

    public void markSavingsAccountAsBlockDebitWithNote(SavingsAccount account, String limitName, SavingsAccountTransaction transaction) {
        // make account BLOCK_DEBIT if the limit reached
        final Map<String, Object> changes = account.blockDebits(account.getSubStatus());
        if (!changes.isEmpty()) {
            this.savingsAccountRepository.save(account);
        }

        // Extract transaction amount if transaction is provided
        BigDecimal transactionAmount = transaction != null ? transaction.getAmount() : null;

        // BLOCKDEBIT NOTE
        String noteWithoutTransaction = "Savings Account : " + account.getId()
                + " is blocked for debit transactions  because the transaction limit : " + limitName
                + " exceeds the limit set by Client's classification %s";
        String note = String.format(noteWithoutTransaction,
                transactionAmount != null ? "with transaction value : " + transactionAmount : "");
        final Note newNote = Note.savingNote(account, note);
        this.noteRepository.saveAndFlush(newNote);

        // Send notification to Kafka with enhanced transaction details
        String transactionDetails = "";
        if (transaction != null) {
            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("Transaction ID: ").append(transaction.getId());
            detailsBuilder.append("; Date: ").append(transaction.getTransactionDate());
            detailsBuilder.append("; Amount: ").append(transactionAmount);

            // Add transaction type
            if (transaction.getTransactionType() != null) {
                detailsBuilder.append("; Type: ").append(transaction.getTransactionType().name());
            } else {
                detailsBuilder.append("; Type: ").append(transaction.getTypeOf());
            }

            // Add reference number if available
            if (transaction.getRefNo() != null) {
                detailsBuilder.append("; Reference: ").append(transaction.getRefNo());
            }

            transactionDetails = detailsBuilder.toString();
        }

        sendKafkaNotification(BLOCK_DEBIT, account, limitName, transactionDetails);
    }

    public void markSavingsAccountAsUnBlockDebitWithNote(SavingsAccount account, String limitName) {
        // make account UNBLOCK_DEBIT if the limit reached
        final Map<String, Object> changes = account.unblockDebits();
        if (!changes.isEmpty()) {
            this.savingsAccountRepository.save(account);
        }

        // UNBLOCKDEBIT NOTE
        final String note = "Savings Account : " + account.getId()
                + " is un blocked for debit transactions  because the transaction limit : " + limitName
                + " is within the limit set by Client's classification limit";
        final Note newNote = Note.savingNote(account, note);
        this.noteRepository.saveAndFlush(newNote);

        // Send notification to Kafka
        sendKafkaNotification(UNBLOCK_DEBIT, account, limitName, null);
    }

    public void updateClientSavingsAccountsSubStatusForClassification(Client client, Long newClassificationId) {
        final List<SavingsAccount> clientSavingAccounts = this.savingsAccountRepository.findSavingAccountByClientId(client.getId());
        for (final SavingsAccount savingsAccount : clientSavingAccounts) {
            Optional<SavingsClientClassificationLimitMapping> mappingOptional = savingsClientClassificationMappingRepository
                    .findByClassificationId(newClassificationId);
            if (mappingOptional.isPresent()) {
                SavingsClientClassificationLimitMapping mapping = mappingOptional.get();
                Long transactionLimitId = mapping.getSavingsAccountGlobalTransactionLimitSetting().getId();
                SavingsAccountGlobalTransactionLimitSetting globalLimit = null;
                if (transactionLimitId != null) {
                    globalLimit = savingsAccountGlobalTransactionLimitSettingRepository.findById(transactionLimitId)
                            .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

                    Money balanceCumulativeLimitMoney = Money.of(savingsAccount.getCurrency(),
                            globalLimit.getTransactionLimits().getBalanceCumulative());
                    Money runningBalance = savingsAccount.getSummary().getAccountBalance(savingsAccount.getCurrency());

                    // if the account is blocked debit, then check the balance cumulative limit against the new
                    // classification to unblock
                    SavingsAccountSubStatusEnum currentSubStatus = SavingsAccountSubStatusEnum.fromInt(savingsAccount.getSubStatus());
                    if (currentSubStatus.hasStateOf(SavingsAccountSubStatusEnum.BLOCK_DEBIT)
                            && (runningBalance.isLessThan(balanceCumulativeLimitMoney)
                                    || runningBalance.isEqualTo(balanceCumulativeLimitMoney))) {
                        markSavingsAccountAsUnBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit");
                    }

                    // check the balance cumulative limit against the new classification to block
                    if (!currentSubStatus.hasStateOf(SavingsAccountSubStatusEnum.BLOCK_DEBIT)
                            && runningBalance.isGreaterThan(balanceCumulativeLimitMoney)) {
                        markSavingsAccountAsBlockDebitWithNote(savingsAccount, "Balance Cumulative Limit", null);
                    }
                }
            }
        }
    }

    /**
     * Sends a notification to Kafka about account status changes asynchronously.
     *
     * @param eventType
     *            the type of event (e.g., "BLOCK_DEBIT", "UNBLOCK_DEBIT")
     * @param account
     *            the savings account that is affected
     * @param limitName
     *            the name of the limit that triggered the status change
     * @param transactionDetails
     *            optional details about the transaction (can be null)
     */
    private void sendKafkaNotification(String eventType, SavingsAccount account, String limitName, String transactionDetails) {
        if (kafkaNotificationService != null && kafkaNotificationRepository != null) {
            try {
                // Create and save notification
                KafkaNotification notification = new KafkaNotification(eventType, account, limitName, transactionDetails);
                notification = kafkaNotificationRepository.save(notification);

                // Send asynchronously - this won't block the business operation
                kafkaNotificationService.sendNotificationAsync(notification).exceptionally(throwable -> {
                    log.error("Failed to send Kafka notification asynchronously for account: {} event: {}", account.getId(), eventType,
                            throwable);
                    return null;
                });

                log.info("Kafka notification queued for async processing. Account: {} Event: {} Notification ID: {}", account.getId(),
                        eventType, notification.getId());

            } catch (Exception e) {
                log.error("Error creating Kafka notification for account: {} event: {}", account.getId(), eventType, e);
                // Don't throw exception to avoid breaking the business operation
            }
        } else {
            log.warn("Kafka notification service or repository not available, skipping notification for account: {} event: {}",
                    account.getId(), eventType);
        }
    }
}
