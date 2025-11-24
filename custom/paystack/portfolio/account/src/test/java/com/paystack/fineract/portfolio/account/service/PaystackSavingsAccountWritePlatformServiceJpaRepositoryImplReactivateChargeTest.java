package com.paystack.fineract.portfolio.account.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.client.charge.service.ClientChargeOverrideReadService;
import com.paystack.fineract.portfolio.savings.service.WithdrawalFrequencyService;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import org.apache.fineract.accounting.journalentry.service.JournalEntryWritePlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
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
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountInterestPostingService;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
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
class PaystackSavingsAccountWritePlatformServiceJpaRepositoryImplReactivateChargeTest {

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
    private ClientChargeOverrideReadService clientChargeOverrideReadService;
    @Mock
    private FeeSplitService feeSplitService;
    @Mock
    private WithdrawalFrequencyService withdrawalFrequencyService;

    private PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl service;
    private MockedStatic<DateUtils> dateUtilsMock;
    private MockedStatic<MoneyHelper> moneyHelperMock;

    @BeforeEach
    void setUp() {
        dateUtilsMock = Mockito.mockStatic(DateUtils.class);
        dateUtilsMock.when(DateUtils::getBusinessLocalDate).thenReturn(LocalDate.of(2025, 1, 15));
        moneyHelperMock = Mockito.mockStatic(MoneyHelper.class);
        moneyHelperMock.when(MoneyHelper::getRoundingMode).thenReturn(RoundingMode.HALF_EVEN);

        service = new PaystackSavingsAccountWritePlatformServiceJpaRepositoryImpl(context, fromApiJsonDeserializer,
                savingAccountRepositoryWrapper, staffRepository, savingsAccountTransactionRepository, savingAccountAssembler,
                savingsAccountTransactionDataValidator, savingsAccountChargeDataValidator, paymentDetailWritePlatformService,
                journalEntryWritePlatformService, savingsAccountDomainService, noteRepository, accountTransfersReadPlatformService,
                accountAssociationsReadPlatformService, chargeRepository, savingsAccountChargeRepository, holidayRepository,
                workingDaysRepository, configurationDomainService, depositAccountOnHoldTransactionRepository,
                entityDatatableChecksWritePlatformService, appUserRepository, standingInstructionRepository, businessEventNotifierService,
                gsimRepository, savingsAccountInterestPostingService, errorHandler, savingsAccountChargePaymentWrapperService,
                clientChargeOverrideReadService, feeSplitService, withdrawalFrequencyService);
    }

    @AfterEach
    void tearDown() {
        dateUtilsMock.close();
        moneyHelperMock.close();
    }

    @Test
    void reactivateRecurringChargeAdvancesWhenResumeFlagSet() {
        SavingsAccountCharge charge = mock(SavingsAccountCharge.class);
        SavingsAccount account = mock(SavingsAccount.class);
        JsonCommand command = mock(JsonCommand.class);

        when(savingsAccountChargeRepository.findOneWithNotFoundDetection(10L, 5L)).thenReturn(charge);
        when(charge.savingsAccount()).thenReturn(account);
        doNothing().when(savingAccountAssembler).assignSavingAccountHelpers(account);
        when(charge.isActive()).thenReturn(false);
        when(charge.isOnSpecifiedDueDate()).thenReturn(false);
        when(charge.isRecurringFee()).thenReturn(true);
        when(charge.getDueDate()).thenReturn(LocalDate.of(2025, 2, 1));
        when(charge.getNextDueDateFrom(any(LocalDate.class))).thenReturn(LocalDate.of(2025, 2, 1), LocalDate.of(2025, 3, 1));
        when(account.getTransactions()).thenReturn(Collections.emptyList());
        when(command.localDateValueOfParameterNamed("reactivationDate")).thenReturn(null);
        when(command.localDateValueOfParameterNamed("dueDate")).thenReturn(null);
        when(command.booleanPrimitiveValueOfParameterNamed("resumeFromNextBillingCycle")).thenReturn(true);

        service.reactivateSavingsAccountCharge(5L, 10L, command);

        verify(charge).reactivateCharge();
        verify(charge).resetPropertiesForRecurringFees();
        verify(charge, times(1)).update(null, LocalDate.of(2025, 3, 1), null, null);
    }

    @Test
    void reactivateSpecifiedDueDateRequiresNewDate() {
        SavingsAccountCharge charge = mock(SavingsAccountCharge.class);
        SavingsAccount account = mock(SavingsAccount.class);
        JsonCommand command = mock(JsonCommand.class);

        when(savingsAccountChargeRepository.findOneWithNotFoundDetection(7L, 3L)).thenReturn(charge);
        when(charge.savingsAccount()).thenReturn(account);
        doNothing().when(savingAccountAssembler).assignSavingAccountHelpers(account);
        when(charge.isActive()).thenReturn(false);
        when(charge.isOnSpecifiedDueDate()).thenReturn(true);
        when(charge.isRecurringFee()).thenReturn(false);
        when(command.localDateValueOfParameterNamed("reactivationDate")).thenReturn(null);
        when(command.localDateValueOfParameterNamed("dueDate")).thenReturn(null);
        when(account.getTransactions()).thenReturn(Collections.emptyList());

        assertThrows(PlatformApiDataValidationException.class, () -> service.reactivateSavingsAccountCharge(3L, 7L, command));
    }
}
