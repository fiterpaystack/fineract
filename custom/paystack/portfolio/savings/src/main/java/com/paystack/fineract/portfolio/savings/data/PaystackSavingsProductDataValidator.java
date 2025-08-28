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

    // locally keep reference to helper (private in superclass)
    private final FromJsonHelper paystackFromJsonHelper;

    public PaystackSavingsProductDataValidator(FromJsonHelper fromApiJsonHelper,
            SavingsProductAccountingDataValidator savingsProductAccountingDataValidator) {
        super(fromApiJsonHelper, savingsProductAccountingDataValidator);
        this.paystackFromJsonHelper = fromApiJsonHelper;
    }

    @PostConstruct
    public void init() {
        // Extend supported parameters with EMT Levy custom fields
        SAVINGS_PRODUCT_REQUEST_DATA_PARAMETERS
                .addAll(Arrays.asList("isEmtLevyApplicable", "emtLevyAmount", "emtLevyThreshold", "overrideGlobalEmtLevySetting"));

    }

    @Override
    public void validateForCreate(String json) {
        // run base validations first
        super.validateForCreate(json);
        // then run EMT levy specific validations
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
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("savingsproduct.emtlevy");

        // isEmtLevyApplicable
        Boolean isEmtLevyApplicable = null;
        if (this.paystackFromJsonHelper.parameterExists("isEmtLevyApplicable", element)) {
            isEmtLevyApplicable = this.paystackFromJsonHelper.extractBooleanNamed("isEmtLevyApplicable", element);
            baseDataValidator.reset().parameter("isEmtLevyApplicable").value(isEmtLevyApplicable).ignoreIfNull().validateForBooleanValue();
        }

        // overrideGlobalEmtLevySetting
        Boolean overrideGlobal = null;
        if (this.paystackFromJsonHelper.parameterExists("overrideGlobalEmtLevySetting", element)) {
            overrideGlobal = this.paystackFromJsonHelper.extractBooleanNamed("overrideGlobalEmtLevySetting", element);
            baseDataValidator.reset().parameter("overrideGlobalEmtLevySetting").value(overrideGlobal).ignoreIfNull()
                    .validateForBooleanValue();
        }

        // emtLevyAmount
        BigDecimal levyAmount = null;
        if (this.paystackFromJsonHelper.parameterExists("emtLevyAmount", element)) {
            levyAmount = this.paystackFromJsonHelper.extractBigDecimalWithLocaleNamed("emtLevyAmount", element);
            baseDataValidator.reset().parameter("emtLevyAmount").value(levyAmount).ignoreIfNull().zeroOrPositiveAmount();
        }

        // emtLevyThreshold
        BigDecimal levyThreshold = null;
        if (this.paystackFromJsonHelper.parameterExists("emtLevyThreshold", element)) {
            levyThreshold = this.paystackFromJsonHelper.extractBigDecimalWithLocaleNamed("emtLevyThreshold", element);
            baseDataValidator.reset().parameter("emtLevyThreshold").value(levyThreshold).ignoreIfNull().zeroOrPositiveAmount();
        }

        // Conditional requirements
        if (Boolean.TRUE.equals(isEmtLevyApplicable) && Boolean.TRUE.equals(overrideGlobal)) {
            // levy amount required when levy is applicable or global override requested
            baseDataValidator.reset().parameter("emtLevyAmount").value(levyAmount).notNull().zeroOrPositiveAmount();
        }

        if (Boolean.TRUE.equals(overrideGlobal)) {
            // when overriding global setting we expect explicit applicability flag
            baseDataValidator.reset().parameter("isEmtLevyApplicable").value(isEmtLevyApplicable).notNull();
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
