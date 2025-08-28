package com.paystack.fineract.portfolio.savings.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.data.SavingsProductAccountingDataValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaystackSavingsProductDataValidatorTest {

    private FromJsonHelper fromJsonHelper;
    private SavingsProductAccountingDataValidator accountingValidator;
    private PaystackSavingsProductDataValidator validator;

    private static final String BASE_JSON_PREFIX = "{\n" + "  \"name\": \"ProdA\",\n" + "  \"shortName\": \"PA\",\n"
            + "  \"currencyCode\": \"USD\",\n" + "  \"digitsAfterDecimal\": 2,\n" + "  \"nominalAnnualInterestRate\": 5,\n"
            + "  \"interestCompoundingPeriodType\": 4,\n" + "  \"interestPostingPeriodType\": 4,\n" + "  \"interestCalculationType\": 1,\n"
            + "  \"interestCalculationDaysInYearType\": 365,\n" + "  \"accountingRule\": 1"; // JSON body will be closed
                                                                                             // later

    @BeforeEach
    void setUp() {
        fromJsonHelper = new FromJsonHelper();
        accountingValidator = mock(SavingsProductAccountingDataValidator.class);
        validator = new PaystackSavingsProductDataValidator(fromJsonHelper, accountingValidator);
        validator.init();
    }

    private String baseJsonWith(String fragment) {
        String suffix = (fragment == null || fragment.isBlank()) ? "\n}" : ",\n" + fragment + "\n}";
        return BASE_JSON_PREFIX + suffix;
    }

    @Test
    void create_valid_whenDepositFlagTrueAndAmountProvided() {
        String json = baseJsonWith(
                "\"isEmtLevyApplicableForDeposit\": true,\n  \"isEmtLevyApplicableForWithdraw\": false,\n  \"emtLevyAmount\": 10");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    @Test
    void create_valid_whenWithdrawFlagTrueAndAmountProvided() {
        String json = baseJsonWith(
                "\"isEmtLevyApplicableForDeposit\": false,\n  \"isEmtLevyApplicableForWithdraw\": true,\n  \"emtLevyAmount\": 5");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    @Test
    void create_valid_whenThresholdEqualsAmount() {
        String json = baseJsonWith(
                "\"isEmtLevyApplicableForDeposit\": true,\n  \"isEmtLevyApplicableForWithdraw\": true,\n  \"emtLevyAmount\": 5,\n  \"emtLevyThreshold\": 5");
        assertDoesNotThrow(() -> validator.validateForCreate(json));
    }

    // Update tests

    private SavingsProduct mockBaseProduct() {
        SavingsProduct product = mock(SavingsProduct.class);
        when(product.getAccountingType()).thenReturn(1); // cash based
        when(product.isDormancyTrackingActive()).thenReturn(false);
        return product;
    }

    @Test
    void update_valid_whenSettingAmountOnly() {
        SavingsProduct product = mockBaseProduct();
        String json = "{\n  \"emtLevyAmount\": 12\n}";
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product));
    }

    @Test
    void update_valid_whenFlagsFalseAndAmountProvided() {
        SavingsProduct product = mockBaseProduct();
        String json = "{\n  \"isEmtLevyApplicableForDeposit\": false,\n  \"isEmtLevyApplicableForWithdraw\": false,\n  \"emtLevyAmount\": 0\n}";
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product));
    }

    @Test
    void update_valid_whenOverrideGlobalTrueBothFlagsProvidedAndAmount() {
        SavingsProduct product = mockBaseProduct();
        String json = "{\n  \"overrideGlobalEmtLevySetting\": true,\n  \"isEmtLevyApplicableForDeposit\": true,\n  \"isEmtLevyApplicableForWithdraw\": false,\n  \"emtLevyAmount\": 2\n}";
        assertDoesNotThrow(() -> validator.validateForUpdate(json, product));
    }

}
