package com.paystack.fineract.portfolio.charge.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.MonthDay;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.fineract.accounting.glaccount.data.GLAccountData;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.monetary.data.CurrencyData;
import org.apache.fineract.portfolio.charge.data.ChargeData;
import org.apache.fineract.portfolio.charge.data.ChargeSlabData;
import org.apache.fineract.portfolio.paymenttype.data.PaymentTypeData;
import org.apache.fineract.portfolio.tax.data.TaxGroupData;

/**
 * Extended ChargeData for Paystack custom module with additional attributes for discount rules.
 */
@Getter
@EqualsAndHashCode(of = "id")
@Builder(toBuilder = true)
public class PaystackChargeData implements Serializable {

    private final Long id;
    private final String name;

    @Builder.Default
    private final boolean active = Boolean.FALSE;
    @Builder.Default
    private final boolean penalty = Boolean.FALSE;
    @Builder.Default
    private final boolean freeWithdrawal = Boolean.FALSE;
    @Builder.Default
    private final boolean isPaymentType = Boolean.FALSE;
    @Builder.Default
    private final boolean enableFeeSplit = Boolean.FALSE;

    private final Integer freeWithdrawalChargeFrequency;
    private final Integer restartFrequency;
    private final Integer restartFrequencyEnum;
    private final PaymentTypeData paymentTypeOptions;
    private final CurrencyData currency;
    private final BigDecimal amount;
    private final EnumOptionData chargeTimeType;
    private final EnumOptionData chargeAppliesTo;
    private final EnumOptionData chargeCalculationType;
    private final EnumOptionData chargePaymentMode;
    private final MonthDay feeOnMonthDay;
    private final Integer feeInterval;
    private final BigDecimal minCap;
    private final BigDecimal maxCap;
    private final EnumOptionData feeFrequency;
    private final GLAccountData incomeOrLiabilityAccount;
    private final TaxGroupData taxGroup;

    // template attributes
    private final Collection<CurrencyData> currencyOptions;
    private final List<EnumOptionData> chargeCalculationTypeOptions;
    private final List<EnumOptionData> chargeAppliesToOptions;
    private final List<EnumOptionData> chargeTimeTypeOptions;
    private final List<EnumOptionData> chargePaymetModeOptions;

    private final List<EnumOptionData> loanChargeCalculationTypeOptions;
    private final List<EnumOptionData> loanChargeTimeTypeOptions;
    private final List<EnumOptionData> savingsChargeCalculationTypeOptions;
    private final List<EnumOptionData> savingsChargeTimeTypeOptions;
    private final List<EnumOptionData> clientChargeCalculationTypeOptions;
    private final List<EnumOptionData> clientChargeTimeTypeOptions;
    private final List<EnumOptionData> shareChargeCalculationTypeOptions;
    private final List<EnumOptionData> shareChargeTimeTypeOptions;

    private final List<EnumOptionData> feeFrequencyOptions;

    private final Map<String, List<GLAccountData>> incomeOrLiabilityAccountOptions;
    private final Collection<TaxGroupData> taxGroupOptions;

    private final String accountMappingForChargeConfig;
    private final List<GLAccountData> expenseAccountOptions;
    private final List<GLAccountData> assetAccountOptions;

    private final Boolean varyAmounts;
    private List<ChargeSlabData> chargeSlabs;

    // Paystack custom attributes
    private final Map<String, Object> additionalAttributes;

    /**
     * Convert from core ChargeData to PaystackChargeData
     */
    public static PaystackChargeData fromChargeData(ChargeData chargeData, Map<String, Object> additionalAttributes) {
        return PaystackChargeData.builder()
                .id(chargeData.getId())
                .name(chargeData.getName())
                .active(chargeData.isActive())
                .penalty(chargeData.isPenalty())
                .freeWithdrawal(chargeData.isFreeWithdrawal())
                .isPaymentType(chargeData.isIsPaymentType())
                .enableFeeSplit(chargeData.isEnableFeeSplit())
                .freeWithdrawalChargeFrequency(chargeData.getFreeWithdrawalChargeFrequency())
                .restartFrequency(chargeData.getRestartFrequency())
                .restartFrequencyEnum(chargeData.getRestartFrequencyEnum())
                .paymentTypeOptions(chargeData.getPaymentTypeOptions())
                .currency(chargeData.getCurrency())
                .amount(chargeData.getAmount())
                .chargeTimeType(chargeData.getChargeTimeType())
                .chargeAppliesTo(chargeData.getChargeAppliesTo())
                .chargeCalculationType(chargeData.getChargeCalculationType())
                .chargePaymentMode(chargeData.getChargePaymentMode())
                .feeOnMonthDay(chargeData.getFeeOnMonthDay())
                .feeInterval(chargeData.getFeeInterval())
                .minCap(chargeData.getMinCap())
                .maxCap(chargeData.getMaxCap())
                .feeFrequency(chargeData.getFeeFrequency())
                .incomeOrLiabilityAccount(chargeData.getIncomeOrLiabilityAccount())
                .taxGroup(chargeData.getTaxGroup())
                .currencyOptions(chargeData.getCurrencyOptions())
                .chargeCalculationTypeOptions(chargeData.getChargeCalculationTypeOptions())
                .chargeAppliesToOptions(chargeData.getChargeAppliesToOptions())
                .chargeTimeTypeOptions(chargeData.getChargeTimeTypeOptions())
                .chargePaymetModeOptions(chargeData.getChargePaymetModeOptions())
                .loanChargeCalculationTypeOptions(chargeData.getLoanChargeCalculationTypeOptions())
                .loanChargeTimeTypeOptions(chargeData.getLoanChargeTimeTypeOptions())
                .savingsChargeCalculationTypeOptions(chargeData.getSavingsChargeCalculationTypeOptions())
                .savingsChargeTimeTypeOptions(chargeData.getSavingsChargeTimeTypeOptions())
                .clientChargeCalculationTypeOptions(chargeData.getClientChargeCalculationTypeOptions())
                .clientChargeTimeTypeOptions(chargeData.getClientChargeTimeTypeOptions())
                .shareChargeCalculationTypeOptions(chargeData.getShareChargeCalculationTypeOptions())
                .shareChargeTimeTypeOptions(chargeData.getShareChargeTimeTypeOptions())
                .feeFrequencyOptions(chargeData.getFeeFrequencyOptions())
                .incomeOrLiabilityAccountOptions(chargeData.getIncomeOrLiabilityAccountOptions())
                .taxGroupOptions(chargeData.getTaxGroupOptions())
                .accountMappingForChargeConfig(chargeData.getAccountMappingForChargeConfig())
                .expenseAccountOptions(chargeData.getExpenseAccountOptions())
                .assetAccountOptions(chargeData.getAssetAccountOptions())
                .varyAmounts(chargeData.getVaryAmounts())
                .chargeSlabs(chargeData.getChargeSlabs())
                .additionalAttributes(additionalAttributes)
                .build();
    }

    /**
     * Convert to core ChargeData (for backward compatibility)
     */
    public ChargeData toChargeData() {
        return ChargeData.builder()
                .id(this.id)
                .name(this.name)
                .active(this.active)
                .penalty(this.penalty)
                .freeWithdrawal(this.freeWithdrawal)
                .isPaymentType(this.isPaymentType)
                .enableFeeSplit(this.enableFeeSplit)
                .freeWithdrawalChargeFrequency(this.freeWithdrawalChargeFrequency)
                .restartFrequency(this.restartFrequency)
                .restartFrequencyEnum(this.restartFrequencyEnum)
                .paymentTypeOptions(this.paymentTypeOptions)
                .currency(this.currency)
                .amount(this.amount)
                .chargeTimeType(this.chargeTimeType)
                .chargeAppliesTo(this.chargeAppliesTo)
                .chargeCalculationType(this.chargeCalculationType)
                .chargePaymentMode(this.chargePaymentMode)
                .feeOnMonthDay(this.feeOnMonthDay)
                .feeInterval(this.feeInterval)
                .minCap(this.minCap)
                .maxCap(this.maxCap)
                .feeFrequency(this.feeFrequency)
                .incomeOrLiabilityAccount(this.incomeOrLiabilityAccount)
                .taxGroup(this.taxGroup)
                .currencyOptions(this.currencyOptions)
                .chargeCalculationTypeOptions(this.chargeCalculationTypeOptions)
                .chargeAppliesToOptions(this.chargeAppliesToOptions)
                .chargeTimeTypeOptions(this.chargeTimeTypeOptions)
                .chargePaymetModeOptions(this.chargePaymetModeOptions)
                .loanChargeCalculationTypeOptions(this.loanChargeCalculationTypeOptions)
                .loanChargeTimeTypeOptions(this.loanChargeTimeTypeOptions)
                .savingsChargeCalculationTypeOptions(this.savingsChargeCalculationTypeOptions)
                .savingsChargeTimeTypeOptions(this.savingsChargeTimeTypeOptions)
                .clientChargeCalculationTypeOptions(this.clientChargeCalculationTypeOptions)
                .clientChargeTimeTypeOptions(this.clientChargeTimeTypeOptions)
                .shareChargeCalculationTypeOptions(this.shareChargeCalculationTypeOptions)
                .shareChargeTimeTypeOptions(this.shareChargeTimeTypeOptions)
                .feeFrequencyOptions(this.feeFrequencyOptions)
                .incomeOrLiabilityAccountOptions(this.incomeOrLiabilityAccountOptions)
                .taxGroupOptions(this.taxGroupOptions)
                .accountMappingForChargeConfig(this.accountMappingForChargeConfig)
                .expenseAccountOptions(this.expenseAccountOptions)
                .assetAccountOptions(this.assetAccountOptions)
                .varyAmounts(this.varyAmounts)
                .chargeSlabs(this.chargeSlabs)
                .additionalAttributes(this.additionalAttributes)
                .build();
    }
}
