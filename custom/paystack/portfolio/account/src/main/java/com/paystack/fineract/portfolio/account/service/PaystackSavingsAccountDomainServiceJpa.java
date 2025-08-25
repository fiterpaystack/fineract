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

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import com.paystack.fineract.portfolio.account.data.ChargePaymentResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.business.domain.savings.transaction.SavingsWithdrawalBusinessEvent;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.savings.SavingsTransactionBooleanValues;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDTO;
import org.apache.fineract.portfolio.savings.domain.*;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.exception.DepositAccountTransactionNotAllowedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class PaystackSavingsAccountDomainServiceJpa extends SavingsAccountDomainServiceJpa {

    private static final Logger log = LoggerFactory.getLogger(PaystackSavingsAccountDomainServiceJpa.class);

    private final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    private final SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService;
    private final ClientChargeOverrideReadService clientChargeOverrideReadService;
    private final SavingsAccountAssembler savingAccountAssembler;
    private final PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    private final SavingsVatPostProcessorService vatService;

    public PaystackSavingsAccountDomainServiceJpa(SavingsAccountRepositoryWrapper savingsAccountRepository,
            SavingsAccountTransactionRepository savingsAccountTransactionRepository,
            ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper,
            JournalEntryWritePlatformService journalEntryWritePlatformService, ConfigurationDomainService configurationDomainService,
            PlatformSecurityContext context, DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository,
            BusinessEventNotifierService businessEventNotifierService,
            SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper,
            SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService,
            ClientChargeOverrideReadService clientChargeOverrideReadService, SavingsAccountAssembler savingAccountAssembler,
            PaymentDetailWritePlatformService paymentDetailWritePlatformService, SavingsVatPostProcessorService vatService) {
        super(savingsAccountRepository, savingsAccountTransactionRepository, applicationCurrencyRepositoryWrapper,
                journalEntryWritePlatformService, configurationDomainService, context, depositAccountOnHoldTransactionRepository,
                businessEventNotifierService);
        this.savingsAccountTransactionSummaryWrapper = savingsAccountTransactionSummaryWrapper;
        this.savingsAccountChargePaymentWrapperService = savingsAccountChargePaymentWrapperService;
        this.clientChargeOverrideReadService = clientChargeOverrideReadService;
        this.savingAccountAssembler = savingAccountAssembler;
        this.paymentDetailWritePlatformService = paymentDetailWritePlatformService;
        this.vatService = vatService;
    }

    @Transactional
    @Override
    public SavingsAccountTransaction handleWithdrawal(final SavingsAccount account, final DateTimeFormatter fmt,
            final LocalDate transactionDate, final BigDecimal transactionAmount, final PaymentDetail paymentDetail,
            final SavingsTransactionBooleanValues transactionBooleanValues, final boolean backdatedTxnsAllowedTill) {
        context.authenticatedUser();
        account.validateForAccountBlock();
        account.validateForDebitBlock();
        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Long relaxingDaysConfigForPivotDate = this.configurationDomainService.retrieveRelaxingDaysConfigForPivotDate();
        final boolean postReversals = this.configurationDomainService.isReversalTransactionAllowed();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();
        if (transactionBooleanValues.isRegularTransaction() && !account.allowWithdrawal()) {
            throw new DepositAccountTransactionNotAllowedException(account.getId(), "withdraw", account.depositAccountType());
        }
        final Set<Long> existingTransactionIds = new HashSet<>();
        final LocalDate postInterestOnDate = null;
        final Set<Long> existingReversedTransactionIds = new HashSet<>();

        if (backdatedTxnsAllowedTill) {
            updateTransactionDetailsWithPivotConfig(account, existingTransactionIds, existingReversedTransactionIds);
        } else {
            updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);
        }

        Integer accountType = null;
        final SavingsAccountTransactionDTO transactionDTO = new SavingsAccountTransactionDTO(fmt, transactionDate, transactionAmount,
                paymentDetail, null, accountType);
        UUID refNo = UUID.randomUUID();
        final SavingsAccountTransaction withdrawal = withdraw(transactionDTO, transactionBooleanValues.isApplyWithdrawFee(),
                backdatedTxnsAllowedTill, relaxingDaysConfigForPivotDate, refNo.toString(), account);
        final MathContext mc = MathContext.DECIMAL64;

        final LocalDate today = DateUtils.getBusinessLocalDate();

        if (account.isBeforeLastPostingPeriod(transactionDate, backdatedTxnsAllowedTill)) {
            account.postInterest(mc, today, transactionBooleanValues.isInterestTransfer(), isSavingsInterestPostingAtCurrentPeriodEnd,
                    financialYearBeginningMonth, postInterestOnDate, backdatedTxnsAllowedTill, postReversals);
        } else {
            account.calculateInterestUsing(mc, today, transactionBooleanValues.isInterestTransfer(),
                    isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth, postInterestOnDate, backdatedTxnsAllowedTill,
                    postReversals);
        }

        List<DepositAccountOnHoldTransaction> depositAccountOnHoldTransactions = null;
        if (account.getOnHoldFunds().compareTo(BigDecimal.ZERO) > 0) {
            depositAccountOnHoldTransactions = this.depositAccountOnHoldTransactionRepository
                    .findBySavingsAccountAndReversedFalseOrderByCreatedDateAsc(account);
        }

        account.validateAccountBalanceDoesNotBecomeNegative(transactionAmount, transactionBooleanValues.isExceptionForBalanceCheck(),
                depositAccountOnHoldTransactions, backdatedTxnsAllowedTill);

        saveTransactionToGenerateTransactionId(withdrawal);
        if (backdatedTxnsAllowedTill) {
            // Update transactions separately
            saveUpdatedTransactionsOfSavingsAccount(account.getSavingsAccountTransactionsWithPivotConfig());
        }
        this.savingsAccountRepository.save(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, transactionBooleanValues.isAccountTransfer(),
                backdatedTxnsAllowedTill);

        businessEventNotifierService.notifyPostBusinessEvent(new SavingsWithdrawalBusinessEvent(withdrawal));
        return withdrawal;
    }

    public SavingsAccountTransaction withdraw(final SavingsAccountTransactionDTO transactionDTO, final boolean applyWithdrawFee,
            final boolean backdatedTxnsAllowedTill, final Long relaxingDaysConfigForPivotDate, String refNo, SavingsAccount account) {
        if (!account.isTransactionsAllowed()) {

            final String defaultUserMessage = "Transaction is not allowed. Account is not active.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.account.is.not.active",
                    defaultUserMessage, "transactionDate", transactionDTO.getTransactionDate().format(transactionDTO.getFormatter()));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (DateUtils.isDateInTheFuture(transactionDTO.getTransactionDate())) {
            final String defaultUserMessage = "Transaction date cannot be in the future.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.in.the.future",
                    defaultUserMessage, "transactionDate", transactionDTO.getTransactionDate().format(transactionDTO.getFormatter()));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (DateUtils.isBefore(transactionDTO.getTransactionDate(), account.getActivationDate())) {
            final Object[] defaultUserArgs = Arrays.asList(transactionDTO.getTransactionDate().format(transactionDTO.getFormatter()),
                    account.getActivationDate().format(transactionDTO.getFormatter())).toArray();
            final String defaultUserMessage = "Transaction date cannot be before accounts activation date.";
            final ApiParameterError error = ApiParameterError.parameterError("error.msg.savingsaccount.transaction.before.activation.date",
                    defaultUserMessage, "transactionDate", defaultUserArgs);

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (account.isAccountLocked(transactionDTO.getTransactionDate())) {
            final String defaultUserMessage = "Withdrawal is not allowed. No withdrawals are allowed until after "
                    + account.getLockedInUntilDate().format(transactionDTO.getFormatter());
            final ApiParameterError error = ApiParameterError.parameterError(
                    "error.msg.savingsaccount.transaction.withdrawals.blocked.during.lockin.period", defaultUserMessage, "transactionDate",
                    transactionDTO.getTransactionDate().format(transactionDTO.getFormatter()),
                    account.getLockedInUntilDate().format(transactionDTO.getFormatter()));

            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            dataValidationErrors.add(error);

            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        account.validatePivotDateTransaction(transactionDTO.getTransactionDate(), backdatedTxnsAllowedTill, relaxingDaysConfigForPivotDate,
                "savingsaccount");
        account.validateActivityNotBeforeClientOrGroupTransferDate(SavingsEvent.SAVINGS_WITHDRAWAL, transactionDTO.getTransactionDate());

        if (applyWithdrawFee) {
            // auto pay withdrawal fee
            payWithdrawalFee(transactionDTO.getTransactionAmount(), transactionDTO.getTransactionDate(), transactionDTO.getPaymentDetail(),
                    backdatedTxnsAllowedTill, refNo, account);
        }

        final Money transactionAmountMoney = Money.of(account.getCurrency(), transactionDTO.getTransactionAmount());
        final SavingsAccountTransaction transaction = SavingsAccountTransaction.withdrawal(account, account.office(),
                transactionDTO.getPaymentDetail(), transactionDTO.getTransactionDate(), transactionAmountMoney, refNo);

        if (backdatedTxnsAllowedTill) {
            account.addTransactionToExisting(transaction);
        } else {
            account.addTransaction(transaction);
        }

        if (account.getSubStatus().equals(SavingsAccountSubStatusEnum.INACTIVE.getValue())
                || account.getSubStatus().equals(SavingsAccountSubStatusEnum.DORMANT.getValue())) {
            account.setSubStatusNone();
        }
        if (backdatedTxnsAllowedTill) {
            account.getSummary().updateSummaryWithPivotConfig(account.getCurrency(), savingsAccountTransactionSummaryWrapper, transaction,
                    account.getSavingsAccountTransactionsWithPivotConfig());
        }
        return transaction;
    }

    private void payWithdrawalFee(final BigDecimal transactionAmount, final LocalDate transactionDate, final PaymentDetail paymentDetail,
            final boolean backdatedTxnsAllowedTill, final String refNo, final SavingsAccount account) {
        for (SavingsAccountCharge charge : account.charges()) {
            if (!charge.isWithdrawalFee() || !charge.isActive()) {
                continue;
            }

            if (charge.getFreeWithdrawalCount() == null) {
                charge.setFreeWithdrawalCount(0);
            }

            // Respect free-withdrawal/paymentType rules
            if (charge.isEnablePaymentType() && charge.isEnableFreeWithdrawal()) {
                if (paymentDetail.getPaymentType().getName().equals(charge.getCharge().getPaymentType().getName())) {
                    account.resetFreeChargeDaysCount(charge, transactionAmount, transactionDate, refNo);
                }
                continue;
            }
            if (charge.isEnablePaymentType()) {
                if (!paymentDetail.getPaymentType().getName().equals(charge.getCharge().getPaymentType().getName())) {
                    continue;
                }
            } else if (!charge.isEnablePaymentType() && charge.isEnableFreeWithdrawal()) {
                account.resetFreeChargeDaysCount(charge, transactionAmount, transactionDate, refNo);
                continue;
            }

            // Determine calculation type from the underlying Charge
            Integer calc = charge.getCharge().getChargeCalculation();
            BigDecimal amountToPay = BigDecimal.ZERO;

            if (calc != null && ChargeCalculationType.fromInt(calc).isPercentageOfAmount()) {
                // 1) Set the percentage from client override (client -> savings -> product)
                BigDecimal pctResolved = clientChargeOverrideReadService.resolvePrimaryAmount(account.clientId(), charge.getCharge(), null);
                charge.update(pctResolved, charge.getDueDate(), null, null);

                // 2) Compute outstanding using the just-updated percentage
                charge.updateWithdralFeeAmount(transactionAmount);
                BigDecimal computed = charge.getAmountOutstanding(account.getCurrency()).getAmount();

                // 3) Apply caps from client override (fallback to product)
                BigDecimal minCap = clientChargeOverrideReadService.resolveMinCap(account.clientId(), charge.getCharge());
                BigDecimal maxCap = clientChargeOverrideReadService.resolveMaxCap(account.clientId(), charge.getCharge());
                BigDecimal desired = computed;
                if (minCap != null && desired.compareTo(minCap) < 0) {
                    desired = minCap;
                }
                if (maxCap != null && desired.compareTo(maxCap) > 0) {
                    desired = maxCap;
                }

                // 4) If caps changed the value, adjust percentage so amountOutstanding matches desired
                if (transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0 && desired.compareTo(computed) != 0) {
                    BigDecimal pctNeeded = desired.multiply(BigDecimal.valueOf(100L)).divide(transactionAmount,
                            org.apache.fineract.organisation.monetary.domain.MoneyHelper.getRoundingMode());
                    charge.update(pctNeeded, charge.getDueDate(), null, null);
                    charge.updateWithdralFeeAmount(transactionAmount);
                }

                amountToPay = charge.getAmountOutstanding(account.getCurrency()).getAmount();
            } else {
                // FLAT: resolve primary amount and set it before computing outstanding
                BigDecimal flatResolved = clientChargeOverrideReadService.resolvePrimaryAmount(account.clientId(), charge.getCharge(),
                        charge.amount());
                charge.update(flatResolved, charge.getDueDate(), null, null);
                charge.updateWithdralFeeAmount(transactionAmount);
                amountToPay = charge.getAmountOutstanding(account.getCurrency()).getAmount();
            }

            if (amountToPay.compareTo(BigDecimal.ZERO) <= 0) {
                continue; // nothing to pay
            }

            Money moneyToPay = org.apache.fineract.organisation.monetary.domain.Money.of(account.getCurrency(), amountToPay);
            savingsAccountChargePaymentWrapperService.payChargeWithVat(account, charge, moneyToPay, transactionDate, refNo,
                    backdatedTxnsAllowedTill);
        }
    }

    /**
     * Enhanced handleDeposit method that applies deposit fees for all credit/inbound transactions
     */
    @Transactional
    @Override
    public SavingsAccountTransaction handleDeposit(final SavingsAccount account, final DateTimeFormatter fmt,
            final LocalDate transactionDate, final BigDecimal transactionAmount, final PaymentDetail paymentDetail,
            final boolean isAccountTransfer, final boolean isRegularTransaction, final boolean backdatedTxnsAllowedTill) {

        // Call parent's handleDeposit method to process the actual deposit
        SavingsAccountTransaction deposit = super.handleDeposit(account, fmt, transactionDate, transactionAmount, paymentDetail,
                isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);

        // Apply deposit fees and VAT after the deposit is processed
        if (transactionAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Store the deposit transaction ID
            Long depositTransactionId = deposit.getId();

            payDepositFee(transactionAmount, transactionDate, paymentDetail, backdatedTxnsAllowedTill, deposit.getRefNo(), account);

            // Recalculate summary after fees and VAT are applied
            final MathContext mc = MathContext.DECIMAL64;
            final LocalDate today = DateUtils.getBusinessLocalDate();
            final boolean postReversals = this.configurationDomainService.isReversalTransactionAllowed();
            final boolean isInterestTransfer = false;
            final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                    .isSavingsInterestPostingAtCurrentPeriodEnd();
            final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();
            final LocalDate postInterestOnDate = null;

            // Trigger recalculation by calling calculateInterestUsing which internally calls recalculateDailyBalances
            account.calculateInterestUsing(mc, today, isInterestTransfer, isSavingsInterestPostingAtCurrentPeriodEnd,
                    financialYearBeginningMonth, postInterestOnDate, backdatedTxnsAllowedTill, postReversals);

            // Save updated transactions if using pivot config
            if (backdatedTxnsAllowedTill) {
                saveUpdatedTransactionsOfSavingsAccount(account.getSavingsAccountTransactionsWithPivotConfig());
            }

            // Save the updated account to persist the recalculated summary and transaction balances
            this.savingsAccountRepository.saveAndFlush(account);

            // Post journal entries after fees and VAT are applied to include them in GL
            final Set<Long> existingTransactionIds = new HashSet<>();
            final Set<Long> existingReversedTransactionIds = new HashSet<>();

            List<SavingsAccountTransaction> allTransactions = backdatedTxnsAllowedTill
                    ? account.getSavingsAccountTransactionsWithPivotConfig()
                    : account.getTransactions();
            Set<Long> feeAndVatTransactionIds = new HashSet<>();
            for (SavingsAccountTransaction transaction : allTransactions) {
                if (transaction.getId() > depositTransactionId
                        && (transaction.isChargeTransaction() || transaction.getTransactionType().isVatOnFees())) {
                    feeAndVatTransactionIds.add(transaction.getId());
                }
            }

            for (SavingsAccountTransaction transaction : allTransactions) {
                // Skip the fee and VAT transactions that were just created for this deposit
                if (feeAndVatTransactionIds.contains(transaction.getId())) {
                    // DO NOT add these to existingTransactionIds - they will be included in GL posting
                    log.debug("DEBUG: Fee/VAT transaction will be included in GL posting: " + transaction.getId() + ", Type: "
                            + transaction.getTransactionType() + ", Amount: " + transaction.getAmount());
                } else {
                    // Add all other transactions to existingTransactionIds to exclude them from GL posting
                    existingTransactionIds.add(transaction.getId());
                }
            }

            existingReversedTransactionIds.addAll(account.findExistingReversedTransactionIds());

            this.savingsAccountRepository.saveAndFlush(account);

            postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, isAccountTransfer,
                    backdatedTxnsAllowedTill);
        }

        return deposit;
    }

    private void payDepositFee(final BigDecimal transactionAmount, final LocalDate transactionDate, final PaymentDetail paymentDetail,
            final boolean backdatedTxnsAllowedTill, final String refNo, final SavingsAccount account) {
        for (SavingsAccountCharge charge : account.charges()) {
            if (!charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DEPOSIT_FEE.getValue()) || !charge.isActive()) {
                continue;
            }

            // Respect payment type rules if enabled
            if (charge.isEnablePaymentType()) {
                if (!paymentDetail.getPaymentType().getName().equals(charge.getCharge().getPaymentType().getName())) {
                    continue;
                }
            }

            // Determine calculation type from the underlying Charge
            Integer calc = charge.getCharge().getChargeCalculation();
            BigDecimal amountToPay = BigDecimal.ZERO;

            if (calc != null && ChargeCalculationType.fromInt(calc).isPercentageOfAmount()) {
                // 1) Set the percentage from client override (client -> savings -> product)
                BigDecimal pctResolved = clientChargeOverrideReadService.resolvePrimaryAmount(account.clientId(), charge.getCharge(), null);
                charge.update(pctResolved, charge.getDueDate(), null, null);

                // 2) Compute outstanding using the just-updated percentage
                charge.updateDepositFeeAmount(transactionAmount);
                BigDecimal computed = charge.getAmountOutstanding(account.getCurrency()).getAmount();

                // 3) Apply caps from client override (fallback to product)
                BigDecimal minCap = clientChargeOverrideReadService.resolveMinCap(account.clientId(), charge.getCharge());
                BigDecimal maxCap = clientChargeOverrideReadService.resolveMaxCap(account.clientId(), charge.getCharge());
                BigDecimal desired = computed;
                if (minCap != null && desired.compareTo(minCap) < 0) {
                    desired = minCap;
                }
                if (maxCap != null && desired.compareTo(maxCap) > 0) {
                    desired = maxCap;
                }

                // 4) If caps changed the value, adjust percentage so amountOutstanding matches desired
                if (transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0 && desired.compareTo(computed) != 0) {
                    BigDecimal pctNeeded = desired.multiply(BigDecimal.valueOf(100L)).divide(transactionAmount,
                            org.apache.fineract.organisation.monetary.domain.MoneyHelper.getRoundingMode());
                    charge.update(pctNeeded, charge.getDueDate(), null, null);
                    charge.updateDepositFeeAmount(transactionAmount);
                }

                amountToPay = charge.getAmountOutstanding(account.getCurrency()).getAmount();
            } else {
                // FLAT: resolve primary amount and set it before computing outstanding
                BigDecimal flatResolved = clientChargeOverrideReadService.resolvePrimaryAmount(account.clientId(), charge.getCharge(),
                        charge.amount());
                charge.update(flatResolved, charge.getDueDate(), null, null);
                charge.updateDepositFeeAmount(transactionAmount);
                amountToPay = charge.getAmountOutstanding(account.getCurrency()).getAmount();
            }

            if (amountToPay.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Money moneyToPay = org.apache.fineract.organisation.monetary.domain.Money.of(account.getCurrency(), amountToPay);
            payChargeWithVatAndSave(account, charge, moneyToPay, transactionDate, refNo, backdatedTxnsAllowedTill);
        }
    }

    private void payChargeWithVatAndSave(SavingsAccount account, SavingsAccountCharge charge, Money amount, LocalDate transactionDate,
            String refNo, boolean backdatedTxnsAllowedTill) {
        ChargePaymentResult result = savingsAccountChargePaymentWrapperService.payChargeWithVat(account, charge, amount, transactionDate,
                refNo, backdatedTxnsAllowedTill);

        if (result.getFeeTransaction() != null) {
            saveTransactionToGenerateTransactionId(result.getFeeTransaction());
        }
        if (result.getVatResult() != null && result.getVatResult().isVatApplied() && result.getVatResult().getVatTransaction() != null) {
            saveTransactionToGenerateTransactionId(result.getVatResult().getVatTransaction());
        }

        this.savingsAccountRepository.saveAndFlush(account);
    }
}
