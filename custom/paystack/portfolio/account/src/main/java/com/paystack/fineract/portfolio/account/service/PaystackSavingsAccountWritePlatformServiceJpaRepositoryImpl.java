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

import com.paystack.fineract.portfolio.account.data.ChargePaymentResult;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
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
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.note.domain.Note;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.data.SavingsAccountChargeDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDTO;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.portfolio.savings.domain.GroupSavingsIndividualMonitoring;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
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
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargePaidBy;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountInterestPostingService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.organisation.monetary.domain.Money;
import com.paystack.fineract.portfolio.account.service.SavingsVatPostProcessorService;
import com.paystack.fineract.portfolio.account.data.VatApplicationResult;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@Primary
public class PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl extends SavingsAccountWritePlatformServiceJpaRepositoryImpl {

    private static final Logger log = LoggerFactory.getLogger(PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl.class);

    private final SavingsAccountChargePaymentWrapperService chargePaymentWrapperService;
    private final SavingsVatPostProcessorService vatService;

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
            SavingsVatPostProcessorService vatService) {
        super(context, fromApiJsonDeserializer, savingAccountRepositoryWrapper, staffRepository, savingsAccountTransactionRepository,
                savingAccountAssembler, savingsAccountTransactionDataValidator, savingsAccountChargeDataValidator,
                paymentDetailWritePlatformService, journalEntryWritePlatformService, savingsAccountDomainService, noteRepository,
                accountTransfersReadPlatformService, accountAssociationsReadPlatformService, chargeRepository,
                savingsAccountChargeRepository, holidayRepository, workingDaysRepository, configurationDomainService,
                depositAccountOnHoldTransactionRepository, entityDatatableChecksWritePlatformService, appuserRepository,
                standingInstructionRepository, businessEventNotifierService, gsimRepository, savingsAccountInterestPostingService,
                errorHandler);
        this.chargePaymentWrapperService = chargePaymentWrapperService;
        this.vatService = vatService;
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

    /**
     * Enhanced deposit method that applies deposit fees for all credit/inbound transactions
     * and provides detailed transaction receipt with fee breakdown
     */
    @Override
    @Transactional
    public CommandProcessingResult deposit(final Long savingsId, final JsonCommand command) {
        // Get the account and calculate fees BEFORE processing the deposit
        final boolean backdatedTxnsAllowedTill = this.savingAccountAssembler.getPivotConfigStatus();
        final SavingsAccount account = this.savingAccountAssembler.assembleFrom(savingsId, backdatedTxnsAllowedTill);
        
        final LocalDate transactionDate = command.localDateValueOfParameterNamed("transactionDate");
        final BigDecimal grossAmount = command.bigDecimalValueOfParameterNamed("transactionAmount");
        final Locale locale = command.extractLocale();
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern(command.dateFormat()).withLocale(locale);
        
        // Find all DEPOSIT_FEE charges on this account
        Set<SavingsAccountCharge> depositFeeCharges = new HashSet<>();
        System.out.println("Checking for DEPOSIT_FEE charges on account: " + savingsId);
        System.out.println("Total charges on account: " + account.charges().size());
        
                for (SavingsAccountCharge charge : account.charges()) {
            System.out.println("Charge: " + charge.getCharge().getName() + 
                     ", TimeType: " + charge.getCharge().getChargeTimeType() + 
                     ", Active: " + charge.isActive() + 
                     ", Paid: " + charge.isPaid());
            
            if (charge.getCharge().getChargeTimeType().equals(ChargeTimeType.DEPOSIT_FEE.getValue()) &&
                charge.isActive()) {
                // For DEPOSIT_FEE charges, we don't check if they're paid because they should be applied to every deposit
                depositFeeCharges.add(charge);
                System.out.println("Found DEPOSIT_FEE charge: " + charge.getCharge().getName());
            }
        }
        
        System.out.println("Total DEPOSIT_FEE charges found: " + depositFeeCharges.size());
        
        // For DEPOSIT_FEE charges, the user wants to see the GROSS amount credited to their account
        // and then the fee debited separately. So if they deposit 5000, they should see:
        // - Deposit: 5000 credit (gross amount)
        // - Pay Charge: 1000 debit (fee amount)
        // - Net effect: balance increases by 4000 (5000 - 1000)
        
        BigDecimal totalFeeAmount = BigDecimal.ZERO;
        
        // Calculate fees based on the gross amount
        for (SavingsAccountCharge charge : depositFeeCharges) {
            BigDecimal feeAmount = calculateDepositFeeAmount(charge, account, grossAmount);
            System.out.println("Calculated fee amount for charge " + charge.getCharge().getName() + ": " + feeAmount);
            totalFeeAmount = totalFeeAmount.add(feeAmount);
        }
        
        // Use gross amount for deposit (what user wants to see credited)
        BigDecimal depositAmount = grossAmount;
        
        System.out.println("Final calculation - Gross amount: " + grossAmount + ", Deposit amount: " + depositAmount + ", Total fee amount: " + totalFeeAmount);
        
        // Call the parent method with the GROSS amount (what user wants to see credited)
        System.out.println("Calling parent deposit method with GROSS amount: " + depositAmount);
        
        // Instead of modifying the command, let's call the parent's handleDeposit method directly
        // This bypasses the validation issues with JsonCommand modification
        System.out.println("Account balance before deposit: " + account.getAccountBalance());
        CommandProcessingResult result = processDepositWithNetAmount(savingsId, command, depositAmount, transactionDate, fmt, backdatedTxnsAllowedTill);
        System.out.println("Parent deposit result: " + result.getResourceId());
        System.out.println("Account balance after deposit: " + account.getAccountBalance());
        
                    // If deposit was successful and fees exist, apply them as separate transactions
            if (result.getResourceId() != null && totalFeeAmount.compareTo(BigDecimal.ZERO) > 0) {
                System.out.println("Deposit successful, applying fees as separate transactions...");
                try {
                    // Get existing transaction IDs BEFORE creating fee transactions
                    Set<Long> existingTransactionIds = new HashSet<>();
                    Set<Long> existingReversedTransactionIds = new HashSet<>();
                    updateExistingTransactionsDetails(account, existingTransactionIds, existingReversedTransactionIds);
                    
                    // Now create the fee transactions
                    applyDepositFees(account, depositFeeCharges, transactionDate, grossAmount, totalFeeAmount, fmt, backdatedTxnsAllowedTill);
                    
                    // Post journal entries for the fee transactions
                    postJournalEntries(account, existingTransactionIds, existingReversedTransactionIds, backdatedTxnsAllowedTill);
                    
                    System.out.println("Account balance after fee application: " + account.getAccountBalance());
                    
                    // Update the result with fee information
                    Map<String, Object> changes = new LinkedHashMap<>();
                    changes.put("feeAmount", totalFeeAmount);
                    changes.put("grossAmount", grossAmount);
                    changes.put("depositAmount", depositAmount);
                    
                    return new CommandProcessingResultBuilder() //
                            .withEntityId(result.getResourceId()) //
                            .withOfficeId(result.getOfficeId()) //
                            .withClientId(result.getClientId()) //
                            .withGroupId(result.getGroupId()) //
                            .withSavingsId(savingsId) //
                            .with(changes) //
                            .build();
                } catch (Exception e) {
                    System.err.println("Error applying deposit fees: " + e.getMessage());
                    e.printStackTrace();
                    // Don't fail the deposit if fee application fails
                }
            }
            
            System.out.println("Returning result");
            return result;
    }

    /**
     * Apply deposit fees to the account
     */
        private void applyDepositFees(final SavingsAccount account, final Set<SavingsAccountCharge> depositFeeCharges,
                                  final LocalDate transactionDate, final BigDecimal grossAmount,
                                  final BigDecimal totalFeeAmount, final DateTimeFormatter formatter, final boolean backdatedTxnsAllowedTill) {
        
        System.out.println("Applying deposit fees - Account balance before fees: " + account.getAccountBalance());
        System.out.println("Total fee amount to apply: " + totalFeeAmount);
        
        for (SavingsAccountCharge charge : depositFeeCharges) {
            BigDecimal feeAmount = calculateDepositFeeAmount(charge, account, grossAmount);
            System.out.println("Processing fee for charge: " + charge.getCharge().getName() + " - Amount: " + feeAmount);
            
            if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
                // Create a direct fee transaction instead of using charge payment infrastructure
                // This bypasses the "already paid/waived" validation for DEPOSIT_FEE charges
                SavingsAccountTransaction feeTransaction = createDirectFeeTransaction(
                    account, charge, feeAmount, transactionDate, formatter);
                
                System.out.println("Created fee transaction ID: " + feeTransaction.getId());
                System.out.println("Fee transaction amount: " + feeTransaction.getAmount());
                
                // Save the fee transaction
                saveTransactionToGenerateTransactionId(feeTransaction);
                
                // Apply VAT if configured (using the VAT service directly)
                applyVatForFeeTransaction(account, charge, feeAmount, transactionDate, feeTransaction);
            } else {
                System.out.println("Skipping fee application - calculated amount is zero or negative");
            }
        }
        
                             // Save the account to persist fee transactions and update balances
                     this.savingAccountRepositoryWrapper.saveAndFlush(account);
                     
                     // The saveAndFlush should trigger proper balance updates
        System.out.println("Account balance after fees: " + account.getAccountBalance());
    }
    
    /**
     * Create a direct fee transaction without using the charge payment infrastructure
     */
    private SavingsAccountTransaction createDirectFeeTransaction(final SavingsAccount account, 
                                                               final SavingsAccountCharge charge,
                                                               final BigDecimal feeAmount,
                                                               final LocalDate transactionDate,
                                                               final DateTimeFormatter formatter) {
        
        System.out.println("Creating direct fee transaction - Amount: " + feeAmount + ", Charge: " + charge.getCharge().getName());
        
        // Create a fee transaction directly on the account
        final boolean isReversed = false;
        final boolean isManualTransaction = false;
        final Boolean lienTransaction = false;
        final String refNo = "DEPOSIT_FEE_" + charge.getCharge().getName();
        
        Money feeMoney = Money.of(account.getCurrency(), feeAmount);
        System.out.println("Fee money: " + feeMoney);
        
        // Create the transaction using the account's addTransaction method
        SavingsAccountTransaction feeTransaction = new SavingsAccountTransaction(
            account, account.office(), 
            SavingsAccountTransactionType.PAY_CHARGE.getValue(), 
            transactionDate, feeMoney, isReversed, isManualTransaction, lienTransaction, refNo);
        
        System.out.println("Created fee transaction - Type: " + feeTransaction.getTransactionType() + 
                 ", Date: " + feeTransaction.getTransactionDate() + ", Amount: " + feeTransaction.getAmount());
        
        // Link this transaction to the specific charge so accounting can post GL (expects exactly one charge payment)
        final SavingsAccountChargePaidBy chargePaidBy = SavingsAccountChargePaidBy.instance(feeTransaction, charge, feeAmount);
        feeTransaction.getSavingsAccountChargesPaid().add(chargePaidBy);
        System.out.println("Linked charge payment - Charge: " + charge.getCharge().getName() + ", Amount: " + feeAmount);

        // Add the transaction to the account
        account.addTransaction(feeTransaction);
        System.out.println("Added fee transaction to account");
        
        // Update the charge to reflect the payment (for DEPOSIT_FEE charges, we don't mark them as fully paid)
        charge.pay(account.getCurrency(), Money.of(account.getCurrency(), feeAmount));
        
        return feeTransaction;
    }
    
    /**
     * Apply VAT for fee transaction using the VAT service directly
     */
    private void applyVatForFeeTransaction(final SavingsAccount account,
                                          final SavingsAccountCharge charge,
                                          final BigDecimal feeAmount,
                                          final LocalDate transactionDate,
                                          final SavingsAccountTransaction feeTransaction) {
        
        try {
            // Use the VAT service to process VAT if applicable
            VatApplicationResult vatResult = vatService.processVatForFeeTransaction(
                feeAmount, transactionDate, charge, account, false);
            
                            if (vatResult.isVatApplied()) {
                    // Save the VAT transaction
                    saveTransactionToGenerateTransactionId(vatResult.getVatTransaction());
                    System.out.println("VAT applied: " + vatResult.getVatAmount());
                }
        } catch (Exception e) {
            System.err.println("Error applying VAT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Process deposit with net amount by calling the parent's handleDeposit method directly
     */
    private CommandProcessingResult processDepositWithNetAmount(final Long savingsId, final JsonCommand command, 
                                                              final BigDecimal netAmount, final LocalDate transactionDate,
                                                              final DateTimeFormatter fmt, final boolean backdatedTxnsAllowedTill) {
        
        // Get the account
        final SavingsAccount account = this.savingAccountAssembler.assembleFrom(savingsId, backdatedTxnsAllowedTill);
        
        // Validate the account - this method is private in parent, so we'll skip it for now
         checkClientOrGroupActive(account);
        
        // Create payment detail
        final Map<String, Object> changes = new LinkedHashMap<>();
        final PaymentDetail paymentDetail = this.paymentDetailWritePlatformService.createAndPersistPaymentDetail(command, changes);
        
        // Process the deposit with net amount
        boolean isAccountTransfer = false;
        boolean isRegularTransaction = true;
        
        final SavingsAccountTransaction deposit = this.savingsAccountDomainService.handleDeposit(account, fmt, transactionDate,
                netAmount, paymentDetail, isAccountTransfer, isRegularTransaction, backdatedTxnsAllowedTill);
        
        // Handle GSIM if applicable - gsimRepository is private in parent, so we'll skip this for now
         if (account.getGsim() != null && (deposit.getId() != null)) {
             GroupSavingsIndividualMonitoring gsim = gsimRepository.findById(account.getGsim().getId()).orElseThrow();
             BigDecimal currentBalance = gsim.getParentDeposit();
             BigDecimal newBalance = currentBalance.add(netAmount); // Use net amount for GSIM tracking
             gsim.setParentDeposit(newBalance);
             gsimRepository.save(gsim);
         }
        
        // Handle note if provided - noteRepository is private in parent, so we'll skip this for now
         final String noteText = command.stringValueOfParameterNamed("note");
         if (noteText != null && !noteText.trim().isEmpty()) {
             final Note note = Note.savingsTransactionNote(account, deposit, noteText);
             // this.noteRepository.save(note);
         }
        
        return new CommandProcessingResultBuilder() //
                .withEntityId(deposit.getId()) //
                .withOfficeId(account.officeId()) //
                .withClientId(account.clientId()) //
                .withGroupId(account.groupId()) //
                .withSavingsId(savingsId) //
                .with(changes) //
                .build();
    }
    
    /**
     * Calculate the deposit fee amount based on charge calculation type
     */
    private BigDecimal calculateDepositFeeAmount(final SavingsAccountCharge charge, final SavingsAccount account, final BigDecimal depositAmount) {
        BigDecimal feeAmount = BigDecimal.ZERO;
        
        // Get charge calculation type from the underlying charge definition
        Integer chargeCalculationType = charge.getCharge().getChargeCalculation();
        
        System.out.println("Calculating fee for charge: " + charge.getCharge().getName() + " (ID: " + charge.getId() + ")");
        System.out.println("Charge calculation type: " + chargeCalculationType);
        System.out.println("Deposit amount: " + depositAmount);
        
        if (chargeCalculationType != null) {
            switch (chargeCalculationType) {
                case 1: // FLAT
                    feeAmount = charge.getAmount(account.getCurrency()).getAmount();
                    System.out.println("FLAT charge - Fee amount: " + feeAmount);
                    break;
                case 2: // PERCENT_OF_AMOUNT
                    // For percentage charges, use the underlying charge definition's amount as the percentage
                    if (charge.getCharge().getAmount() != null && depositAmount != null) {
                        BigDecimal percentage = charge.getCharge().getAmount();
                        System.out.println("PERCENT_OF_AMOUNT charge - Percentage: " + percentage + "%");
                        
                        feeAmount = depositAmount.multiply(percentage)
                                               .divide(BigDecimal.valueOf(100), MathContext.DECIMAL64);
                        
                        System.out.println("Calculated fee before caps: " + feeAmount);
                        
                        // Apply min/max caps if configured
                        if (charge.getCharge().getMinCap() != null && feeAmount.compareTo(charge.getCharge().getMinCap()) < 0) {
                            feeAmount = charge.getCharge().getMinCap();
                            System.out.println("Applied min cap: " + feeAmount);
                        }
                        if (charge.getCharge().getMaxCap() != null && feeAmount.compareTo(charge.getCharge().getMaxCap()) > 0) {
                            feeAmount = charge.getCharge().getMaxCap();
                            System.out.println("Applied max cap: " + feeAmount);
                        }
                    }
                    break;
                default:
                    // Default to flat amount
                    feeAmount = charge.getAmount(account.getCurrency()).getAmount();
                    System.out.println("DEFAULT charge - Fee amount: " + feeAmount);
                    break;
            }
        }
        
        System.out.println("Final calculated fee amount: " + feeAmount);
        return feeAmount;
    }
}
