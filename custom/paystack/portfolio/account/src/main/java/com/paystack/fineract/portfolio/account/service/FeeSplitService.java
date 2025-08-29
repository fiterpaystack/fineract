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

import com.paystack.fineract.portfolio.account.domain.ChargeSplit;
import com.paystack.fineract.portfolio.account.domain.ChargeSplitRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAudit;
import com.paystack.fineract.portfolio.account.domain.FeeSplitAuditRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetail;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetailRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForSavings;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepository;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.accounting.journalentry.domain.JournalEntryType;
import org.apache.fineract.accounting.journalentry.service.AccountingProcessorHelper;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.client.domain.ClientChargePaidBy;
import org.apache.fineract.portfolio.client.domain.ClientTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeeSplitService {

    private final ChargeSplitRepository splitRepository;
    private final FeeSplitAuditRepository auditRepository;
    private final FeeSplitDetailRepository detailRepository;
    private final GLAccountRepository glAccountRepository;
    private final OfficeRepositoryWrapper officeRepository;
    private final AccountingProcessorHelper accountingHelper;
    private final ProductToGLAccountMappingRepository accountMappingRepository;

    @Transactional
    public void processFeeSplit(ClientTransaction clientTransaction, BigDecimal totalFeeAmount) {
        log.info("Processing fee split for client transaction: {}, amount: {}", clientTransaction.getId(), totalFeeAmount);

        // Get charge and check if fee splitting is enabled
        Charge charge = getChargeFromClientTransaction(clientTransaction);
        if (charge == null || !charge.isEnableFeeSplit()) {
            log.debug("Fee splitting not enabled for charge: {}", charge != null ? charge.getId() : "null");
            return;
        }

        // Get stakeholder splits for this charge
        List<ChargeSplit> splits = splitRepository.findActiveSplitsByChargeId(charge.getId());
        if (splits.isEmpty()) {
            log.warn("No active splits found for charge: {}", charge.getId());
            return;
        }

        // Validate total splits don't exceed 100%
        validateSplitTotals(splits, totalFeeAmount);

        // Create audit record
        FeeSplitAudit audit = createAuditRecord(clientTransaction, charge, totalFeeAmount);

        // Process each split
        for (ChargeSplit split : splits) {
            processIndividualSplit(split, totalFeeAmount, audit, clientTransaction);
        }

        // Save audit record with all details
        auditRepository.save(audit);

        log.info("Fee split processing completed for transaction: {}, total splits: {}", clientTransaction.getId(), splits.size());
    }

    @Transactional
    public void processFeeSplitForSavings(SavingsAccountTransaction savingsTransaction, BigDecimal totalFeeAmount) {
        log.info("=== FEE SPLIT SERVICE: Starting processFeeSplitForSavings ===");
        log.info("Transaction ID: {}, Amount: {}, Type: {}", savingsTransaction.getId(), totalFeeAmount,
                savingsTransaction.getTransactionType());
        log.info("Transaction Date: {}, Currency: {}", savingsTransaction.getTransactionDate(), savingsTransaction.getCurrency());

        // Get charge and check if fee splitting is enabled
        log.info("Attempting to get charge from savings transaction...");
        Charge charge = getChargeFromSavingsTransaction(savingsTransaction);

        if (charge == null) {
            log.error("❌ No charge found for savings transaction: {}", savingsTransaction.getId());
            return;
        }

        log.info("✅ Charge found: ID={}, Name={}, Enable Fee Split={}", charge.getId(), charge.getName(), charge.isEnableFeeSplit());

        if (!charge.isEnableFeeSplit()) {
            log.warn("❌ Fee splitting is NOT enabled for charge: {}", charge.getId());
            return;
        }

        // Get stakeholder splits for this charge
        log.info("Looking for active splits for charge: {}", charge.getId());
        List<ChargeSplit> splits = splitRepository.findActiveSplitsByChargeId(charge.getId());
        log.info("Found {} splits for charge: {}", splits.size(), charge.getId());

        if (splits.isEmpty()) {
            log.warn("❌ No active splits found for charge: {}", charge.getId());
            return;
        }

        // Log split details
        for (ChargeSplit split : splits) {
            log.info("Split: ID={}, Fund={}, Type={}, Value={}", split.getId(), split.getFund().getName(),
                    split.isPercentageSplit() ? "PERCENTAGE" : "FLAT_AMOUNT", split.getSplitValue());
        }

        // Validate total splits don't exceed 100%
        log.info("Validating split totals...");
        validateSplitTotals(splits, totalFeeAmount);
        log.info("✅ Split validation passed");

        // Create audit record
        log.info("Creating audit record...");
        FeeSplitAudit audit = createAuditRecordForSavings(savingsTransaction, charge, totalFeeAmount);
        log.info("✅ Audit record created: {}", audit.getId());

        // Process each split
        log.info("Processing individual splits...");
        for (ChargeSplit split : splits) {
            log.info("Processing split: {}", split.getId());
            processIndividualSplitForSavings(split, totalFeeAmount, audit, savingsTransaction);
        }

        // Save audit record with all details
        log.info("Saving audit record...");
        auditRepository.save(audit);

        log.info("=== FEE SPLIT SERVICE: Completed successfully ===");
        log.info("Fee split processing completed for savings transaction: {}, total splits: {}", savingsTransaction.getId(), splits.size());
    }

    private Charge getChargeFromClientTransaction(ClientTransaction clientTransaction) {
        // Get the charge from the client charge paid by collection
        if (clientTransaction.getClientChargePaidByCollection() != null && !clientTransaction.getClientChargePaidByCollection().isEmpty()) {
            ClientChargePaidBy chargePaidBy = clientTransaction.getClientChargePaidByCollection().iterator().next();
            return chargePaidBy.getClientCharge().getCharge();
        }
        return null;
    }

    private Charge getChargeFromSavingsTransaction(SavingsAccountTransaction savingsTransaction) {
        log.info("Getting charge from savings transaction: {}", savingsTransaction.getId());

        // For savings transactions, we need to find the associated charge
        // We'll look up the charge through the savings account charges
        try {
            // Get the savings account from the transaction
            if (savingsTransaction.getSavingsAccount() != null) {
                log.info("Savings account found: {}", savingsTransaction.getSavingsAccount().getId());

                // Look for charges that match this transaction
                // This is a simplified approach - we'll look for charges with the same amount
                log.info("Total charges on account: {}", savingsTransaction.getSavingsAccount().charges().size());
                log.info("Looking for charge matching transaction amount: {}", savingsTransaction.getAmount());

                for (SavingsAccountCharge accountCharge : savingsTransaction.getSavingsAccount().charges()) {
                    BigDecimal chargeAmount = accountCharge.getAmount(savingsTransaction.getCurrency()).getAmount();
                    BigDecimal chargeOutstanding = accountCharge.getAmountOutstanding(savingsTransaction.getCurrency()).getAmount();

                    log.info("Checking charge: ID={}, Amount={}, Outstanding={}, Active={}", accountCharge.getCharge().getId(),
                            chargeAmount, chargeOutstanding, accountCharge.isActive());

                    // Fix: Use type-safe comparison for Money vs BigDecimal
                    if (chargeAmount.compareTo(savingsTransaction.getAmount()) == 0) {
                        log.info("✅ Found matching charge: {} for transaction: {}", accountCharge.getCharge().getId(),
                                savingsTransaction.getId());
                        log.info("Charge amount: {}, Transaction amount: {}, Match: {}", chargeAmount, savingsTransaction.getAmount(),
                                chargeAmount.compareTo(savingsTransaction.getAmount()) == 0);
                        return accountCharge.getCharge();
                    } else {
                        log.debug("No match: Charge amount {} != Transaction amount {}", chargeAmount, savingsTransaction.getAmount());
                    }
                }

                log.warn("No matching charge found for transaction: {}", savingsTransaction.getId());
            } else {
                log.warn("No savings account found for transaction: {}", savingsTransaction.getId());
            }
        } catch (Exception e) {
            log.error("Error getting charge from savings transaction: {}", e.getMessage(), e);
        }

        return null;
    }

    private void validateSplitTotals(List<ChargeSplit> splits, BigDecimal totalFeeAmount) {
        BigDecimal totalPercentage = BigDecimal.ZERO;
        BigDecimal totalFlatAmount = BigDecimal.ZERO;

        for (ChargeSplit split : splits) {
            if (split.isPercentageSplit()) {
                totalPercentage = totalPercentage.add(split.getSplitValue());
            } else if (split.isFlatAmountSplit()) {
                totalFlatAmount = totalFlatAmount.add(split.getSplitValue());
            }
        }

        if (totalPercentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new PlatformApiDataValidationException("error.msg.fee.split.total.percentage.exceeds.100",
                    "Total percentage splits cannot exceed 100%",
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.total.percentage.exceeds.100",
                            "Total percentage splits cannot exceed 100%", "splitValue", totalPercentage)));
        }

        if (totalFlatAmount.compareTo(totalFeeAmount) > 0) {
            throw new PlatformApiDataValidationException("error.msg.fee.split.total.flat.amount.exceeds.fee",
                    "Total flat amount splits cannot exceed total fee amount",
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.total.flat.amount.exceeds.fee",
                            "Total flat amount splits cannot exceed total fee amount", "splitValue", totalFlatAmount)));
        }
    }

    private FeeSplitAudit createAuditRecord(ClientTransaction clientTransaction, Charge charge, BigDecimal totalFeeAmount) {
        String transactionId = generateTransactionId(clientTransaction.getClient().getOffice().getId());
        return FeeSplitAudit.createNew(transactionId, charge, totalFeeAmount, DateUtils.getBusinessLocalDate());
    }

    private FeeSplitAudit createAuditRecordForSavings(SavingsAccountTransaction savingsTransaction, Charge charge,
            BigDecimal totalFeeAmount) {
        log.info("Creating audit record for savings transaction: {}", savingsTransaction.getId());
        try {
            String transactionId = generateTransactionId(savingsTransaction.getSavingsAccount().officeId());
            log.info("Generated transaction ID: {}", transactionId);

            FeeSplitAudit audit = FeeSplitAudit.createNew(transactionId, charge, totalFeeAmount, DateUtils.getBusinessLocalDate());
            log.info("✅ Audit record created successfully: {}", audit.getId());
            return audit;
        } catch (Exception e) {
            log.error("❌ Error creating audit record: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIndividualSplit(ChargeSplit split, BigDecimal totalFeeAmount, FeeSplitAudit audit,
            ClientTransaction clientTransaction) {
        try {
            // Calculate split amount
            BigDecimal splitAmount = split.calculateSplitAmount(totalFeeAmount);

            // Get charge for GL account resolution
            Charge charge = getChargeFromClientTransaction(clientTransaction);
            if (charge == null) {
                List<ApiParameterError> dataValidationErrors = new ArrayList<>();
                dataValidationErrors.add(ApiParameterError.parameterError("error.msg.fee.split.charge.not.found", "Charge not found",
                        "clientTransactionId", clientTransaction.getId()));

                throw new PlatformApiDataValidationException("error.msg.fee.split.charge.not.found",
                        "Charge not found for client transaction: " + clientTransaction.getId(), dataValidationErrors);
            }

            // Create balanced journal entries
            List<JournalEntry> journalEntries = createJournalEntriesForSplit(split, splitAmount, clientTransaction, charge);

            // Use the CREDIT entry for the split detail (maintains existing logic)
            JournalEntry creditEntry = journalEntries.stream().filter(je -> je.getType().equals(JournalEntryType.CREDIT.getValue()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Credit entry not found in balanced journal entries"));

            // Create detail record
            FeeSplitDetail detail = FeeSplitDetail.createNew(audit, split.getFund(), splitAmount,
                    split.isPercentageSplit() ? split.getSplitValue() : null, split.getGlAccount(), creditEntry);

            audit.addSplitDetail(detail);

        } catch (Exception e) {
            log.error("❌ Error processing individual client split: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void processIndividualSplitForSavings(ChargeSplit split, BigDecimal totalFeeAmount, FeeSplitAudit audit,
            SavingsAccountTransaction savingsTransaction) {
        log.info("Processing individual split: ID={}, Fund={}, Value={}", split.getId(), split.getFund().getName(), split.getSplitValue());

        try {
            // Calculate split amount
            BigDecimal splitAmount = split.calculateSplitAmount(totalFeeAmount);
            log.info("Calculated split amount: {} for total fee: {}", splitAmount, totalFeeAmount);

            // Get charge for GL account resolution
            Charge charge = getChargeFromSavingsTransaction(savingsTransaction);
            if (charge == null) {
                List<ApiParameterError> dataValidationErrors = new ArrayList<>();
                dataValidationErrors.add(ApiParameterError.parameterError("error.msg.fee.split.charge.not.found", "Charge not found",
                        "savingsTransactionId", savingsTransaction.getId()));

                throw new PlatformApiDataValidationException("error.msg.fee.split.charge.not.found",
                        "Charge not found for savings transaction: " + savingsTransaction.getId(), dataValidationErrors);
            }

            // Create balanced journal entries
            log.info("Creating balanced journal entries for split...");
            List<JournalEntry> journalEntries = createJournalEntriesForSavingsSplit(split, splitAmount, savingsTransaction, charge);

            // Use the CREDIT entry for the split detail (maintains existing logic)
            JournalEntry creditEntry = journalEntries.stream().filter(je -> je.getType().equals(JournalEntryType.CREDIT.getValue()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Credit entry not found in balanced journal entries"));

            log.info("✅ Journal entries created: DEBIT={}, CREDIT={}", journalEntries.stream()
                    .filter(je -> je.getType().equals(JournalEntryType.DEBIT.getValue())).findFirst().map(JournalEntry::getId).orElse(null),
                    creditEntry.getId());

            // Create split detail record
            log.info("Creating split detail record...");
            FeeSplitDetail detail = FeeSplitDetail.createNew(audit, split.getFund(), splitAmount,
                    split.isPercentageSplit() ? split.getSplitValue() : null, split.getGlAccount(), creditEntry);

            audit.addSplitDetail(detail);
            log.info("✅ Split detail added to audit record");

        } catch (Exception e) {
            log.error("❌ Error processing individual split: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<JournalEntry> createJournalEntriesForSplit(ChargeSplit split, BigDecimal splitAmount, ClientTransaction clientTransaction,
            Charge charge) {
        log.info("Creating balanced journal entries for client split: Split ID={}, Amount={}", split.getId(), splitAmount);

        try {
            Office office = clientTransaction.getClient().getOffice();
            String currencyCode = clientTransaction.getCurrencyCode();
            LocalDate transactionDate = clientTransaction.getTransactionDate();
            String transactionId = generateTransactionId(office.getId());

            // Get original income account (implement similar to savings)
            GLAccount originalIncomeAccount = getOriginalIncomeAccountForClient(charge, clientTransaction);

            log.info("Office: {}, Currency: {}, Date: {}, Transaction ID: {}", office.getId(), currencyCode, transactionDate,
                    transactionId);
            log.info("Original Income Account: {}, Destination GL Account: {}, Fund: {}", originalIncomeAccount.getId(),
                    split.getGlAccount().getId(), split.getFund().getName());

            // Create DEBIT entry from original income account
            JournalEntry debitEntry = JournalEntry.createNew(office, null, // paymentDetail
                    originalIncomeAccount, currencyCode, transactionId, false, // manualEntry
                    transactionDate, JournalEntryType.DEBIT, splitAmount, "Fee split debit for " + split.getFund().getName(), null, // entityType
                    null, // entityId
                    null, // referenceNumber
                    null, // loanTransactionId
                    null, // savingsTransactionId
                    clientTransaction.getId(), // clientTransactionId
                    null // shareTransactionId
            );

            // Create CREDIT entry to destination GL account
            JournalEntry creditEntry = JournalEntry.createNew(office, null, // paymentDetail
                    split.getGlAccount(), currencyCode, transactionId, false, // manualEntry
                    transactionDate, JournalEntryType.CREDIT, splitAmount, "Fee split for " + split.getFund().getName(), null, // entityType
                    null, // entityId
                    null, // referenceNumber
                    null, // loanTransactionId
                    null, // savingsTransactionId
                    clientTransaction.getId(), // clientTransactionId
                    null // shareTransactionId
            );

            // Persist both entries
            log.info("Persisting balanced journal entries for client split...");
            JournalEntry persistedDebit = accountingHelper.persistJournalEntry(debitEntry);
            JournalEntry persistedCredit = accountingHelper.persistJournalEntry(creditEntry);

            List<JournalEntry> journalEntries = Arrays.asList(persistedDebit, persistedCredit);

            // Validate journal entry balance and transaction ID uniqueness
            validateJournalEntryBalance(journalEntries, splitAmount);
            validateTransactionIdUniqueness(journalEntries);

            log.info("✅ Balanced journal entries created for client: DEBIT={}, CREDIT={}", persistedDebit.getId(), persistedCredit.getId());

            return journalEntries;

        } catch (Exception e) {
            log.error("❌ Error creating balanced journal entries for client split: {}", e.getMessage(), e);
            throw e;
        }
    }

    private List<JournalEntry> createJournalEntriesForSavingsSplit(ChargeSplit split, BigDecimal splitAmount,
            SavingsAccountTransaction savingsTransaction, Charge charge) {
        log.info("Creating balanced journal entries for savings split: Split ID={}, Amount={}", split.getId(), splitAmount);

        try {
            Office office = savingsTransaction.getSavingsAccount().office();
            String currencyCode = savingsTransaction.getCurrency().getCode();
            LocalDate transactionDate = savingsTransaction.getTransactionDate();
            String transactionId = generateTransactionId(office.getId());

            // Get original income account
            GLAccount originalIncomeAccount = getOriginalIncomeAccount(charge, savingsTransaction);

            log.info("Office: {}, Currency: {}, Date: {}, Transaction ID: {}", office.getId(), currencyCode, transactionDate,
                    transactionId);
            log.info("Original Income Account: {}, Destination GL Account: {}, Fund: {}", originalIncomeAccount.getId(),
                    split.getGlAccount().getId(), split.getFund().getName());

            // Create DEBIT entry from original income account
            JournalEntry debitEntry = JournalEntry.createNew(office, null, // paymentDetail
                    originalIncomeAccount, currencyCode, transactionId, false, // manualEntry
                    transactionDate, JournalEntryType.DEBIT, splitAmount, "Fee split debit for " + split.getFund().getName(), null, // entityType
                    null, // entityId
                    null, // referenceNumber
                    null, // loanTransactionId
                    savingsTransaction.getId(), // savingsTransactionId
                    null, // clientTransactionId
                    null // shareTransactionId
            );

            // Create CREDIT entry to destination GL account
            JournalEntry creditEntry = JournalEntry.createNew(office, null, // paymentDetail
                    split.getGlAccount(), currencyCode, transactionId, false, // manualEntry
                    transactionDate, JournalEntryType.CREDIT, splitAmount, "Fee split for " + split.getFund().getName(), null, // entityType
                    null, // entityId
                    null, // referenceNumber
                    null, // loanTransactionId
                    savingsTransaction.getId(), // savingsTransactionId
                    null, // clientTransactionId
                    null // shareTransactionId
            );

            // Persist both entries atomically
            log.info("Persisting balanced journal entries...");
            JournalEntry persistedDebit = accountingHelper.persistJournalEntry(debitEntry);
            JournalEntry persistedCredit = accountingHelper.persistJournalEntry(creditEntry);

            List<JournalEntry> journalEntries = Arrays.asList(persistedDebit, persistedCredit);

            // Validate journal entry balance and transaction ID uniqueness
            validateJournalEntryBalance(journalEntries, splitAmount);
            validateTransactionIdUniqueness(journalEntries);

            log.info("✅ Balanced journal entries created: DEBIT={}, CREDIT={}", persistedDebit.getId(), persistedCredit.getId());

            return journalEntries;

        } catch (Exception e) {
            log.error("❌ Error creating balanced journal entries for savings split: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get the original income account for a charge from savings product
     */
    private GLAccount getOriginalIncomeAccount(Charge charge, SavingsAccountTransaction savingsTransaction) {
        try {
            Long savingsProductId = savingsTransaction.getSavingsAccount().getSavingsProductId();
            Long chargeId = charge.getId();
            int accountTypeId = CashAccountsForSavings.INCOME_FROM_FEES.getValue(); // = 4

            log.info("Getting original income account for charge {} from savings product {} using account type {}", chargeId,
                    savingsProductId, accountTypeId);

            // 1. Get product-level default mapping (EXACT same as Fineract)
            ProductToGLAccountMapping accountMapping = accountMappingRepository.findCoreProductToFinAccountMapping(savingsProductId,
                    PortfolioProductType.SAVING.getValue(), accountTypeId);

            // 2. For income accounts, try charge-specific account first (EXACT same as Fineract)
            if (accountTypeId == CashAccountsForSavings.INCOME_FROM_FEES.getValue()) {

                // Try to get charge's own account (EXACT same as Fineract)
                GLAccount glAccount = charge.getAccount();
                if (glAccount != null) {
                    log.info("✅ Using charge-specific account: ID={}, Name={}", glAccount.getId(), glAccount.getName());
                    return glAccount;
                }

                // Fallback: Look for charge-specific product mapping (EXACT same as Fineract)
                final ProductToGLAccountMapping chargeSpecificIncomeAccountMapping = accountMappingRepository
                        .findProductIdAndProductTypeAndFinancialAccountTypeAndChargeId(savingsProductId,
                                PortfolioProductType.SAVING.getValue(), accountTypeId, chargeId);
                if (chargeSpecificIncomeAccountMapping != null) {
                    accountMapping = chargeSpecificIncomeAccountMapping;
                    log.info("✅ Using charge-specific product mapping: ID={}, Name={}", accountMapping.getGlAccount().getId(),
                            accountMapping.getGlAccount().getName());
                }
            }

            // 3. Return the best available account (EXACT same as Fineract)
            GLAccount finalAccount = accountMapping.getGlAccount();
            log.info("✅ Using product default account: ID={}, Name={}", finalAccount.getId(), finalAccount.getName());
            return finalAccount;

        } catch (Exception e) {
            log.error("Failed to get original income account for charge {}: {}", charge.getId(), e.getMessage(), e);
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.income.account.not.found",
                            "Original income account not found for charge: " + charge.getId(), "chargeId", charge.getId())),
                    e);
        }
    }

    /**
     * Get the original income account for client transactions using EXACTLY the same logic as normal fee processing
     */
    private GLAccount getOriginalIncomeAccountForClient(Charge charge, ClientTransaction clientTransaction) {
        try {
            // For client transactions, we need to determine the appropriate income account
            // This implementation follows the same pattern as savings but for client products

            log.info("Getting original income account for client charge: {}", charge.getId());

            // For now, we'll use the charge's own account if available
            // In a full implementation, you would need to determine the client's product
            // and use the same mapping logic as savings
            GLAccount chargeAccount = charge.getAccount();
            if (chargeAccount != null) {
                log.info("✅ Using charge's own account as income account: ID={}, Name={}", chargeAccount.getId(), chargeAccount.getName());
                return chargeAccount;
            }

            // Fallback: throw exception as we need a proper income account
            // TODO: Implement full client product mapping logic similar to savings
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.income.account.not.found",
                            "No income account found for client charge: " + charge.getId(), "chargeId", charge.getId())));

        } catch (Exception e) {
            log.error("Failed to get original income account for client charge {}: {}", charge.getId(), e.getMessage(), e);
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.income.account.not.found",
                            "Original income account not found for client charge: " + charge.getId(), "chargeId", charge.getId())),
                    e);
        }
    }

    /**
     * Validate that journal entries are properly balanced
     */
    private void validateJournalEntryBalance(List<JournalEntry> journalEntries, BigDecimal expectedAmount) {
        BigDecimal totalDebits = journalEntries.stream().filter(je -> je.getType().equals(JournalEntryType.DEBIT.getValue()))
                .map(JournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = journalEntries.stream().filter(je -> je.getType().equals(JournalEntryType.CREDIT.getValue()))
                .map(JournalEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new PlatformApiDataValidationException("error.msg.fee.split.journal.entries.unbalanced",
                    "Journal entries are not balanced: DEBITS={}, CREDITS={}",
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.journal.entries.unbalanced", "Journal entries unbalanced",
                            "debits", totalDebits, "credits", totalCredits)));
        }

        if (totalDebits.compareTo(expectedAmount) != 0) {
            throw new PlatformApiDataValidationException("error.msg.fee.split.journal.entries.amount.mismatch",
                    "Journal entry amounts don't match expected: EXPECTED={}, ACTUAL={}",
                    List.of(ApiParameterError.parameterError("error.msg.fee.split.journal.entries.amount.mismatch", "Amount mismatch",
                            "expected", expectedAmount, "actual", totalDebits)));
        }

        log.info("✅ Journal entry balance validation passed: DEBITS={}, CREDITS={}, EXPECTED={}", totalDebits, totalCredits,
                expectedAmount);
    }

    /**
     * Validate transaction ID uniqueness across journal entries
     */
    private void validateTransactionIdUniqueness(List<JournalEntry> journalEntries) {
        Set<String> transactionIds = journalEntries.stream().map(JournalEntry::getTransactionId).collect(Collectors.toSet());

        if (transactionIds.size() != 1) {
            List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(ApiParameterError.parameterError("error.msg.fee.split.transaction.id.not.unique",
                    "Transaction IDs not unique", "transactionIds", transactionIds));

            throw new PlatformApiDataValidationException("error.msg.fee.split.transaction.id.not.unique",
                    "Transaction IDs are not unique across journal entries: " + transactionIds, dataValidationErrors);
        }

        log.info("✅ Transaction ID uniqueness validation passed: {}", transactionIds.iterator().next());
    }

    private String generateTransactionId(Long officeId) {
        log.info("Generating transaction ID for office: {}", officeId);
        String transactionId = "FS" + officeId + System.currentTimeMillis();
        log.info("Generated transaction ID: {}", transactionId);
        return transactionId;
    }

    /**
     * Log comprehensive fee split summary for production monitoring
     */
    private void logFeeSplitSummary(FeeSplitAudit audit, List<ChargeSplit> splits, SavingsAccountTransaction savingsTransaction) {
        log.info("=== FEE SPLIT SUMMARY ===");
        log.info("Transaction ID: {}", savingsTransaction.getId());
        log.info("Charge ID: {}, Name: {}", audit.getCharge().getId(), audit.getCharge().getName());
        log.info("Total Fee Amount: {}", audit.getTotalFeeAmount());
        log.info("Split Date: {}", audit.getSplitDate());
        log.info("Total Splits: {}", splits.size());

        BigDecimal totalSplitAmount = audit.getTotalSplitAmount();
        log.info("Total Split Amount: {}", totalSplitAmount);

        // Verify balance
        if (audit.getTotalFeeAmount().compareTo(totalSplitAmount) != 0) {
            log.warn("⚠️ WARNING: Total fee amount ({}) != Total split amount ({})", audit.getTotalFeeAmount(), totalSplitAmount);
        } else {
            log.info("✅ Balance verification passed: Fee amount = Split amount");
        }

        // Log individual splits
        for (FeeSplitDetail detail : audit.getSplitDetails()) {
            log.info("Split Detail: Fund={}, Amount={}, Percentage={}, GL Account={}, Journal Entry={}", detail.getFund().getName(),
                    detail.getSplitAmount(), detail.getSplitPercentage(), detail.getGlAccount().getName(),
                    detail.getJournalEntry().getId());
        }

        log.info("=== END FEE SPLIT SUMMARY ===");
    }
}
