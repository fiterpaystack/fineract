package com.paystack.fineract.portfolio.savings.data;

import com.google.gson.JsonElement;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.data.SavingsProductAccountingDataValidator;
import org.apache.fineract.portfolio.savings.data.SavingsProductDataValidator;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class PaystackSavingsProductDataValidator extends SavingsProductDataValidator {

    private final FromJsonHelper paystackFromJsonHelper;

    public PaystackSavingsProductDataValidator(FromJsonHelper fromApiJsonHelper,
            SavingsProductAccountingDataValidator savingsProductAccountingDataValidator) {
        super(fromApiJsonHelper, savingsProductAccountingDataValidator);
        this.paystackFromJsonHelper = fromApiJsonHelper;
    }

    @PostConstruct
    public void init() {
        SAVINGS_PRODUCT_REQUEST_DATA_PARAMETERS.addAll(Arrays.asList(
                PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT,
                PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW,
                PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT, PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD,
                PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY));
    }

    @Override
    public void validateForCreate(String json) {
        super.validateForCreate(json);
        validateEmtLevyParams(json);
    }

    @Override
    public void validateForUpdate(String json, SavingsProduct product) {
        super.validateForUpdate(json, product);
        validateEmtLevyParams(json);
    }

    private void validateEmtLevyParams(String json) {
        final JsonElement element = this.paystackFromJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder base = new DataValidatorBuilder(dataValidationErrors).resource("savingsproduct.emtlevy");

        // Flags: applicability for deposit & withdraw
        Boolean levyOnDeposit = null;
        if (paystackFromJsonHelper.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT, element)) {
            levyOnDeposit = paystackFromJsonHelper
                    .extractBooleanNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT, element);
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_DEPOSIT).value(levyOnDeposit)
                    .ignoreIfNull().validateForBooleanValue();
        }
        Boolean levyOnWithdraw = null;
        if (paystackFromJsonHelper.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW, element)) {
            levyOnWithdraw = paystackFromJsonHelper
                    .extractBooleanNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW, element);
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_APPLICABLE_FOR_WITHDRAW).value(levyOnWithdraw)
                    .ignoreIfNull().validateForBooleanValue();
        }

        // Override global flag
        Boolean overrideGlobal = null;
        if (paystackFromJsonHelper.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY, element)) {
            overrideGlobal = paystackFromJsonHelper.extractBooleanNamed(PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY,
                    element);
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_OVERRIDE_GLOBAL_LEVY).value(overrideGlobal).ignoreIfNull()
                    .validateForBooleanValue();
        }

        // Amount & threshold
        BigDecimal levyAmount = null;
        if (paystackFromJsonHelper.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT, element)) {
            levyAmount = paystackFromJsonHelper.extractBigDecimalWithLocaleNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT,
                    element);
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT).value(levyAmount).ignoreIfNull()
                    .zeroOrPositiveAmount();
        }
        BigDecimal levyThreshold = null;
        if (paystackFromJsonHelper.parameterExists(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD, element)) {
            levyThreshold = paystackFromJsonHelper
                    .extractBigDecimalWithLocaleNamed(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD, element);
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_THRESHOLD).value(levyThreshold).ignoreIfNull()
                    .zeroOrPositiveAmount();
        }

        // If any applicability flag is true, levy amount must be supplied (and non-negative validated earlier)
        if (Boolean.TRUE.equals(overrideGlobal)) {
            base.reset().parameter(PaystackSavingsProductAdditionalAttributes.EMT_LEVY_AMOUNT).value(levyAmount).notNull();
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
