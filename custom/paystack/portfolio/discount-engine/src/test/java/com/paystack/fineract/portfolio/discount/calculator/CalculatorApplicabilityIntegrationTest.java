package com.paystack.fineract.portfolio.discount.calculator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.calculator.impl.AccountBalanceBasedDiscountCalculator;
import com.paystack.fineract.portfolio.discount.calculator.impl.SavingsAccountTimeBasedDiscountCalculator;
import com.paystack.fineract.portfolio.discount.calculator.impl.SavingsAccountTransactionCountDiscountCalculator;
import com.paystack.fineract.portfolio.discount.calculator.impl.SavingsAccountTransactionFlowDiscountCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.holiday.domain.HolidayRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Integration tests to verify that isApplicable() methods correctly validate business conditions. This ensures AND
 * logic works correctly when allRulesRequired = true.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Calculator Applicability Integration Tests")
class CalculatorApplicabilityIntegrationTest {

    @Mock
    private PaystackSavingsAccountTransactionRepository transactionRepository;

    @Mock
    private HolidayRepositoryWrapper holidayRepositoryWrapper;

    private AccountBalanceBasedDiscountCalculator balanceCalculator;
    private SavingsAccountTransactionCountDiscountCalculator countCalculator;
    private SavingsAccountTransactionFlowDiscountCalculator flowCalculator;
    private SavingsAccountTimeBasedDiscountCalculator timeCalculator;

    @BeforeEach
    void setUp() {
        // Initialize ThreadLocalContextUtil with tenant and business date for DateUtils.getBusinessLocalDate()
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.now())));

        balanceCalculator = new AccountBalanceBasedDiscountCalculator(transactionRepository);
        countCalculator = new SavingsAccountTransactionCountDiscountCalculator(transactionRepository);
        flowCalculator = new SavingsAccountTransactionFlowDiscountCalculator(transactionRepository);
        timeCalculator = new SavingsAccountTimeBasedDiscountCalculator(holidayRepositoryWrapper);
    }

    @AfterEach
    void tearDown() {
        // Clean up ThreadLocalContextUtil to avoid test interference
        ThreadLocalContextUtil.reset();
    }

    @Nested
    @DisplayName("AccountBalanceBasedDiscountCalculator Applicability Tests")
    class BalanceCalculatorTests {

        @Test
        @DisplayName("Should be applicable when balance threshold is met")
        void shouldBeApplicableWhenBalanceThresholdMet() {
            // Given: Calculator configured with minimum balance of 500k
            Map<String, Object> params = new HashMap<>();
            params.put("minimumAverageBalance", "500000");
            params.put("discountPercentage", "5");
            balanceCalculator.configure(params);

            // Mock: Account has average balance of 600k (above threshold)
            List<SavingsAccountTransaction> mockTransactions = createMockTransactions(600000.0);
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenReturn(mockTransactions);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(balanceCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when balance threshold is NOT met")
        void shouldNotBeApplicableWhenBalanceThresholdNotMet() {
            // Given: Calculator configured with minimum balance of 500k
            Map<String, Object> params = new HashMap<>();
            params.put("minimumAverageBalance", "500000");
            params.put("discountPercentage", "5");
            balanceCalculator.configure(params);

            // Mock: Account has average balance of 300k (below threshold)
            List<SavingsAccountTransaction> mockTransactions = createMockTransactions(300000.0);
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenReturn(mockTransactions);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(balanceCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should NOT be applicable when balance calculation fails")
        void shouldNotBeApplicableWhenBalanceCalculationFails() {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("minimumAverageBalance", "500000");
            params.put("discountPercentage", "5");
            balanceCalculator.configure(params);

            // Mock: Repository throws exception
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenThrow(new RuntimeException("Database error"));

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(balanceCalculator.isApplicable(context)).isFalse();
        }
    }

    @Nested
    @DisplayName("SavingsAccountTransactionCountDiscountCalculator Applicability Tests")
    class TransactionCountCalculatorTests {

        @Test
        @DisplayName("Should be applicable when transaction count threshold is met")
        void shouldBeApplicableWhenCountThresholdMet() {
            // Given: Calculator configured with threshold of 50 transactions
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdCount", 50);
            params.put("periodType", "MONTHLY");
            params.put("directionType", "ALL");
            params.put("discountPercentage", "10");
            countCalculator.configure(params);

            // Mock: Account has 60 transactions (above threshold)
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(60L);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(countCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when transaction count threshold is NOT met")
        void shouldNotBeApplicableWhenCountThresholdNotMet() {
            // Given: Calculator configured with threshold of 50 transactions
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdCount", 50);
            params.put("periodType", "MONTHLY");
            params.put("directionType", "ALL");
            params.put("discountPercentage", "10");
            countCalculator.configure(params);

            // Mock: Account has 30 transactions (below threshold)
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(30L);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(countCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should be applicable when count equals threshold (edge case)")
        void shouldBeApplicableWhenCountEqualsThreshold() {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdCount", 50);
            params.put("periodType", "MONTHLY");
            params.put("directionType", "ALL");
            params.put("discountPercentage", "10");
            countCalculator.configure(params);

            // Mock: Account has exactly 50 transactions
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(50L);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(countCalculator.isApplicable(context)).isTrue();
        }
    }

    @Nested
    @DisplayName("SavingsAccountTransactionFlowDiscountCalculator Applicability Tests")
    class TransactionFlowCalculatorTests {

        @Test
        @DisplayName("Should be applicable when transaction flow threshold is met")
        void shouldBeApplicableWhenFlowThresholdMet() {
            // Given: Calculator configured with threshold of 200k
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdAmount", "200000");
            params.put("directionType", "INFLOW");
            params.put("periodType", "DAILY");
            params.put("discountPercentage", "10");
            flowCalculator.configure(params);

            // Mock: Account has transactions totaling 250k (above threshold)
            List<SavingsAccountTransaction> mockTransactions = createMockTransactionsForFlow(250000.0, true);
            when(transactionRepository.findTransactionsForPeriod(eq(100L), any(), any())).thenReturn(mockTransactions);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(flowCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when transaction flow threshold is NOT met")
        void shouldNotBeApplicableWhenFlowThresholdNotMet() {
            // Given: Calculator configured with threshold of 200k
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdAmount", "200000");
            params.put("directionType", "INFLOW");
            params.put("periodType", "DAILY");
            params.put("discountPercentage", "10");
            flowCalculator.configure(params);

            // Mock: Account has transactions totaling 150k (below threshold)
            List<SavingsAccountTransaction> mockTransactions = createMockTransactionsForFlow(150000.0, true);
            when(transactionRepository.findTransactionsForPeriod(eq(100L), any(), any())).thenReturn(mockTransactions);

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(flowCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should NOT be applicable when no transactions found")
        void shouldNotBeApplicableWhenNoTransactions() {
            // Given
            Map<String, Object> params = new HashMap<>();
            params.put("thresholdAmount", "200000");
            params.put("directionType", "INFLOW");
            params.put("periodType", "DAILY");
            params.put("discountPercentage", "10");
            flowCalculator.configure(params);

            // Mock: No transactions
            when(transactionRepository.findTransactionsForPeriod(eq(100L), any(), any())).thenReturn(new ArrayList<>());

            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));

            // When & Then
            assertThat(flowCalculator.isApplicable(context)).isFalse();
        }
    }

    @Nested
    @DisplayName("SavingsAccountTimeBasedDiscountCalculator Applicability Tests")
    class TimeBasedCalculatorTests {

        @Test
        @DisplayName("Should be applicable when transaction is on weekend (WEEKEND rule)")
        void shouldBeApplicableWhenWeekend() {
            // Given: Calculator configured for weekend discount
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "WEEKEND");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            // Create context with Saturday date
            LocalDate saturday = LocalDate.now().with(DayOfWeek.SATURDAY);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(saturday);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when transaction is NOT on weekend")
        void shouldNotBeApplicableWhenNotWeekend() {
            // Given: Calculator configured for weekend discount
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "WEEKEND");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            // Create context with Monday date
            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(monday);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should be applicable when transaction is on holiday (HOLIDAY rule)")
        void shouldBeApplicableWhenHoliday() {
            // Given: Calculator configured for holiday discount
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "HOLIDAY");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            LocalDate holidayDate = LocalDate.now();
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(holidayDate);
            context.setOfficeId(1L);

            // Mock: Date is a holiday
            when(holidayRepositoryWrapper.isHoliday(eq(1L), eq(holidayDate))).thenReturn(true);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when transaction is NOT on holiday")
        void shouldNotBeApplicableWhenNotHoliday() {
            // Given: Calculator configured for holiday discount
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "HOLIDAY");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            LocalDate regularDate = LocalDate.now();
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(regularDate);
            context.setOfficeId(1L);

            // Mock: Date is NOT a holiday
            when(holidayRepositoryWrapper.isHoliday(eq(1L), eq(regularDate))).thenReturn(false);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("Should be applicable when transaction is within date range")
        void shouldBeApplicableWhenWithinDateRange() {
            // Given: Calculator configured with date range
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "DATE_RANGE");
            params.put("startDate", "01 January 2024");
            params.put("endDate", "31 December 2024");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            LocalDate dateInRange = LocalDate.of(2024, 6, 15);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(dateInRange);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Should NOT be applicable when transaction is outside date range")
        void shouldNotBeApplicableWhenOutsideDateRange() {
            // Given: Calculator configured with date range
            Map<String, Object> params = new HashMap<>();
            params.put("timeRuleType", "DATE_RANGE");
            params.put("startDate", "01 January 2024");
            params.put("endDate", "31 December 2024");
            params.put("discountPercentage", "10");
            timeCalculator.configure(params);

            LocalDate dateOutsideRange = LocalDate.of(2025, 1, 15);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(dateOutsideRange);

            // When & Then
            assertThat(timeCalculator.isApplicable(context)).isFalse();
        }
    }

    @Nested
    @DisplayName("AND Logic Integration Tests")
    class AndLogicIntegrationTests {

        @Test
        @DisplayName("All 3 conditions met - should all be applicable")
        void allConditionsMet_ShouldAllBeApplicable() {
            // Setup Balance Calculator: 500k threshold
            Map<String, Object> balanceParams = new HashMap<>();
            balanceParams.put("minimumAverageBalance", "500000");
            balanceParams.put("discountPercentage", "5");
            balanceCalculator.configure(balanceParams);
            List<SavingsAccountTransaction> balanceTransactions = createMockTransactions(600000.0);
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenReturn(balanceTransactions);

            // Setup Count Calculator: 50 transactions threshold
            Map<String, Object> countParams = new HashMap<>();
            countParams.put("thresholdCount", 50);
            countParams.put("periodType", "MONTHLY");
            countParams.put("directionType", "ALL");
            countParams.put("discountPercentage", "10");
            countCalculator.configure(countParams);
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(60L);

            // Setup Time Calculator: Weekend
            Map<String, Object> timeParams = new HashMap<>();
            timeParams.put("timeRuleType", "WEEKEND");
            timeParams.put("discountPercentage", "10");
            timeCalculator.configure(timeParams);

            LocalDate saturday = LocalDate.now().with(DayOfWeek.SATURDAY);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(saturday);

            // When & Then: All should be applicable
            assertThat(balanceCalculator.isApplicable(context)).isTrue();
            assertThat(countCalculator.isApplicable(context)).isTrue();
            assertThat(timeCalculator.isApplicable(context)).isTrue();
        }

        @Test
        @DisplayName("Only 2 of 3 conditions met - one should NOT be applicable")
        void twoOfThreeConditionsMet_OneShouldNotBeApplicable() {
            // Setup Balance Calculator: 500k threshold - MET
            Map<String, Object> balanceParams = new HashMap<>();
            balanceParams.put("minimumAverageBalance", "500000");
            balanceParams.put("discountPercentage", "5");
            balanceCalculator.configure(balanceParams);
            List<SavingsAccountTransaction> balanceTransactions = createMockTransactions(600000.0);
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenReturn(balanceTransactions);

            // Setup Count Calculator: 50 transactions threshold - MET
            Map<String, Object> countParams = new HashMap<>();
            countParams.put("thresholdCount", 50);
            countParams.put("periodType", "MONTHLY");
            countParams.put("directionType", "ALL");
            countParams.put("discountPercentage", "10");
            countCalculator.configure(countParams);
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(60L);

            // Setup Time Calculator: Weekend - NOT MET (Monday)
            Map<String, Object> timeParams = new HashMap<>();
            timeParams.put("timeRuleType", "WEEKEND");
            timeParams.put("discountPercentage", "10");
            timeCalculator.configure(timeParams);

            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(monday);

            // When & Then: Balance and Count applicable, Time NOT applicable
            assertThat(balanceCalculator.isApplicable(context)).isTrue();
            assertThat(countCalculator.isApplicable(context)).isTrue();
            assertThat(timeCalculator.isApplicable(context)).isFalse();
        }

        @Test
        @DisplayName("None of the conditions met - all should NOT be applicable")
        void noConditionsMet_AllShouldNotBeApplicable() {
            // Setup Balance Calculator: 500k threshold - NOT MET
            Map<String, Object> balanceParams = new HashMap<>();
            balanceParams.put("minimumAverageBalance", "500000");
            balanceParams.put("discountPercentage", "5");
            balanceCalculator.configure(balanceParams);
            List<SavingsAccountTransaction> balanceTransactions = createMockTransactions(300000.0);
            when(transactionRepository.findTransactionsForPeriod(any(), any(), any())).thenReturn(balanceTransactions);

            // Setup Count Calculator: 50 transactions threshold - NOT MET
            Map<String, Object> countParams = new HashMap<>();
            countParams.put("thresholdCount", 50);
            countParams.put("periodType", "MONTHLY");
            countParams.put("directionType", "ALL");
            countParams.put("discountPercentage", "10");
            countCalculator.configure(countParams);
            when(transactionRepository.countTransactionsForPeriod(eq(100L), any(), any(), anyBoolean(), anyList())).thenReturn(30L);

            // Setup Time Calculator: Weekend - NOT MET (Monday)
            Map<String, Object> timeParams = new HashMap<>();
            timeParams.put("timeRuleType", "WEEKEND");
            timeParams.put("discountPercentage", "10");
            timeCalculator.configure(timeParams);

            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            DiscountContext context = createContext(100L, BigDecimal.valueOf(100.0));
            context.setTransactionDate(monday);

            // When & Then: None should be applicable
            assertThat(balanceCalculator.isApplicable(context)).isFalse();
            assertThat(countCalculator.isApplicable(context)).isFalse();
            assertThat(timeCalculator.isApplicable(context)).isFalse();
        }
    }

    // Helper methods
    private DiscountContext createContext(Long accountId, BigDecimal transactionAmount) {
        DiscountContext context = new DiscountContext();
        context.setAccountId(accountId);
        context.setTransactionAmount(transactionAmount);
        context.setTransactionDate(LocalDate.now());
        return context;
    }

    private List<SavingsAccountTransaction> createMockTransactions(double balance) {
        List<SavingsAccountTransaction> transactions = new ArrayList<>();
        SavingsAccountTransaction tx = org.mockito.Mockito.mock(SavingsAccountTransaction.class);
        when(tx.getDateOf()).thenReturn(LocalDate.now().withDayOfMonth(1).minusDays(1));
        when(tx.isReversed()).thenReturn(false);
        when(tx.getRunningBalance()).thenReturn(BigDecimal.valueOf(balance));
        transactions.add(tx);
        return transactions;
    }

    private List<SavingsAccountTransaction> createMockTransactionsForFlow(double amount, boolean isCredit) {
        List<SavingsAccountTransaction> transactions = new ArrayList<>();
        SavingsAccountTransaction tx = org.mockito.Mockito.mock(SavingsAccountTransaction.class,
                org.mockito.Mockito.withSettings().lenient());
        when(tx.getAmount()).thenReturn(BigDecimal.valueOf(amount));
        when(tx.isCredit()).thenReturn(isCredit);
        when(tx.isDebit()).thenReturn(!isCredit);
        when(tx.isReversed()).thenReturn(false);
        transactions.add(tx);
        return transactions;
    }
}
