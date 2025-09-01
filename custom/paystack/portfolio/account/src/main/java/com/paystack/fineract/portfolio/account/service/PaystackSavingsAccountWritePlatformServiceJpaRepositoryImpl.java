/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformServiceUnavailableException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.PortfolioAccountType;
import org.apache.fineract.portfolio.account.domain.StandingInstructionRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.apache.fineract.portfolio.savings.data.SavingsAccountChargeDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDataValidator;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransaction;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.exception.SavingsAccountTransactionNotFoundException;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountInterestPostingService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@Slf4j
public class PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl extends SavingsAccountWritePlatformServiceJpaRepositoryImpl {

    private final AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    private final SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService;
    private final ClientChargeOverrideReadService clientChargeOverrideReadService;
    private final ChargeRepositoryWrapper chargeRepositoryWrapper;
    private final SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepositoryWrapper;
    private final FeeSplitService feeSplitService;

    public PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context,
            SavingsAccountDataValidator fromApiJsonDeserializer, SavingsAccountRepositoryWrapper savingAccountRepositoryWrapper,
            StaffRepositoryWrapper staffRepository, SavingsAccountTransactionRepository savingsAccountTransactionRepository,
            SavingsAccountAssembler savingAccountAssembler, SavingsAccountTransactionDataValidator savingsAccountTransactionDataValidator,
            SavingsAccountChargeDataValidator savingsAccountChargeDataValidator,
            PaymentDetailWritePlatformService paymentDetailWritePlatformService,
            JournalEntryWritePlatformService journalEntryWritePlatformService, SavingsAccountDomainService savingsAccountDomainService,
            NoteRepository noteRepository, AccountTransfersReadPlatformService accountTransfersReadPlatformService,
            AccountAssociationsReadPlatformService accountAssociationsReadPlatformService, ChargeRepositoryWrapper chargeRepository,
            SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepository, HolidayRepositoryWrapper holidayRepository,
            WorkingDaysRepositoryWrapper workingDaysRepository, ConfigurationDomainService configurationDomainService,
            DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, AppUserRepositoryWrapper appuserRepository,
            StandingInstructionRepository standingInstructionRepository, BusinessEventNotifierService businessEventNotifierService,
            GSIMRepositoy gsimRepository, SavingsAccountInterestPostingService savingsAccountInterestPostingService,
            ErrorHandler errorHandler, SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService,
            ClientChargeOverrideReadService clientChargeOverrideReadService, FeeSplitService feeSplitService) {
        super(context, fromApiJsonDeserializer, savingAccountRepositoryWrapper, staffRepository, savingsAccountTransactionRepository,
                savingAccountAssembler, savingsAccountTransactionDataValidator, savingsAccountChargeDataValidator,
                paymentDetailWritePlatformService, journalEntryWritePlatformService, savingsAccountDomainService, noteRepository,
                accountTransfersReadPlatformService, accountAssociationsReadPlatformService, chargeRepository,
                savingsAccountChargeRepository, holidayRepository, workingDaysRepository, configurationDomainService,
                depositAccountOnHoldTransactionRepository, entityDatatableChecksWritePlatformService, appuserRepository,
                standingInstructionRepository, businessEventNotifierService, gsimRepository, savingsAccountInterestPostingService,
                errorHandler);
        this.accountTransfersReadPlatformService = accountTransfersReadPlatformService;
        this.savingsAccountChargePaymentWrapperService = savingsAccountChargePaymentWrapperService;
        this.clientChargeOverrideReadService = clientChargeOverrideReadService;
        this.chargeRepositoryWrapper = chargeRepository;
        this.savingsAccountChargeRepositoryWrapper = savingsAccountChargeRepository;
        this.feeSplitService = feeSplitService;
    }

    @Override
    @Transactional
    protected SavingsAccountTransaction payCharge(final SavingsAccountCharge savingsAccountCharge, final LocalDate transactionDate,
            final BigDecimal amountPaid, final DateTimeFormatter formatter, final boolean backdatedTxnsAllowedTill) {
        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();

        // Get Savings account from savings charge
        final SavingsAccount account = savingsAccountCharge.savingsAccount();
        this.savingAccountAssembler.assignSavingAccountHelpers(account);
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        Pageable sortedByDateAndIdDesc = PageRequest.of(0, 1, Sort.by("dateOf", "id").descending());

        List<SavingsAccountTransaction> savingsAccountTransaction = this.savingsAccountTransactionRepository
                .findBySavingsAccountIdAndLessThanDateOfAndReversedIsFalse(account.getId(), transactionDate, sortedByDateAndIdDesc);

        account.validateAccountBalanceDoesNotViolateOverdraft(savingsAccountTransaction, amountPaid);

        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);

        ChargePaymentResult chargePaymentResult = savingsAccountChargePaymentWrapperService.payChargeWithVat(account, savingsAccountCharge,
                amountPaid, transactionDate, formatter, backdatedTxnsAllowedTill, null);

        boolean isInterestTransfer = false;
        LocalDate postInterestOnDate = null;
        final MathContext mc = MathContext.DECIMAL64;
        boolean postReversals = false;
        if (account.isBeforeLastPostingPeriod(transactionDate, backdatedTxnsAllowedTill)) {
            final LocalDate today = DateUtils.getBusinessLocalDate();
            account.postInterest(mc, today, isInterestTransfer, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth,
                    postInterestOnDate, isInterestTransfer, postReversals);
        } else {
            final LocalDate today = DateUtils.getBusinessLocalDate();
            account.calculateInterestUsing(mc, today, isInterestTransfer, isSavingsInterestPostingAtCurrentPeriodEnd,
                    financialYearBeginningMonth, postInterestOnDate, backdatedTxnsAllowedTill, postReversals);
        }
        List<DepositAccountOnHoldTransaction> depositAccountOnHoldTransactions = null;

        if (account.getOnHoldFunds().compareTo(BigDecimal.ZERO) > 0) {
            depositAccountOnHoldTransactions = this.depositAccountOnHoldTransactionRepository
                    .findBySavingsAccountAndReversedFalseOrderByCreatedDateAsc(account);
        }

        account.validateAccountBalanceDoesNotBecomeNegative(SavingsApiConstants.undoTransactionAction, depositAccountOnHoldTransactions,
                false);

        saveTransactionToGenerateTransactionId(chargePaymentResult.getFeeTransaction());
        if (chargePaymentResult.hasVat()) {
            saveTransactionToGenerateTransactionId(chargePaymentResult.getVatResult().getVatTransaction());
        }

        this.savingAccountRepositoryWrapper.saveAndFlush(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, backdatedTxnsAllowedTill);

        // Process fee split if enabled for this charge
        if (savingsAccountCharge.getCharge().isEnableFeeSplit()) {
            feeSplitService.processFeeSplitForSavings(chargePaymentResult.getFeeTransaction(), amountPaid);
        }

        return chargePaymentResult.getFeeTransaction();
    }

    @Override
    @Transactional
    public CommandProcessingResult undoTransaction(final Long savingsId, final Long transactionId,
            final boolean allowAccountTransferModification) {

        final boolean isSavingsInterestPostingAtCurrentPeriodEnd = this.configurationDomainService
                .isSavingsInterestPostingAtCurrentPeriodEnd();
        final Integer financialYearBeginningMonth = this.configurationDomainService.retrieveFinancialYearBeginningMonth();
        final SavingsAccount account = this.savingAccountAssembler.assembleFrom(savingsId, false);
        final Set<Long> existingTransactionIds = new HashSet<>();
        final Set<Long> existingReversedTransactionIds = new HashSet<>();
        updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);

        final SavingsAccountTransaction savingsAccountTransaction = this.savingsAccountTransactionRepository
                .findOneByIdAndSavingsAccountId(transactionId, savingsId);
        if (savingsAccountTransaction == null) {
            throw new SavingsAccountTransactionNotFoundException(savingsId, transactionId);
        }

        this.savingsAccountTransactionDataValidator.validateTransactionWithPivotDate(savingsAccountTransaction.getTransactionDate(),
                account);

        if (!allowAccountTransferModification
                && this.accountTransfersReadPlatformService.isAccountTransfer(transactionId, PortfolioAccountType.SAVINGS)) {
            throw new PlatformServiceUnavailableException("error.msg.saving.account.transfer.transaction.update.not.allowed",
                    "Savings account transaction:" + transactionId + " update not allowed as it involves in account transfer",
                    transactionId);
        }

        if (!account.allowModify()) {
            throw new PlatformServiceUnavailableException("error.msg.saving.account.transfer.transaction.update.not.allowed",
                    "Savings account transaction:" + transactionId + " update not allowed for this savings type", transactionId);
        }

        final LocalDate today = DateUtils.getBusinessLocalDate();
        final MathContext mc = new MathContext(15, MoneyHelper.getRoundingMode());

        if (account.isNotActive()) {
            throwValidationForActiveStatus(SavingsApiConstants.undoTransactionAction);
        }
        account.undoTransaction(transactionId);

        Long emtLevyPossibleId = transactionId + 1;
        final SavingsAccountTransaction nextSavingsAccountTransaction = this.savingsAccountTransactionRepository
                .findOneByIdAndSavingsAccountId(transactionId + 1, savingsId);

        if (nextSavingsAccountTransaction != null && savingsAccountTransaction.isChargeTransaction()
                && nextSavingsAccountTransaction.isVatonFeesAndNotReversed()) {
            emtLevyPossibleId = emtLevyPossibleId + 1;
            account.undoTransaction(transactionId + 1);
        }

        if (nextSavingsAccountTransaction != null && (nextSavingsAccountTransaction.isWithdrawalFeeAndNotReversed()
                || nextSavingsAccountTransaction.isDepositFeeAndNotReversed())) {
            account.undoTransaction(transactionId + 1);
            emtLevyPossibleId = emtLevyPossibleId + 1;

            final SavingsAccountTransaction vatTransaction = this.savingsAccountTransactionRepository
                    .findOneByIdAndSavingsAccountId(transactionId + 2, savingsId);
            if (vatTransaction != null && vatTransaction.isVatonFeesAndNotReversed()) {
                account.undoTransaction(transactionId + 2);
                emtLevyPossibleId = emtLevyPossibleId + 1;
            }
        }

        final SavingsAccountTransaction possibleEmtLevyTransaction = this.savingsAccountTransactionRepository
                .findOneByIdAndSavingsAccountId(emtLevyPossibleId, savingsId);

        if (possibleEmtLevyTransaction != null && possibleEmtLevyTransaction.isEmtLevyAndNotReversed()
                && !savingsAccountTransaction.isChargeTransaction()) {
            account.undoTransaction(emtLevyPossibleId);
        }

        boolean isInterestTransfer = false;
        LocalDate postInterestOnDate = null;
        boolean postReversals = false;
        checkClientOrGroupActive(account);
        if (savingsAccountTransaction.isPostInterestCalculationRequired()
                && account.isBeforeLastPostingPeriod(savingsAccountTransaction.getTransactionDate(), false)) {
            account.postInterest(mc, today, isInterestTransfer, isSavingsInterestPostingAtCurrentPeriodEnd, financialYearBeginningMonth,
                    postInterestOnDate, false, postReversals);
        } else {
            account.calculateInterestUsing(mc, today, isInterestTransfer, isSavingsInterestPostingAtCurrentPeriodEnd,
                    financialYearBeginningMonth, postInterestOnDate, false, postReversals);
        }
        List<DepositAccountOnHoldTransaction> depositAccountOnHoldTransactions = null;
        if (account.getOnHoldFunds().compareTo(BigDecimal.ZERO) > 0) {
            depositAccountOnHoldTransactions = this.depositAccountOnHoldTransactionRepository
                    .findBySavingsAccountAndReversedFalseOrderByCreatedDateAsc(account);
        }

        account.validateAccountBalanceDoesNotBecomeNegative(SavingsApiConstants.undoTransactionAction, depositAccountOnHoldTransactions,
                false);

        final Set<Long> existingTransactionIdsForPosting = new HashSet<>();
        final Set<Long> existingReversedTransactionIdsForPosting = new HashSet<>();
        updateExistingTransactionsDetails(account, existingTransactionIdsForPosting, existingReversedTransactionIdsForPosting);

        postJournalEntries(account, existingTransactionIdsForPosting, existingReversedTransactionIdsForPosting, false);

        return new CommandProcessingResultBuilder() //
                .withEntityId(savingsId) //
                .withOfficeId(account.officeId()) //
                .withClientId(account.clientId()) //
                .withGroupId(account.groupId()) //
                .withSavingsId(savingsId) //
                .withTransactionId(String.valueOf(transactionId)) //
                .build();
    }

    private void throwValidationForActiveStatus(final String actionName) {
        final String errorMessage = "validation.msg.savingsaccount.transaction." + actionName + ".account.is.not.active";
        throw new GeneralPlatformDomainRuleException(errorMessage, "Transaction " + actionName + " is not allowed. Account is not active.");
    }
}
