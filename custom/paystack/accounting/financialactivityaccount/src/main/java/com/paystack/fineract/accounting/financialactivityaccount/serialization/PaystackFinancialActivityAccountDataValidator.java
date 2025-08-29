package com.paystack.fineract.accounting.financialactivityaccount.serialization;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.accounting.common.AccountingConstants;
import org.apache.fineract.accounting.financialactivityaccount.serialization.FinancialActivityAccountDataValidator;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class PaystackFinancialActivityAccountDataValidator extends FinancialActivityAccountDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    @Autowired
    public PaystackFinancialActivityAccountDataValidator(FromJsonHelper fromApiJsonHelper) {
        super(fromApiJsonHelper);
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    @Override
    public void validateForCreate(final String json) {
        validateJSONAndCheckForUnsupportedParams(json);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = getDataValidator(dataValidationErrors);

        final JsonElement element = fromApiJsonHelper.parse(json);

        final Integer financialActivityId = fromApiJsonHelper.extractIntegerSansLocaleNamed(paramNameForFinancialActivity, element);
        baseDataValidator.reset().parameter(paramNameForFinancialActivity).value(financialActivityId).notNull().isOneOfTheseValues(
                AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(),
                AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(),
                AccountingConstants.FinancialActivity.CASH_AT_MAINVAULT.getValue(),
                AccountingConstants.FinancialActivity.CASH_AT_TELLER.getValue(),
                AccountingConstants.FinancialActivity.OPENING_BALANCES_TRANSFER_CONTRA.getValue(),
                AccountingConstants.FinancialActivity.ASSET_FUND_SOURCE.getValue(),
                AccountingConstants.FinancialActivity.PAYABLE_DIVIDENDS.getValue(),
                AccountingConstants.FinancialActivity.EMT_LEVY.getValue());

        final Long glAccountId = this.fromApiJsonHelper.extractLongNamed(paramNameForGLAccount, element);
        baseDataValidator.reset().parameter(paramNameForGLAccount).value(glAccountId).notNull().integerGreaterThanZero();

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    @Override
    public void validateForUpdate(final String json) {
        validateJSONAndCheckForUnsupportedParams(json);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = getDataValidator(dataValidationErrors);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(paramNameForFinancialActivity, element)) {
            final Integer financialActivityId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(paramNameForFinancialActivity,
                    element);
            baseDataValidator.reset().parameter(paramNameForFinancialActivity).value(financialActivityId).ignoreIfNull().isOneOfTheseValues(
                    AccountingConstants.FinancialActivity.ASSET_TRANSFER.getValue(),
                    AccountingConstants.FinancialActivity.LIABILITY_TRANSFER.getValue(),
                    AccountingConstants.FinancialActivity.OPENING_BALANCES_TRANSFER_CONTRA.getValue(),
                    AccountingConstants.FinancialActivity.ASSET_FUND_SOURCE.getValue(),
                    AccountingConstants.FinancialActivity.PAYABLE_DIVIDENDS.getValue(),
                    AccountingConstants.FinancialActivity.EMT_LEVY.getValue());
        }

        if (this.fromApiJsonHelper.parameterExists(paramNameForGLAccount, element)) {
            final Long glAccountId = this.fromApiJsonHelper.extractLongNamed(paramNameForGLAccount, element);
            baseDataValidator.reset().parameter(paramNameForGLAccount).value(glAccountId).ignoreIfNull().integerGreaterThanZero();
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

}
