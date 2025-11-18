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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import com.paystack.fineract.portfolio.account.data.SavingsAccountTransactionLimitValidator;
import com.paystack.fineract.portfolio.discount.service.DiscountApplicationService;
import com.paystack.fineract.portfolio.discount.service.ProductDiscountService;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributes;
import com.paystack.fineract.portfolio.savings.domain.PaystackSavingsProductAttributesRepository;
import com.paystack.fineract.portfolio.savings.service.WithdrawalFrequencyService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.SavingsTransactionBooleanValues;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaystackSavingsAccountDomainServiceJpaEmtLevyExemptionTest {

    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepositoryWrapper;
    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private PlatformSecurityContext context;
    @Mock
    private DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository;
    @Mock
    private BusinessEventNotifierService businessEventNotifierService;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    @Mock
    private SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService;
    @Mock
    private ClientChargeOverrideReadService clientChargeOverrideReadService;
    @Mock
    private FeeSplitService feeSplitService;
    @Mock
    private SavingsAccountTransactionLimitValidator savingsAccountTransactionLimitValidator;
    @Mock
    private PaystackSavingsProductAttributesRepository savingsProductAttributesRepository;
    @Mock
    private ProductDiscountService productDiscountService;
    @Mock
    private DiscountApplicationService discountApplicationService;
    @Mock
    private WithdrawalFrequencyService withdrawalFrequencyService;

    @Mock
    private SavingsAccount account;
    @Mock
    private SavingsProduct product;
    @Mock
    private PaystackSavingsProductAttributes productAttributes;

    private PaystackSavingsAccountDomainServiceJpa service;
    private MockedStatic<DateUtils> dateUtilsMock;
    private MockedStatic<MoneyHelper> moneyHelperMock;

    private static final Long CLIENT_ID_1 = 100L;
    private static final Long CLIENT_ID_2 = 200L;
    private static final BigDecimal TRANSACTION_AMOUNT = new BigDecimal("15000.00");
    private static final BigDecimal EMT_LEVY_AMOUNT = new BigDecimal("50.00");
    private static final BigDecimal EMT_LEVY_THRESHOLD = new BigDecimal("10000.00");
    private static final LocalDate TRANSACTION_DATE = LocalDate.of(2024, 1, 15);

    @BeforeEach
    void setup() {
        dateUtilsMock = Mockito.mockStatic(DateUtils.class);
        dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2024, 1, 15));
        moneyHelperMock = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);
        moneyHelperMock.when(MoneyHelper::getMathContext).thenReturn(java.math.MathContext.DECIMAL64);

        service = Mockito.spy(new PaystackSavingsAccountDomainServiceJpa(savingsAccountRepository, savingsAccountTransactionRepository,
                applicationCurrencyRepositoryWrapper, journalEntryWritePlatformService, configurationDomainService, context,
                depositAccountOnHoldTransactionRepository, businessEventNotifierService, noteRepository,
                savingsAccountTransactionLimitValidator, savingsAccountTransactionSummaryWrapper, savingsAccountChargePaymentWrapperService,
                clientChargeOverrideReadService, savingsProductAttributesRepository, feeSplitService, productDiscountService,
                discountApplicationService, withdrawalFrequencyService));

        // Setup common mocks
        when(account.clientId()).thenReturn(CLIENT_ID_1);
        when(account.savingsProduct()).thenReturn(product);
        org.apache.fineract.organisation.monetary.domain.MonetaryCurrency currency = mock(
                org.apache.fineract.organisation.monetary.domain.MonetaryCurrency.class);
        when(currency.getCode()).thenReturn("NGN");
        when(account.getCurrency()).thenReturn(currency);

        // Mock CurrencyData for Money.of() calls
        org.apache.fineract.organisation.monetary.data.CurrencyData currencyData = mock(
                org.apache.fineract.organisation.monetary.data.CurrencyData.class);
        when(currencyData.getCode()).thenReturn("NGN");
        when(currencyData.getDecimalPlaces()).thenReturn(2);
        when(currencyData.getInMultiplesOf()).thenReturn(1);
        when(currency.toData()).thenReturn(currencyData);
        when(account.office()).thenReturn(mock(org.apache.fineract.organisation.office.domain.Office.class));
        when(account.allowWithdrawal()).thenReturn(true);
        when(account.allowDeposit()).thenReturn(true);
        when(account.isTransactionsAllowed()).thenReturn(true);
        when(account.getOnHoldFunds()).thenReturn(BigDecimal.ZERO);
        when(account.charges()).thenReturn(new HashSet<SavingsAccountCharge>());
        when(account.isBeforeLastPostingPeriod(any(), anyBoolean())).thenReturn(false);
        when(account.getActivationDate()).thenReturn(LocalDate.of(2024, 1, 1));
        when(account.isAccountLocked(any())).thenReturn(false);
        doNothing().when(account).validateForAccountBlock();
        doNothing().when(account).validateForDebitBlock();
        doNothing().when(account).validateForCreditBlock();
        doNothing().when(account).validatePivotDateTransaction(any(), anyBoolean(), anyLong(), any());
        doNothing().when(account).validateActivityNotBeforeClientOrGroupTransferDate(any(), any());
        doNothing().when(account).validateAccountBalanceDoesNotBecomeNegative(any(), anyBoolean(), any(), anyBoolean());
        doNothing().when(account).addTransaction(any());
        when(account.calculateInterestUsing(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(new ArrayList<>());
        // Mock accounting bridge data for journal entries
        when(account.deriveAccountingBridgeData(any(), any(), any(), anyBoolean(), anyBoolean())).thenReturn(null);

        // Mock deposit account type
        org.apache.fineract.portfolio.savings.DepositAccountType depositAccountType = mock(
                org.apache.fineract.portfolio.savings.DepositAccountType.class);
        when(account.depositAccountType()).thenReturn(depositAccountType);
        when(depositAccountType.resourceName()).thenReturn("savingsaccount");

        // Mock account transactions list
        when(account.getTransactions()).thenReturn(new ArrayList<>());
        when(account.getSavingsAccountTransactionsWithPivotConfig()).thenReturn(new ArrayList<>());
        when(account.findExistingReversedTransactionIds()).thenReturn(new HashSet<>());

        // Mock summary wrapper for account
        org.apache.fineract.portfolio.savings.domain.SavingsAccountSummary summary = mock(
                org.apache.fineract.portfolio.savings.domain.SavingsAccountSummary.class);
        when(account.getSummary()).thenReturn(summary);

        // Create Money objects before stubbing to avoid unfinished stubbing issues
        Money transactionAmountMoney = Money.of(currency, TRANSACTION_AMOUNT);

        // Mock account.deposit() and account.withdraw() methods that are called by parent
        SavingsAccountTransaction mockWithdrawalTransaction = mock(SavingsAccountTransaction.class);
        when(mockWithdrawalTransaction.getId()).thenReturn(1L);
        when(mockWithdrawalTransaction.getRefNo()).thenReturn("withdrawal-ref-no");
        when(mockWithdrawalTransaction.getTransactionDate()).thenReturn(TRANSACTION_DATE);
        when(mockWithdrawalTransaction.getAmount(any())).thenReturn(transactionAmountMoney);
        when(mockWithdrawalTransaction.isEmtLevyAndNotReversed()).thenReturn(false);

        SavingsAccountTransaction mockDepositTransaction = mock(SavingsAccountTransaction.class);
        when(mockDepositTransaction.getId()).thenReturn(2L);
        when(mockDepositTransaction.getRefNo()).thenReturn("deposit-ref-no");
        when(mockDepositTransaction.getTransactionDate()).thenReturn(TRANSACTION_DATE);
        when(mockDepositTransaction.getAmount(any())).thenReturn(transactionAmountMoney);
        when(mockDepositTransaction.isEmtLevyAndNotReversed()).thenReturn(false);

        // withdraw(transactionDTO, applyWithdrawFee, backdatedTxnsAllowedTill, relaxingDaysConfigForPivotDate, refNo)
        when(account.withdraw(any(), anyBoolean(), anyBoolean(), anyLong(), any())).thenReturn(mockWithdrawalTransaction);
        // deposit(transactionDTO, savingsAccountTransactionType, backdatedTxnsAllowedTill,
        // relaxingDaysConfigForPivotDate, refNo)
        when(account.deposit(any(), any(), anyBoolean(), anyLong(), any())).thenReturn(mockDepositTransaction);

        when(product.getId()).thenReturn(1L);
        when(savingsProductAttributesRepository.findBySavingsProductId(1L)).thenReturn(Optional.of(productAttributes));
        when(productAttributes.getIsEmtLevyApplicableForDeposit()).thenReturn(true);
        when(productAttributes.getIsEmtLevyApplicableForWithdraw()).thenReturn(true);
        when(productAttributes.getOverrideGlobalEmtLevy()).thenReturn(true);
        when(productAttributes.getEmtLevyAmount()).thenReturn(EMT_LEVY_AMOUNT);
        when(productAttributes.getEmtLevyThreshold()).thenReturn(EMT_LEVY_THRESHOLD);

        when(withdrawalFrequencyService.isWithdrawalAllowed(any(), any())).thenReturn(true);
        when(configurationDomainService.isSavingsInterestPostingAtCurrentPeriodEnd()).thenReturn(false);
        when(configurationDomainService.retrieveFinancialYearBeginningMonth()).thenReturn(1);
        when(configurationDomainService.isReversalTransactionAllowed()).thenReturn(false);

        // Mock journal entries posting
        doNothing().when(service).postJournalEntries(any(), anySet(), anySet(), anyBoolean());

        // Mock savings account repository save
        when(savingsAccountRepository.save(any())).thenReturn(account);
        when(savingsAccountRepository.saveAndFlush(any())).thenReturn(account);

        // Mock transaction repository save (for saveTransactionToGenerateTransactionId)
        when(savingsAccountTransactionRepository.save(any())).then(returnsFirstArg());

        // Mock transaction limit validator (called in handleDeposit)
        doNothing().when(savingsAccountTransactionLimitValidator).isDepositTransactionExceedsLimits(any());
    }

    @AfterEach
    void tearDown() {
        // Always clear ThreadLocal context after each test
        PaystackSavingsAccountDomainServiceJpa.clearTransferContext();
        dateUtilsMock.close();
        moneyHelperMock.close();
    }

    @Test
    void shouldSkipEmtLevyForIntraClientTransferWithdrawal() {
        // Given: Intra-client transfer (same client ID)
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_1);

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, // isAccountTransfer
                true, // isRegularTransaction
                false, // isApplyWithdrawFee
                false, // isInterestTransfer
                false // isWithdrawBalance
        );

        // When: Processing withdrawal
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, transactionBooleanValues, false, "Test note");

        // Then: EMT Levy transaction should NOT be created
        verify(account, never()).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }

    @Test
    void shouldSkipEmtLevyForIntraClientTransferDeposit() {
        // Given: Intra-client transfer (same client ID)
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_1);

        // When: Processing deposit as part of account transfer
        SavingsAccountTransaction deposit = service.handleDeposit(account, java.time.format.DateTimeFormatter.ISO_DATE, TRANSACTION_DATE,
                TRANSACTION_AMOUNT, null, true, // isAccountTransfer
                true, // isRegularTransaction
                false, "Test note");

        // Then: EMT Levy transaction should NOT be created
        verify(account, never()).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(deposit);
    }

    @Test
    void shouldApplyEmtLevyForInterClientTransferWithdrawal() {
        // Given: Inter-client transfer (different client IDs)
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_2);

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, // isAccountTransfer
                true, // isRegularTransaction
                false, // isApplyWithdrawFee
                false, // isInterestTransfer
                false // isWithdrawBalance
        );

        // When: Processing withdrawal
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, transactionBooleanValues, false, "Test note");

        // Then: EMT Levy transaction SHOULD be created
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }

    @Test
    void shouldApplyEmtLevyForInterClientTransferDeposit() {
        // Given: Inter-client transfer (different client IDs)
        // For deposit, the receiving account belongs to CLIENT_ID_2
        when(account.clientId()).thenReturn(CLIENT_ID_2);
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_2);

        // When: Processing deposit as part of account transfer
        SavingsAccountTransaction deposit = service.handleDeposit(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, true, // isAccountTransfer
                true, // isRegularTransaction
                false, "Test note");

        // Then: EMT Levy transaction SHOULD be created
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(deposit);
    }

    @Test
    void shouldApplyEmtLevyForNonTransferWithdrawal() {
        // Given: Regular withdrawal (not an account transfer)
        // No transfer context set

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(false, // isAccountTransfer
                true, // isRegularTransaction
                false, // isApplyWithdrawFee
                false, // isInterestTransfer
                false // isWithdrawBalance
        );

        // When: Processing withdrawal
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, transactionBooleanValues, false, "Test note");

        // Then: EMT Levy transaction SHOULD be created (normal behavior)
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }

    @Test
    void shouldApplyEmtLevyForNonTransferDeposit() {
        // Given: Regular deposit (not an account transfer)
        // No transfer context set

        // When: Processing deposit
        SavingsAccountTransaction deposit = service.handleDeposit(account, java.time.format.DateTimeFormatter.ISO_DATE, TRANSACTION_DATE,
                TRANSACTION_AMOUNT, null, false, // isAccountTransfer
                true, // isRegularTransaction
                false, "Test note");

        // Then: EMT Levy transaction SHOULD be created (normal behavior)
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(deposit);
    }

    @Test
    void shouldSkipEmtLevyWhenAmountBelowThreshold() {
        // Given: Transaction amount below threshold
        BigDecimal smallAmount = new BigDecimal("5000.00");
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_2);

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, // isAccountTransfer
                true, // isRegularTransaction
                false, // isApplyWithdrawFee
                false, // isInterestTransfer
                false // isWithdrawBalance
        );

        // When: Processing withdrawal with amount below threshold
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, smallAmount, null, transactionBooleanValues, false, "Test note");

        // Then: EMT Levy transaction should NOT be created (below threshold)
        verify(account, never()).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }

    @Test
    void shouldClearTransferContextAfterProcessing() {
        // Given: Transfer context is set
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_1);

        // When: Processing and then checking context
        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, true, false, false, false);
        service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE, TRANSACTION_DATE, TRANSACTION_AMOUNT, null,
                transactionBooleanValues, false, "Test note");

        // Clear context manually (simulating cleanup)
        PaystackSavingsAccountDomainServiceJpa.clearTransferContext();

        // Then: Context should be cleared
        // Verify by processing another transaction - should apply levy normally
        SavingsTransactionBooleanValues regularTransaction = new SavingsTransactionBooleanValues(false, true, false, false, false);
        service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE, TRANSACTION_DATE, TRANSACTION_AMOUNT, null,
                regularTransaction, false, "Test note");

        // Should apply EMT Levy (normal behavior)
        verify(account, Mockito.atLeastOnce()).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));
    }

    @Test
    void shouldHandleNullClientIdGracefully() {
        // Given: Account with null client ID
        when(account.clientId()).thenReturn(null);
        PaystackSavingsAccountDomainServiceJpa.setTransferContext(CLIENT_ID_1, CLIENT_ID_1);

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, true, false, false, false);

        // When: Processing withdrawal
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, transactionBooleanValues, false, "Test note");

        // Then: Should apply EMT Levy normally (null client ID means can't determine intra-client)
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }

    @Test
    void shouldHandleNullDestinationClientIdGracefully() {
        // Given: No transfer context set (null destination client ID)
        // No context set

        SavingsTransactionBooleanValues transactionBooleanValues = new SavingsTransactionBooleanValues(true, true, false, false, false);

        // When: Processing withdrawal
        SavingsAccountTransaction withdrawal = service.handleWithdrawal(account, java.time.format.DateTimeFormatter.ISO_DATE,
                TRANSACTION_DATE, TRANSACTION_AMOUNT, null, transactionBooleanValues, false, "Test note");

        // Then: Should apply EMT Levy normally (no context means can't determine intra-client)
        verify(account).addTransaction(argThat(txn -> {
            if (txn instanceof SavingsAccountTransaction) {
                return ((SavingsAccountTransaction) txn).isEmtLevyAndNotReversed();
            }
            return false;
        }));

        assertNotNull(withdrawal);
    }
}
