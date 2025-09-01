package com.paystack.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformServiceUnavailableException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.organisation.monetary.domain.MoneyHelper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.organisation.workingdays.domain.WorkingDaysRepositoryWrapper;
import org.apache.fineract.portfolio.account.domain.StandingInstructionRepository;
import org.apache.fineract.portfolio.account.service.AccountAssociationsReadPlatformService;
import org.apache.fineract.portfolio.account.service.AccountTransfersReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.paymentdetail.service.PaymentDetailWritePlatformService;
import org.apache.fineract.portfolio.savings.data.SavingsAccountChargeDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsAccountTransactionDataValidator;
import org.apache.fineract.portfolio.savings.domain.DepositAccountOnHoldTransactionRepository;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountInterestPostingService;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PaystackSavingsAccountWritePlatformServiceJpaRepositoryImplUndoTransactionTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private SavingsAccountDataValidator fromApiJsonDeserializer;
    @Mock
    private SavingsAccountRepositoryWrapper savingAccountRepositoryWrapper;
    @Mock
    private StaffRepositoryWrapper staffRepository;
    @Mock
    private SavingsAccountTransactionRepository savingsAccountTransactionRepository;
    @Mock
    private SavingsAccountAssembler savingAccountAssembler;
    @Mock
    private SavingsAccountTransactionDataValidator savingsAccountTransactionDataValidator;
    @Mock
    private SavingsAccountChargeDataValidator savingsAccountChargeDataValidator;
    @Mock
    private PaymentDetailWritePlatformService paymentDetailWritePlatformService;
    @Mock
    private JournalEntryWritePlatformService journalEntryWritePlatformService;
    @Mock
    private SavingsAccountDomainService savingsAccountDomainService;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private AccountTransfersReadPlatformService accountTransfersReadPlatformService;
    @Mock
    private AccountAssociationsReadPlatformService accountAssociationsReadPlatformService;
    @Mock
    private ChargeRepositoryWrapper chargeRepository;
    @Mock
    private SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepository;
    @Mock
    private HolidayRepositoryWrapper holidayRepository;
    @Mock
    private WorkingDaysRepositoryWrapper workingDaysRepository;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private DepositAccountOnHoldTransactionRepository depositAccountOnHoldTransactionRepository;
    @Mock
    private EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService;
    @Mock
    private AppUserRepositoryWrapper appUserRepository;
    @Mock
    private StandingInstructionRepository standingInstructionRepository;
    @Mock
    private BusinessEventNotifierService businessEventNotifierService;
    @Mock
    private GSIMRepositoy gsimRepository;
    @Mock
    private SavingsAccountInterestPostingService savingsAccountInterestPostingService;
    @Mock
    private ErrorHandler errorHandler;
    @Mock
    private SavingsAccountChargePaymentWrapperService savingsAccountChargePaymentWrapperService;
    @Mock
    private SavingsVatPostProcessorService vatService;
    @Mock
    private ClientChargeOverrideReadService clientChargeOverrideReadService;

    @Mock
    private SavingsAccount account;

    @Mock
    private FeeSplitService feeSplitService;

    private PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl service;

    private MockedStatic<DateUtils> dateUtilsMock;
    private MockedStatic<MoneyHelper> moneyHelperMock;

    private static final Long SAVINGS_ID = 100L;

    @BeforeEach
    void setup() {
        dateUtilsMock = Mockito.mockStatic(DateUtils.class);
        dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2025, 1, 1));
        moneyHelperMock = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);

        service = Mockito.spy(new PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl(context, fromApiJsonDeserializer,
                savingAccountRepositoryWrapper, staffRepository, savingsAccountTransactionRepository, savingAccountAssembler,
                savingsAccountTransactionDataValidator, savingsAccountChargeDataValidator, paymentDetailWritePlatformService,
                journalEntryWritePlatformService, savingsAccountDomainService, noteRepository, accountTransfersReadPlatformService,
                accountAssociationsReadPlatformService, chargeRepository, savingsAccountChargeRepository, holidayRepository,
                workingDaysRepository, configurationDomainService, depositAccountOnHoldTransactionRepository,
                entityDatatableChecksWritePlatformService, appUserRepository, standingInstructionRepository, businessEventNotifierService,
                gsimRepository, savingsAccountInterestPostingService, errorHandler, savingsAccountChargePaymentWrapperService,
                clientChargeOverrideReadService, feeSplitService));

        when(configurationDomainService.isSavingsInterestPostingAtCurrentPeriodEnd()).thenReturn(false);
        when(configurationDomainService.retrieveFinancialYearBeginningMonth()).thenReturn(1);
        when(savingAccountAssembler.assembleFrom(SAVINGS_ID, false)).thenReturn(account);
        when(account.allowModify()).thenReturn(true);
        when(account.isNotActive()).thenReturn(false);
        when(account.getOnHoldFunds()).thenReturn(BigDecimal.ZERO);
        // prevent journal entry posting logic
        doNothing().when(service).postJournalEntries(any(), anySet(), anySet(), anyBoolean());
    }

    @AfterEach
    void tearDown() {
        dateUtilsMock.close();
        moneyHelperMock.close();
    }

    private SavingsAccountTransaction txn(long id, boolean isCharge, boolean isWithdrawal, boolean withdrawalFee, boolean depositFee,
            boolean vatOnFees, boolean emtLevy) {
        SavingsAccountTransaction t = mock(SavingsAccountTransaction.class);
        when(t.getTransactionDate()).thenReturn(LocalDate.of(2024, 12, 31));
        when(t.isChargeTransaction()).thenReturn(isCharge);
        when(t.isWithdrawal()).thenReturn(isWithdrawal);
        when(t.isWithdrawalFeeAndNotReversed()).thenReturn(withdrawalFee);
        when(t.isDepositFeeAndNotReversed()).thenReturn(depositFee);
        when(t.isVatonFeesAndNotReversed()).thenReturn(vatOnFees);
        when(t.isEmtLevyAndNotReversed()).thenReturn(emtLevy);
        when(t.isPostInterestCalculationRequired()).thenReturn(false);
        return t;
    }

    @Test
    void undoChargeTransactionAlsoUndoesFollowingVatButNotEmtLevy() {
        long transactionId = 10L;
        SavingsAccountTransaction primary = txn(transactionId, true, false, false, false, false, false);
        SavingsAccountTransaction vat = txn(transactionId + 1, false, false, false, false, true, false);
        SavingsAccountTransaction emt = txn(transactionId + 2, false, false, false, false, false, true);

        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(primary);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 1, SAVINGS_ID)).thenReturn(vat);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 2, SAVINGS_ID)).thenReturn(emt);

        CommandProcessingResult result = service.undoTransaction(SAVINGS_ID, transactionId, true);

        // verify only primary and VAT undone, not EMT levy
        verify(account).undoTransaction(transactionId);
        verify(account).undoTransaction(transactionId + 1);
        verify(account, never()).undoTransaction(transactionId + 2);

        assertEquals(SAVINGS_ID, result.getSavingsId());
        assertEquals(String.valueOf(transactionId), result.getTransactionId());
    }

    @Test
    void undoWithdrawalWithFeeVatAndEmtLevy() {
        long transactionId = 20L;
        SavingsAccountTransaction withdrawal = txn(transactionId, false, true, false, false, false, false);
        SavingsAccountTransaction fee = txn(transactionId + 1, false, false, true, false, false, false);
        SavingsAccountTransaction vat = txn(transactionId + 2, false, false, false, false, true, false);
        SavingsAccountTransaction emt = txn(transactionId + 3, false, false, false, false, false, true);

        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(withdrawal);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 1, SAVINGS_ID)).thenReturn(fee);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 2, SAVINGS_ID)).thenReturn(vat);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 3, SAVINGS_ID)).thenReturn(emt);

        CommandProcessingResult result = service.undoTransaction(SAVINGS_ID, transactionId, true);

        InOrder inOrder = inOrder(account);
        inOrder.verify(account).undoTransaction(transactionId);
        inOrder.verify(account).undoTransaction(transactionId + 1);
        inOrder.verify(account).undoTransaction(transactionId + 2);
        inOrder.verify(account).undoTransaction(transactionId + 3);

        assertEquals(SAVINGS_ID, result.getSavingsId());
    }

    @Test
    void undoDepositFeeWithoutVatOrEmtLevy() {
        long transactionId = 30L;
        SavingsAccountTransaction deposit = txn(transactionId, false, false, false, false, false, false);
        SavingsAccountTransaction depositFee = txn(transactionId + 1, false, false, false, true, false, false);

        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(deposit);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId + 1, SAVINGS_ID)).thenReturn(depositFee);

        CommandProcessingResult result = service.undoTransaction(SAVINGS_ID, transactionId, true);

        verify(account).undoTransaction(transactionId);
        verify(account).undoTransaction(transactionId + 1);
        verify(account, times(2)).undoTransaction(anyLong());
        assertEquals(SAVINGS_ID, result.getSavingsId());
    }

    @Test
    void undoTransactionThrowsWhenTransferAndModificationNotAllowed() {
        long transactionId = 40L;
        SavingsAccountTransaction txn = txn(transactionId, false, false, false, false, false, false);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(txn);
        when(accountTransfersReadPlatformService.isAccountTransfer(transactionId,
                org.apache.fineract.portfolio.account.PortfolioAccountType.SAVINGS)).thenReturn(true);

        assertThrows(PlatformServiceUnavailableException.class, () -> service.undoTransaction(SAVINGS_ID, transactionId, false));
        verify(account, never()).undoTransaction(anyLong());
    }

    @Test
    void undoTransactionThrowsWhenNotAllowedToModify() {
        long transactionId = 50L;
        SavingsAccountTransaction txn = txn(transactionId, false, false, false, false, false, false);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(txn);
        when(accountTransfersReadPlatformService.isAccountTransfer(transactionId,
                org.apache.fineract.portfolio.account.PortfolioAccountType.SAVINGS)).thenReturn(false);
        when(account.allowModify()).thenReturn(false);

        PlatformServiceUnavailableException ex = assertThrows(PlatformServiceUnavailableException.class,
                () -> service.undoTransaction(SAVINGS_ID, transactionId, true));
        assertTrue(ex.getMessage().contains("update not allowed"));
        verify(account, never()).undoTransaction(anyLong());
    }

    @Test
    void undoTransactionThrowsWhenTransactionNotFound() {
        long transactionId = 60L;
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(null);
        Exception ex = assertThrows(org.apache.fineract.portfolio.savings.exception.SavingsAccountTransactionNotFoundException.class,
                () -> service.undoTransaction(SAVINGS_ID, transactionId, true));
        assertTrue(ex.getMessage().contains(String.valueOf(transactionId)));
    }

    @Test
    void undoTransactionTriggersPostInterestWhenRequiredAndBeforeLastPostingPeriod() {
        long transactionId = 70L;
        SavingsAccountTransaction primary = txn(transactionId, false, false, false, false, false, false);
        when(primary.isPostInterestCalculationRequired()).thenReturn(true);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(primary);
        when(account.isBeforeLastPostingPeriod(primary.getTransactionDate(), false)).thenReturn(true);
        // stub void method postInterest
        Mockito.doNothing().when(account).postInterest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(),
                anyBoolean());
        // no need to stub calculateInterestUsing; verify not called

        service.undoTransaction(SAVINGS_ID, transactionId, true);

        verify(account).postInterest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(), anyBoolean());
        verify(account, never()).calculateInterestUsing(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(),
                anyBoolean());
    }

    @Test
    void undoTransactionTriggersCalculateInterestWhenRequiredAndNotBeforeLastPostingPeriod() {
        long transactionId = 80L;
        SavingsAccountTransaction primary = txn(transactionId, false, false, false, false, false, false);
        when(primary.isPostInterestCalculationRequired()).thenReturn(true);
        when(savingsAccountTransactionRepository.findOneByIdAndSavingsAccountId(transactionId, SAVINGS_ID)).thenReturn(primary);
        when(account.isBeforeLastPostingPeriod(primary.getTransactionDate(), false)).thenReturn(false);
        when(account.calculateInterestUsing(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(), anyBoolean()))
                .thenReturn(java.util.Collections.emptyList());

        service.undoTransaction(SAVINGS_ID, transactionId, true);

        verify(account, never()).postInterest(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(), anyBoolean());
        verify(account).calculateInterestUsing(any(), any(), anyBoolean(), anyBoolean(), anyInt(), any(), anyBoolean(), anyBoolean());
    }
}
