package com.paystack.fineract.portfolio.charge.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.MonthDay;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainServiceJpa;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.data.ChargeSlabData;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformServiceImpl;
import org.apache.fineract.portfolio.common.service.CommonEnumerations;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.tax.data.TaxGroupData;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformService;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Extended charge read platform service for Paystack custom module. Overrides specific methods to handle fee split
 * functionality for savings operations. Uses @Order(2) to avoid conflicts with other custom charge services.
 */
@Service
@Order(2)
public class PaystackChargeReadPlatformServiceImpl extends ChargeReadPlatformServiceImpl implements ChargeReadPlatformService {

    private final JdbcTemplate jdbcTemplate;

    public PaystackChargeReadPlatformServiceImpl(CurrencyReadPlatformService currencyReadPlatformService,
            ChargeDropdownReadPlatformService chargeDropdownReadPlatformService, JdbcTemplate jdbcTemplate,
            DropdownReadPlatformService dropdownReadPlatformService, FineractEntityAccessUtil fineractEntityAccessUtil,
            AccountingDropdownReadPlatformService accountingDropdownReadPlatformService, TaxReadPlatformService taxReadPlatformService,
            ConfigurationDomainServiceJpa configurationDomainServiceJpa, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        super(currencyReadPlatformService, chargeDropdownReadPlatformService, jdbcTemplate, dropdownReadPlatformService,
                fineractEntityAccessUtil, accountingDropdownReadPlatformService, taxReadPlatformService, configurationDomainServiceJpa,
                namedParameterJdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Override to use custom mapper with fee split functionality for individual charge retrieval.
     */
    @Override
    public ChargeData retrieveCharge(final Long chargeId) {
        try {
            final PaystackChargeMapper rm = new PaystackChargeMapper();

            String sql = "select " + rm.chargeSchema() + " where c.id = ? and c.is_deleted=false ";

            // Use the parent class method to get the office-specific clause
            String officeClause = getOfficeSpecificClause();
            sql += officeClause;

            sql = sql + " ;";
            ChargeData chargeData = getJdbcTemplate().queryForObject(sql, rm, new Object[] { chargeId }); // NOSONAR
            if (chargeData != null && chargeData.getVaryAmounts()) {
                ChargeSlabMapper slabMapper = new ChargeSlabMapper();
                final String slabSQL = "select " + slabMapper.getSchema() + " where c.charge_id = ? order by c.from_amount asc";
                List<ChargeSlabData> slabData = this.jdbcTemplate.query(slabSQL, slabMapper, new Object[] { chargeId });
                chargeData.setChargeSlabs(slabData);
            }

            return chargeData;
        } catch (final org.springframework.dao.EmptyResultDataAccessException e) {
            throw new org.apache.fineract.portfolio.charge.exception.ChargeNotFoundException(chargeId, e);
        }
    }

    /**
     * Override to use custom mapper with fee split functionality for all charges.
     */
    @Override
    public List<ChargeData> retrieveAllCharges() {
        final PaystackChargeMapper rm = new PaystackChargeMapper();

        String sql = "select " + rm.chargeSchema() + " where c.is_deleted=false ";

        // Use the parent class method to get the office-specific clause
        String officeClause = getOfficeSpecificClause();
        sql += officeClause;

        sql += " order by c.name ";

        return getJdbcTemplate().query(sql, rm); // NOSONAR
    }

    /**
     * Override to use custom mapper with fee split functionality for savings product applicable charges.
     */
    @Override
    public List<ChargeData> retrieveSavingsProductApplicableCharges(final boolean feeChargesOnly) {
        final PaystackChargeMapper rm = new PaystackChargeMapper();

        String sql = "select " + rm.chargeSchema() + " where c.is_deleted=false and c.is_active=true and c.charge_applies_to_enum=? ";
        if (feeChargesOnly) {
            sql = "select " + rm.chargeSchema()
                    + " where c.is_deleted=false and c.is_active=true and c.is_penalty=false and c.charge_applies_to_enum=? ";
        }

        // Use the parent class method to get the office-specific clause
        String officeClause = getOfficeSpecificClause();
        sql += officeClause;

        sql += " order by c.name ";

        return getJdbcTemplate().query(sql, rm,
                new Object[] { org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo.SAVINGS.getValue() }); // NOSONAR
    }

    /**
     * Override to use custom mapper with fee split functionality for savings product charges.
     */
    @Override
    public List<ChargeData> retrieveSavingsProductCharges(final Long savingsProductId) {
        final PaystackChargeMapper rm = new PaystackChargeMapper();

        String sql = "select " + rm.savingsProductChargeSchema()
                + " where c.is_deleted=false and c.is_active=true and spc.savings_product_id=? ";

        // Use the parent class method to get the office-specific clause
        String officeClause = getOfficeSpecificClause();
        sql += officeClause;

        return getJdbcTemplate().query(sql, rm, new Object[] { savingsProductId }); // NOSONAR
    }

    /**
     * Override to use custom mapper with fee split functionality for savings account applicable charges.
     */
    @Override
    public List<ChargeData> retrieveSavingsAccountApplicableCharges(Long savingsAccountId) {
        final PaystackChargeMapper rm = new PaystackChargeMapper();

        String sql = "select " + rm.chargeSchema() + " join m_savings_account sa on sa.currency_code = c.currency_code"
                + " where c.is_deleted=false and c.is_active=true and c.charge_applies_to_enum=? " + " and sa.id = ?";

        // Use the parent class method to get the office-specific clause
        String officeClause = getOfficeSpecificClause();
        sql += officeClause;

        return getJdbcTemplate().query(sql, rm,
                new Object[] { org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo.SAVINGS.getValue(), savingsAccountId }); // NOSONAR
    }

    /**
     * Helper method to get the office-specific clause using reflection to access parent class method.
     */
    private String getOfficeSpecificClause() {
        try {
            // Use reflection to call the private method from parent class
            java.lang.reflect.Method method = ChargeReadPlatformServiceImpl.class
                    .getDeclaredMethod("addInClauseToSQL_toLimitChargesMappedToOffice_ifOfficeSpecificProductsEnabled");
            method.setAccessible(true);
            return (String) method.invoke(this);
        } catch (Exception e) {
            // Fallback to empty string if reflection fails
            return "";
        }
    }

    /**
     * Helper method to get JdbcTemplate using reflection.
     */
    private JdbcTemplate getJdbcTemplate() {
        try {
            // Use reflection to access the private jdbcTemplate field
            java.lang.reflect.Field field = ChargeReadPlatformServiceImpl.class.getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            return (JdbcTemplate) field.get(this);
        } catch (Exception e) {
            // This should not happen, but provide a fallback
            throw new RuntimeException("Failed to access JdbcTemplate", e);
        }
    }

    /**
     * Custom ChargeMapper that handles fee split functionality for savings operations. Extends the core ChargeMapper to
     * include enable_fee_split field.
     */
    private static final class PaystackChargeMapper
            extends org.apache.fineract.portfolio.charge.service.ChargeReadPlatformServiceImpl.ChargeMapper {

        @Override
        public String chargeSchema() {
            // Include the enable_fee_split field in our custom schema
            return "c.id as id, c.name as name, c.amount as amount, c.currency_code as currencyCode, "
                    + "c.charge_applies_to_enum as chargeAppliesTo, c.charge_time_enum as chargeTime, "
                    + "c.charge_payment_mode_enum as chargePaymentMode, "
                    + "c.charge_calculation_enum as chargeCalculation, c.is_penalty as penalty, "
                    + "c.is_active as active, c.is_free_withdrawal as isFreeWithdrawal, c.free_withdrawal_charge_frequency as freeWithdrawalChargeFrequency, c.restart_frequency as restartFrequency, c.restart_frequency_enum as restartFrequencyEnum,"
                    + "oc.name as currencyName, oc.decimal_places as currencyDecimalPlaces, "
                    + "oc.currency_multiplesof as inMultiplesOf, oc.display_symbol as currencyDisplaySymbol, "
                    + "oc.internationalized_name_code as currencyNameCode, c.fee_on_day as feeOnDay, c.fee_on_month as feeOnMonth, "
                    + "c.fee_interval as feeInterval, c.fee_frequency as feeFrequency,c.min_cap as minCap,c.max_cap as maxCap, "
                    + "c.income_or_liability_account_id as glAccountId , acc.name as glAccountName, acc.gl_code as glCode, "
                    + "tg.id as taxGroupId, c.is_payment_type as isPaymentType, pt.id as paymentTypeId, pt.value as paymentTypeName, tg.name as taxGroupName, "
                    + "c.enable_fee_split as enableFeeSplit, " + "c.has_varying_charge as varyingCharge " + "from m_charge c "
                    + "join m_organisation_currency oc on c.currency_code = oc.code "
                    + " LEFT JOIN acc_gl_account acc on acc.id = c.income_or_liability_account_id "
                    + " LEFT JOIN m_tax_group tg on tg.id = c.tax_group_id " + " LEFT JOIN m_payment_type pt on pt.id = c.payment_type_id ";
        }

        @Override
        public ChargeData mapRow(final ResultSet rs, final int rowNum) throws SQLException {
            // Extract all core fields using parent method
            final Long id = rs.getLong("id");
            final String name = rs.getString("name");
            final BigDecimal amount = rs.getBigDecimal("amount");

            final String currencyCode = rs.getString("currencyCode");
            final String currencyName = rs.getString("currencyName");
            final String currencyNameCode = rs.getString("currencyNameCode");
            final String currencyDisplaySymbol = rs.getString("currencyDisplaySymbol");
            final Integer currencyDecimalPlaces = JdbcSupport.getInteger(rs, "currencyDecimalPlaces");
            final Integer inMultiplesOf = JdbcSupport.getInteger(rs, "inMultiplesOf");

            final CurrencyData currency = new CurrencyData(currencyCode, currencyName, currencyDecimalPlaces, inMultiplesOf,
                    currencyDisplaySymbol, currencyNameCode);

            final int chargeAppliesTo = rs.getInt("chargeAppliesTo");
            final EnumOptionData chargeAppliesToType = org.apache.fineract.portfolio.charge.service.ChargeEnumerations
                    .chargeAppliesTo(chargeAppliesTo);

            final int chargeTime = rs.getInt("chargeTime");
            final EnumOptionData chargeTimeType = org.apache.fineract.portfolio.charge.service.ChargeEnumerations
                    .chargeTimeType(chargeTime);

            final int chargeCalculation = rs.getInt("chargeCalculation");
            final EnumOptionData chargeCalculationType = org.apache.fineract.portfolio.charge.service.ChargeEnumerations
                    .chargeCalculationType(chargeCalculation);

            final int paymentMode = rs.getInt("chargePaymentMode");
            final EnumOptionData chargePaymentMode = org.apache.fineract.portfolio.charge.service.ChargeEnumerations
                    .chargePaymentMode(paymentMode);

            final boolean penalty = rs.getBoolean("penalty");
            final boolean active = rs.getBoolean("active");

            final Integer feeInterval = JdbcSupport.getInteger(rs, "feeInterval");
            EnumOptionData feeFrequencyType = null;
            final Integer feeFrequency = JdbcSupport.getInteger(rs, "feeFrequency");
            if (feeFrequency != null) {
                feeFrequencyType = CommonEnumerations.termFrequencyType(feeFrequency, "feeFrequency");
            }
            MonthDay feeOnMonthDay = null;
            final Integer feeOnMonth = JdbcSupport.getInteger(rs, "feeOnMonth");
            final Integer feeOnDay = JdbcSupport.getInteger(rs, "feeOnDay");
            if (feeOnDay != null && feeOnMonth != null) {
                feeOnMonthDay = MonthDay.now(DateUtils.getDateTimeZoneOfTenant()).withDayOfMonth(feeOnDay).withMonth(feeOnMonth);
            }
            final BigDecimal minCap = rs.getBigDecimal("minCap");
            final BigDecimal maxCap = rs.getBigDecimal("maxCap");

            // extract GL Account
            final Long glAccountId = JdbcSupport.getLong(rs, "glAccountId");
            final String glAccountName = rs.getString("glAccountName");
            final String glCode = rs.getString("glCode");
            org.apache.fineract.accounting.glaccount.data.GLAccountData glAccountData = null;
            if (glAccountId != null) {
                glAccountData = new org.apache.fineract.accounting.glaccount.data.GLAccountData().setId(glAccountId).setName(glAccountName)
                        .setGlCode(glCode);
            }

            final Long taxGroupId = JdbcSupport.getLong(rs, "taxGroupId");
            final String taxGroupName = rs.getString("taxGroupName");
            TaxGroupData taxGroupData = null;
            if (taxGroupId != null) {
                taxGroupData = TaxGroupData.lookup(taxGroupId, taxGroupName);
            }

            final boolean isFreeWithdrawal = rs.getBoolean("isFreeWithdrawal");
            final int freeWithdrawalChargeFrequency = rs.getInt("freeWithdrawalChargeFrequency");
            final int restartFrequency = rs.getInt("restartFrequency");
            final int restartFrequencyEnum = rs.getInt("restartFrequencyEnum");

            final boolean isPaymentType = rs.getBoolean("isPaymentType");
            final Long paymentTypeId = JdbcSupport.getLong(rs, "paymentTypeId");

            final String paymentTypeName = rs.getString("paymentTypeName");
            PaymentTypeData paymentTypeData = null;
            if (paymentTypeId != null) {
                paymentTypeData = PaymentTypeData.instance(paymentTypeId, paymentTypeName);
            }

            // Extract our custom fee split field
            final boolean enableFeeSplit = rs.getBoolean("enableFeeSplit");

            final Boolean varyingCharge = rs.getBoolean("varyingCharge");

            // Build ChargeData with our custom field
            return ChargeData.builder().id(id).name(name).amount(amount).currency(currency).chargeTimeType(chargeTimeType)
                    .chargeAppliesTo(chargeAppliesToType).chargeCalculationType(chargeCalculationType).chargePaymentMode(chargePaymentMode)
                    .feeOnMonthDay(feeOnMonthDay).feeInterval(feeInterval).penalty(penalty).active(active).freeWithdrawal(isFreeWithdrawal)
                    .freeWithdrawalChargeFrequency(freeWithdrawalChargeFrequency).restartFrequency(restartFrequency)
                    .restartFrequencyEnum(restartFrequencyEnum).isPaymentType(isPaymentType).paymentTypeOptions(paymentTypeData)
                    .minCap(minCap).maxCap(maxCap).feeFrequency(feeFrequencyType).incomeOrLiabilityAccount(glAccountData)
                    .taxGroup(taxGroupData).enableFeeSplit(enableFeeSplit).varyAmounts(varyingCharge).build();
        }
    }

    private static class ChargeSlabMapper implements RowMapper<ChargeSlabData> {

        public String getSchema() {
            return "c.id as id, c.from_amount as fromAmount, c.to_amount as toAmount, c.fee_amount as value from m_fee_charge_slab c";
        }

        @Override
        public ChargeSlabData mapRow(ResultSet rs, int rowNum) throws SQLException {
            final Long id = rs.getLong("id");
            final BigDecimal fromAmount = rs.getBigDecimal("fromAmount");
            final BigDecimal toAmount = rs.getBigDecimal("toAmount");
            final BigDecimal value = rs.getBigDecimal("value");

            return new ChargeSlabData(id, fromAmount, toAmount, value);
        }
    }
}
