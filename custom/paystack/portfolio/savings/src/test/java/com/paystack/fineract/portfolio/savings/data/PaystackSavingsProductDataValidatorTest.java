package com.paystack.fineract.portfolio.savings.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.data.SavingsProductAccountingDataValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests EMT levy validation logic in PaystackSavingsProductDataValidator
 */
class PaystackSavingsProductDataValidatorTest {

    private FromJsonHelper fromJsonHelper; // real instance for parsing
    private SavingsProductAccountingDataValidator accountingValidator; // mocked to bypass accounting field validations
    private PaystackSavingsProductDataValidator validator;

    private static final String BASE_JSON_PREFIX = "{\n" + "  \"name\": \"ProdA\",\n" + "  \"shortName\": \"PA\",\n"
            + "  \"currencyCode\": \"USD\",\n" + "  \"digitsAfterDecimal\": 2,\n" + "  \"nominalAnnualInterestRate\": 5,\n"
            + "  \"interestCompoundingPeriodType\": 4,\n" + "  \"interestPostingPeriodType\": 4,\n" + "  \"interestCalculationType\": 1,\n"
            + "  \"interestCalculationDaysInYearType\": 365,\n" + "  \"accountingRule\": 1"; // close JSON body later

    @BeforeEach
    void setUp() {
        fromJsonHelper = new FromJsonHelper();
        accountingValidator = mock(SavingsProductAccountingDataValidator.class); // no behavior needed
        validator = new PaystackSavingsProductDataValidator(fromJsonHelper, accountingValidator);
        validator.init(); // ensure custom params added to supported set
    }

    private String baseJsonWith(String emtFragment) {
        String suffix = emtFragment == null || emtFragment.isBlank() ? "\n}" : ",\n" + emtFragment + "\n}";
        return BASE_JSON_PREFIX + suffix;
    }

    @Test
    void create_valid_whenLevyApplicableWithAmountOnly() {
        String json = baseJsonWith("\"isEmtLevyApplicable\": true,\n  \"emtLevyAmount\": 10");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    @Test
    void create_valid_whenOverrideGlobalFalseWithThresholdProvided() {
        String json = baseJsonWith("\"isEmtLevyApplicable\": true,\n  \"emtLevyAmount\": 10,\n  \"emtLevyThreshold\": 5");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    @Test
    void create_invalid_whenOverrideGlobalTrueMissingApplicability() {
        String json = baseJsonWith("\"overrideGlobalEmtLevySetting\": true,\n  \"emtLevyAmount\": 7");
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForCreate(json));
        assertEquals("validation.msg.savingsproduct.emtlevy.isEmtLevyApplicable.cannot.be.blank",
                ex.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void create_valid_whenOverrideGlobalTrueApplicabilityFalseWithAmount() {
        String json = baseJsonWith("\"overrideGlobalEmtLevySetting\": true,\n  \"isEmtLevyApplicable\": false,\n  \"emtLevyAmount\": 0");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    // Update tests

    @Test
    void update_invalid_whenMakingLevyApplicableWithoutAmount() {
        // Only EMT fields provided, mock product for base update context
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1); // cash based
        when(product.isDormancyTrackingActive()).thenReturn(false);
        String json = "{\n  \"isEmtLevyApplicable\": true,\n\"overrideGlobalEmtLevySetting\": true}"; // minimal update
                                                                                                      // JSON
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json, product));
        assertEquals("validation.msg.savingsproduct.emtlevy.emtLevyAmount.cannot.be.blank",
                ex.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void update_valid_whenSettingAmountOnlyWithoutChangingApplicability() {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1);
        when(product.isDormancyTrackingActive()).thenReturn(false);
        String json = "{\n  \"emtLevyAmount\": 25\n}"; // amount alone should be fine
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product));
    }

    @Test
    void update_invalid_whenOverrideGlobalTrueMissingApplicability() {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1);
        when(product.isDormancyTrackingActive()).thenReturn(false);
        String json = "{\n  \"overrideGlobalEmtLevySetting\": true,\n  \"emtLevyAmount\": 5\n}";
        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate(json, product));
        assertEquals("validation.msg.savingsproduct.emtlevy.isEmtLevyApplicable.cannot.be.blank",
                ex.getErrors().get(0).getUserMessageGlobalisationCode());
    }

    @Test
    void update_valid_whenOverrideGlobalTrueApplicabilityFalseAmountProvided() {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1);
        when(product.isDormancyTrackingActive()).thenReturn(false);
        String json = "{\n  \"overrideGlobalEmtLevySetting\": true,\n  \"isEmtLevyApplicable\": false,\n  \"emtLevyAmount\": 3\n}";
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product));
    }
}
