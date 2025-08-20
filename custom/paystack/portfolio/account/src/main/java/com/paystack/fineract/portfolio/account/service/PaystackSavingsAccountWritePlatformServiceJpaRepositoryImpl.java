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

import static org.apache.fineract.portfolio.client.api.ClientApiConstants.chargeIdParamName;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import com.paystack.fineract.portfolio.account.data.ChargePaymentResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.domain.StandingInstructionRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
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
public class PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl extends SavingsAccountWritePlatformServiceJpaRepositoryImpl {

    private final SavingsAccountChargePaymentWrapperService chargePaymentWrapperService;
    private final ClientChargeOverrideReadService clientChargeOverrideReadService;
    private final ChargeRepositoryWrapper chargeRepositoryWrapper;
    private final SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepositoryWrapper;

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
            ErrorHandler errorHandler, SavingsAccountChargePaymentWrapperService chargePaymentWrapperService,
            ClientChargeOverrideReadService clientChargeOverrideReadService) {
        super(context, fromApiJsonDeserializer, savingAccountRepositoryWrapper, staffRepository, savingsAccountTransactionRepository,
                savingAccountAssembler, savingsAccountTransactionDataValidator, savingsAccountChargeDataValidator,
                paymentDetailWritePlatformService, journalEntryWritePlatformService, savingsAccountDomainService, noteRepository,
                accountTransfersReadPlatformService, accountAssociationsReadPlatformService, chargeRepository,
                savingsAccountChargeRepository, holidayRepository, workingDaysRepository, configurationDomainService,
                depositAccountOnHoldTransactionRepository, entityDatatableChecksWritePlatformService, appuserRepository,
                standingInstructionRepository, businessEventNotifierService, gsimRepository, savingsAccountInterestPostingService,
                errorHandler);
        this.chargePaymentWrapperService = chargePaymentWrapperService;
        this.clientChargeOverrideReadService = clientChargeOverrideReadService;
        this.chargeRepositoryWrapper = chargeRepository;
        this.savingsAccountChargeRepositoryWrapper = savingsAccountChargeRepository;
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

        ChargePaymentResult chargePaymentResult = chargePaymentWrapperService.payChargeWithVat(account, savingsAccountCharge, amountPaid,
                transactionDate, formatter, backdatedTxnsAllowedTill, null);

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

        account.validateAccountBalanceDoesNotBecomeNegative("." + SavingsAccountTransactionType.PAY_CHARGE.getCode(),
                depositAccountOnHoldTransactions, backdatedTxnsAllowedTill);

        saveTransactionToGenerateTransactionId(chargePaymentResult.getFeeTransaction());
        if (chargePaymentResult.hasVat()) {
            saveTransactionToGenerateTransactionId(chargePaymentResult.getVatResult().getVatTransaction());
        }

        this.savingAccountRepositoryWrapper.saveAndFlush(account);

        postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, backdatedTxnsAllowedTill);

        return chargePaymentResult.getFeeTransaction();
    }

    @Override
    @Transactional
    public CommandProcessingResult addSavingsAccountCharge(final JsonCommand command) {
        // Call parent to reuse validations and creation
        CommandProcessingResult result = super.addSavingsAccountCharge(command);
        Long sacId = result.getResourceId();
        if (sacId != null) {
            // Load the created charge and align primary amount based on override precedence
            SavingsAccountCharge sac = this.savingsAccountChargeRepositoryWrapper.findOneWithNotFoundDetection(sacId);
            Long clientId = sac.savingsAccount().clientId();
            Charge chargeDef = this.chargeRepositoryWrapper
                    .findOneWithNotFoundDetection(command.longValueOfParameterNamed(chargeIdParamName));
            // Resolve effective primary amount using override -> savings amount (if provided) -> product
            BigDecimal savingsAmountFromApi = command.bigDecimalValueOfParameterNamed(SavingsApiConstants.amountParamName);
            BigDecimal effective = clientChargeOverrideReadService.resolvePrimaryAmount(clientId, chargeDef, savingsAmountFromApi);
            // Update primary value (amount for FLAT, percentage for PERCENT_OF_AMOUNT)
            sac.update(effective, sac.getDueDate(), null, null);
            this.savingsAccountChargeRepositoryWrapper.saveAndFlush(sac);
        }
        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult updateSavingsAccountCharge(final JsonCommand command) {
        // Let parent handle standard validations and updates first
        CommandProcessingResult result = super.updateSavingsAccountCharge(command);
        Long sacId = command.getResourceId();
        if (sacId != null) {
            SavingsAccountCharge sac = this.savingsAccountChargeRepositoryWrapper.findOneWithNotFoundDetection(sacId);
            Long clientId = sac.savingsAccount().clientId();
            Charge chargeDef = sac.getCharge();
            BigDecimal savingsAmountFromApi = command.bigDecimalValueOfParameterNamed(SavingsApiConstants.amountParamName);
            BigDecimal effective = clientChargeOverrideReadService.resolvePrimaryAmount(clientId, chargeDef, savingsAmountFromApi);
            sac.update(effective, sac.getDueDate(), null, null);
            this.savingsAccountChargeRepositoryWrapper.saveAndFlush(sac);
        }
        return result;
    }
}
