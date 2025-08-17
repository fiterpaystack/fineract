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

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.savings.SavingsTransactionBooleanValues;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDTO;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransaction;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountDomainServiceJpa;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountSubStatusEnum;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsEvent;
import org.apache.fineract.portfolio.savings.exception.DepositAccountTransactionNotAllowedException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class PaystackServiceAccountDomainServiceJpa extends SavingsAccountDomainServiceJpa {

    private final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    private final SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService;

    public PaystackServiceAccountDomainServiceJpa(SavingsAccountRepositoryWrapper savingsAccountRepository,
            SavingsAccountTransactionRepository savingsAccountTransactionRepository,
            ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper,
            JournalEntryWritePlatformService journalEntryWritePlatformService, ConfigurationDomainService configurationDomainService,
            PlatformSecurityContext context, DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository,
            BusinessEventNotifierService businessEventNotifierService,
            SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper,
            SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService) {
        super(savingsAccountRepository, savingsAccountTransactionRepository, applicationCurrencyRepositoryWrapper,
                journalEntryWritePlatformService, configurationDomainService, context, depositAccountOnHoldTransactionRepository,
                businessEventNotifierService);
        this.savingsAccountTransactionSummaryWrapper = savingsAccountTransactionSummaryWrapper;
        this.savingsAccountChargePaymentWrapperService = savingsAccountChargePaymentWrapperService;
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
            if (charge.isWithdrawalFee() && charge.isActive()) {

                if (charge.getFreeWithdrawalCount() == null) {
                    charge.setFreeWithdrawalCount(0);
                }

                if (charge.isEnablePaymentType() && charge.isEnableFreeWithdrawal()) { // discount transaction to
                    // specific paymentType
                    if (paymentDetail.getPaymentType().getName().equals(charge.getCharge().getPaymentType().getName())) {
                        account.resetFreeChargeDaysCount(charge, transactionAmount, transactionDate, refNo);
                    }
                } else if (charge.isEnablePaymentType()) { // normal charge-transaction to specific paymentType
                    if (paymentDetail.getPaymentType().getName().equals(charge.getCharge().getPaymentType().getName())) {
                        charge.updateWithdralFeeAmount(transactionAmount);
                        savingsAccountChargePaymentWrapperService.payChargeWithVat(account, charge,
                                charge.getAmountOutstanding(account.getCurrency()), transactionDate, refNo, backdatedTxnsAllowedTill);
                    }
                } else if (!charge.isEnablePaymentType() && charge.isEnableFreeWithdrawal()) { // discount transaction
                    // irrespective of
                    // PaymentTypes.
                    account.resetFreeChargeDaysCount(charge, transactionAmount, transactionDate, refNo);

                } else { // normal-withdraw
                    charge.updateWithdralFeeAmount(transactionAmount);
                    savingsAccountChargePaymentWrapperService.payChargeWithVat(account, charge,
                            charge.getAmountOutstanding(account.getCurrency()), transactionDate, refNo, backdatedTxnsAllowedTill);
                }
            }
        }
    }
}
