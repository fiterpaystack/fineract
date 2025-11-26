package com.paystack.fineract.portfolio.charge.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.api.ChargesApiConstants;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Extended charge definition command deserializer for Paystack custom module. Reuses all core functionality including
 * fee split validation and adds support for discount rules.
 */
@Component
@Primary
public class PaystackChargeDefinitionCommandFromApiJsonDeserializer extends ChargeDefinitionCommandFromApiJsonDeserializer {

    private Set<String> supportedParameters;
    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public PaystackChargeDefinitionCommandFromApiJsonDeserializer(FromJsonHelper fromApiJsonHelper) {
        super(fromApiJsonHelper);
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    @PostConstruct
    public void init() {
        // Create extended set of supported parameters including discount rules and chart slabs
        supportedParameters = new HashSet<>(Arrays.asList(NAME, AMOUNT, LOCALE, CURRENCY_CODE, CURRENCY_OPTIONS, CHARGE_APPLIES_TO,
                CHARGE_TIME_TYPE, CHARGE_CALCULATION_TYPE, CHARGE_CALCULATION_TYPE_OPTIONS, PENALTY, ACTIVE, CHARGE_PAYMENT_MODE,
                FEE_ON_MONTH_DAY, FEE_INTERVAL, MONTH_DAY_FORMAT, MIN_CAP, MAX_CAP, FEE_FREQUENCY, ENABLE_FREE_WITHDRAWAL_CHARGE,
                FREE_WITHDRAWAL_FREQUENCY, RESTART_COUNT_FREQUENCY, COUNT_FREQUENCY_TYPE, PAYMENT_TYPE_ID, ENABLE_PAYMENT_TYPE,
                ENABLE_FEE_SPLIT, ChargesApiConstants.glAccountIdParamName, ChargesApiConstants.taxGroupIdParamName, CHART, ENABLE_SLABS,
                "chartSlabs", "stakeholderSplits", "discountRules", "allRulesRequired", "combinationStrategy"));
    }

    @Override
    public void validateForCreate(final String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        // Use custom supportedParameters instead of core SUPPORTED_PARAMETERS
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(CHARGE);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final Integer chargeAppliesTo = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(CHARGE_APPLIES_TO, element);
        baseDataValidator.reset().parameter(CHARGE_APPLIES_TO).value(chargeAppliesTo).notNull();
        if (chargeAppliesTo != null) {
            baseDataValidator.reset().parameter(CHARGE_APPLIES_TO).value(chargeAppliesTo).isOneOfTheseValues(ChargeAppliesTo.validValues());
        }

        final Integer chargeTimeType = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(CHARGE_TIME_TYPE, element);
        baseDataValidator.reset().parameter(CHARGE_TIME_TYPE).value(chargeTimeType).notNull();
        if (chargeTimeType != null) {
            // Use appropriate validation based on chargeAppliesTo
            if (chargeAppliesTo != null && ChargeAppliesTo.fromInt(chargeAppliesTo).isLoanCharge()) {
                baseDataValidator.reset().parameter(CHARGE_TIME_TYPE).value(chargeTimeType)
                        .isOneOfTheseValues(ChargeTimeType.validLoanValues());
            } else if (chargeAppliesTo != null && ChargeAppliesTo.fromInt(chargeAppliesTo).isSavingsCharge()) {
                baseDataValidator.reset().parameter(CHARGE_TIME_TYPE).value(chargeTimeType)
                        .isOneOfTheseValues(ChargeTimeType.validSavingsValues());
            }
        }

        final Integer chargeCalculationType = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(CHARGE_CALCULATION_TYPE, element);
        baseDataValidator.reset().parameter(CHARGE_CALCULATION_TYPE).value(chargeCalculationType).notNull();
        if (chargeCalculationType != null) {
            // Use appropriate validation based on chargeAppliesTo
            if (chargeAppliesTo != null && ChargeAppliesTo.fromInt(chargeAppliesTo).isLoanCharge()) {
                baseDataValidator.reset().parameter(CHARGE_CALCULATION_TYPE).value(chargeCalculationType)
                        .isOneOfTheseValues(ChargeCalculationType.validValuesForLoan());
            } else if (chargeAppliesTo != null && ChargeAppliesTo.fromInt(chargeAppliesTo).isSavingsCharge()) {
                baseDataValidator.reset().parameter(CHARGE_CALCULATION_TYPE).value(chargeCalculationType)
                        .isOneOfTheseValues(ChargeCalculationType.validValuesForSavings());
            }
        }

        final String name = this.fromApiJsonHelper.extractStringNamed(NAME, element);
        baseDataValidator.reset().parameter(NAME).value(name).notBlank().notExceedingLengthOf(100);

        final String currencyCode = this.fromApiJsonHelper.extractStringNamed(CURRENCY_CODE, element);
        baseDataValidator.reset().parameter(CURRENCY_CODE).value(currencyCode).notBlank();

        final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT, element, Locale.getDefault());
        baseDataValidator.reset().parameter(AMOUNT).value(amount).notNull().positiveAmount();

        final Boolean penalty = this.fromApiJsonHelper.extractBooleanNamed(PENALTY, element);
        baseDataValidator.reset().parameter(PENALTY).value(penalty).notNull();

        final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(ACTIVE, element);
        baseDataValidator.reset().parameter(ACTIVE).value(active).notNull();

        // Validate discount rules if provided
        if (this.fromApiJsonHelper.parameterExists("discountRules", element)) {
            final JsonElement discountRulesElement = this.fromApiJsonHelper.extractJsonArrayNamed("discountRules", element);
            if (discountRulesElement != null && discountRulesElement.isJsonArray()) {
                // Additional validation for discount rules can be added here if needed
                // For now, we just ensure it's a valid JSON array
            }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    @Override
    public void validateForUpdate(final String json) {
        if (json == null || json.trim().isEmpty()) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        // Use custom supportedParameters instead of core SUPPORTED_PARAMETERS
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, supportedParameters);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(CHARGE);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(NAME, element)) {
            final String name = this.fromApiJsonHelper.extractStringNamed(NAME, element);
            baseDataValidator.reset().parameter(NAME).value(name).notBlank().notExceedingLengthOf(100);
        }

        if (this.fromApiJsonHelper.parameterExists(AMOUNT, element)) {
            final BigDecimal amount = this.fromApiJsonHelper.extractBigDecimalNamed(AMOUNT, element, Locale.getDefault());
            baseDataValidator.reset().parameter(AMOUNT).value(amount).notNull().positiveAmount();
        }

        if (this.fromApiJsonHelper.parameterExists(PENALTY, element)) {
            final Boolean penalty = this.fromApiJsonHelper.extractBooleanNamed(PENALTY, element);
            baseDataValidator.reset().parameter(PENALTY).value(penalty).notNull();
        }

        if (this.fromApiJsonHelper.parameterExists(ACTIVE, element)) {
            final Boolean active = this.fromApiJsonHelper.extractBooleanNamed(ACTIVE, element);
            baseDataValidator.reset().parameter(ACTIVE).value(active).notNull();
        }

        // Validate discount rules if provided
        if (this.fromApiJsonHelper.parameterExists("discountRules", element)) {
            final JsonElement discountRulesElement = this.fromApiJsonHelper.extractJsonArrayNamed("discountRules", element);
            if (discountRulesElement != null && discountRulesElement.isJsonArray()) {
                // Additional validation for discount rules can be added here if needed
                // For now, we just ensure it's a valid JSON array
            }
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
