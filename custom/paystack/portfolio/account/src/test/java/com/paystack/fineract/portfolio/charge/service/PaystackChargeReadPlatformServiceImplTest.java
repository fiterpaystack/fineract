package com.paystack.fineract.portfolio.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainServiceJpa;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PaystackChargeReadPlatformServiceImplTest {

    @Mock
    private CurrencyReadPlatformService currencyReadPlatformService;
    @Mock
    private ChargeDropdownReadPlatformService chargeDropdownReadPlatformService;
    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private DropdownReadPlatformService dropdownReadPlatformService;
    @Mock
    private FineractEntityAccessUtil fineractEntityAccessUtil;
    @Mock
    private AccountingDropdownReadPlatformService accountingDropdownReadPlatformService;
    @Mock
    private TaxReadPlatformService taxReadPlatformService;
    @Mock
    private ConfigurationDomainServiceJpa configurationDomainServiceJpa;
    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Mock
    private DiscountRuleService discountRuleService;

    private PaystackChargeReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaystackChargeReadPlatformServiceImpl(currencyReadPlatformService, chargeDropdownReadPlatformService, jdbcTemplate,
                dropdownReadPlatformService, fineractEntityAccessUtil, accountingDropdownReadPlatformService, taxReadPlatformService,
                configurationDomainServiceJpa, namedParameterJdbcTemplate, discountRuleService);
    }

    @Test
    void shouldIncludeDiscountRulesInAdditionalAttributes() {
        // Given
        Long chargeId = 1L;
        com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData assignment = com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData
                .builder().ruleId(1L).ruleName("Test Rule").ruleType("TIME_BASED").active(true).rulePriority(1).assignmentPriority(1)
                .build();
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenReturn(List.of(assignment));

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(true);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).hasSize(1);
        assertThat(discountRules.get(0).getRuleName()).isEqualTo("Test Rule");
    }

    @Test
    void shouldHandleEmptyDiscountRules() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenReturn(List.of());

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).isEmpty();
    }

    @Test
    void shouldHandleDiscountServiceException() {
        // Given
        Long chargeId = 1L;
        when(discountRuleService.getAssignmentDataForCharge(eq(chargeId))).thenThrow(new RuntimeException("Service unavailable"));

        // When
        Map<String, Object> additionalAttributes = service.getAdditionalAttributes(chargeId);

        // Then
        assertThat(additionalAttributes).isNotNull();
        assertThat(additionalAttributes.get("enableDiscountEngine")).isEqualTo(false);
        assertThat(additionalAttributes.get("discountRules")).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData> discountRules = (List<com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData>) additionalAttributes
                .get("discountRules");
        assertThat(discountRules).isEmpty();
    }

    // --- feeOnMonthDay mapper tests (same MonthDay regardless of current date; clamping for invalid day/month) ---

    @Test
    @SuppressWarnings("unchecked")
    void retrieveSavingsProductApplicableCharges_mapsFeeOnMonthDay_independentOfCurrentMonth() throws SQLException {
        // Charge with day 30, month 8 (August) must yield August 30 regardless of when the test runs
        ResultSet rs = createChargeResultSet(30, 8);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            RowMapper<ChargeData> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<ChargeData> result = service.retrieveSavingsProductApplicableCharges(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFeeOnMonthDay()).isEqualTo(MonthDay.of(8, 30));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveSavingsProductApplicableCharges_clampsDay30February_toLastDayOfFebruary() throws SQLException {
        // Day 30 with month 2 (February) must be clamped to 28 (or 29 in leap years) -> February 29
        ResultSet rs = createChargeResultSet(30, 2);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            RowMapper<ChargeData> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<ChargeData> result = service.retrieveSavingsProductApplicableCharges(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFeeOnMonthDay()).isEqualTo(MonthDay.of(2, 29)); // February max 29
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveSavingsProductApplicableCharges_keepsDay31January_unchanged() throws SQLException {
        ResultSet rs = createChargeResultSet(31, 1);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            RowMapper<ChargeData> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<ChargeData> result = service.retrieveSavingsProductApplicableCharges(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFeeOnMonthDay()).isEqualTo(MonthDay.of(1, 31));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrieveSavingsProductApplicableCharges_clampsDay31April_to30() throws SQLException {
        ResultSet rs = createChargeResultSet(31, 4);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            RowMapper<ChargeData> mapper = inv.getArgument(1);
            return List.of(mapper.mapRow(rs, 0));
        });

        List<ChargeData> result = service.retrieveSavingsProductApplicableCharges(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFeeOnMonthDay()).isEqualTo(MonthDay.of(4, 30));
    }

    /**
     * Builds a mock ResultSet with all columns required by PaystackChargeMapper.mapRow, with feeOnDay/feeOnMonth set as
     * given (others use minimal defaults so ChargeData builds successfully).
     */
    private static ResultSet createChargeResultSet(Integer feeOnDay, Integer feeOnMonth) throws SQLException {
        ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(1L);
        when(rs.getString("name")).thenReturn("Test Charge");
        when(rs.getBigDecimal("amount")).thenReturn(BigDecimal.ONE);
        when(rs.getString("currencyCode")).thenReturn("NGN");
        when(rs.getString("currencyName")).thenReturn("Naira");
        when(rs.getString("currencyNameCode")).thenReturn("currency.NGN");
        when(rs.getString("currencyDisplaySymbol")).thenReturn("N");
        when(rs.findColumn("currencyDecimalPlaces")).thenReturn(1);
        when(rs.findColumn("inMultiplesOf")).thenReturn(2);
        when(rs.findColumn("feeInterval")).thenReturn(3);
        when(rs.findColumn("feeFrequency")).thenReturn(4);
        when(rs.findColumn("feeOnMonth")).thenReturn(5);
        when(rs.findColumn("feeOnDay")).thenReturn(6);
        when(rs.findColumn("glAccountId")).thenReturn(7);
        when(rs.findColumn("taxGroupId")).thenReturn(8);
        when(rs.findColumn("paymentTypeId")).thenReturn(9);
        // JdbcUtils.getResultSetValue uses getInt(index) / getLong(index) for Integer/Long
        lenient().when(rs.getInt(1)).thenReturn(2);
        lenient().when(rs.getInt(2)).thenReturn(0);
        lenient().when(rs.getInt(3)).thenReturn(1);
        lenient().when(rs.getInt(4)).thenReturn(3);
        lenient().when(rs.getInt(5)).thenReturn(feeOnMonth);
        lenient().when(rs.getInt(6)).thenReturn(feeOnDay);
        lenient().when(rs.getLong(7)).thenReturn(0L);
        lenient().when(rs.getLong(8)).thenReturn(0L);
        lenient().when(rs.getLong(9)).thenReturn(0L);
        lenient().when(rs.getObject(1, Integer.class)).thenReturn(2);
        lenient().when(rs.getObject(2, Integer.class)).thenReturn(null);
        lenient().when(rs.getObject(3, Integer.class)).thenReturn(1);
        lenient().when(rs.getObject(4, Integer.class)).thenReturn(3);
        lenient().when(rs.getObject(5, Integer.class)).thenReturn(feeOnMonth);
        lenient().when(rs.getObject(6, Integer.class)).thenReturn(feeOnDay);
        lenient().when(rs.getObject(7, Long.class)).thenReturn(null);
        lenient().when(rs.getObject(8, Long.class)).thenReturn(null);
        lenient().when(rs.getObject(9, Long.class)).thenReturn(null);
        when(rs.getInt("chargeAppliesTo")).thenReturn(ChargeAppliesTo.SAVINGS.getValue());
        when(rs.getInt("chargeTime")).thenReturn(2);
        when(rs.getInt("chargeCalculation")).thenReturn(1);
        when(rs.getInt("chargePaymentMode")).thenReturn(0);
        when(rs.getBoolean("penalty")).thenReturn(false);
        when(rs.getBoolean("active")).thenReturn(true);
        when(rs.getBigDecimal("minCap")).thenReturn(null);
        when(rs.getBigDecimal("maxCap")).thenReturn(null);
        when(rs.getString("glAccountName")).thenReturn(null);
        when(rs.getString("glCode")).thenReturn(null);
        when(rs.getString("taxGroupName")).thenReturn(null);
        when(rs.getBoolean("isFreeWithdrawal")).thenReturn(false);
        when(rs.getInt("freeWithdrawalChargeFrequency")).thenReturn(0);
        when(rs.getInt("restartFrequency")).thenReturn(0);
        when(rs.getInt("restartFrequencyEnum")).thenReturn(0);
        when(rs.getBoolean("isPaymentType")).thenReturn(false);
        when(rs.getString("paymentTypeName")).thenReturn(null);
        when(rs.getBoolean("enableFeeSplit")).thenReturn(false);
        when(rs.getBoolean("varyingCharge")).thenReturn(false);
        return rs;
    }
}
