/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.paystack.fineract.portfolio.tax.serialization;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.tax.api.TaxApiConstants;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class PaystackTaxValidator {

    public static final String DATE_FORMAT = "dateFormat";
    public static final String LOCALE = "locale";
    public static final String TAX_COMPONENT = "tax.component";

    private static final Set<String> SUPPORTED_TAX_COMPONENT_UPDATE_PARAMETERS = new HashSet<>(Arrays.asList(DATE_FORMAT, LOCALE,
            TaxApiConstants.nameParamName, TaxApiConstants.percentageParamName, TaxApiConstants.startDateParamName,
            TaxApiConstants.creditAccountTypeParamName, TaxApiConstants.creditAccountIdParamName));

    private final FromJsonHelper fromApiJsonHelper;

    public PaystackTaxValidator(final FromJsonHelper fromApiJsonHelper) {
        this.fromApiJsonHelper = fromApiJsonHelper;
    }

    public void validateForTaxComponentUpdate(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED_TAX_COMPONENT_UPDATE_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(TAX_COMPONENT);

        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(TaxApiConstants.nameParamName, element)) {
            final String name = this.fromApiJsonHelper.extractStringNamed(TaxApiConstants.nameParamName, element);
            baseDataValidator.reset().parameter(TaxApiConstants.nameParamName).value(name).notBlank();
        }

        if (this.fromApiJsonHelper.parameterExists(TaxApiConstants.percentageParamName, element)) {
            final BigDecimal percentage = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(TaxApiConstants.percentageParamName,
                    element);
            baseDataValidator.reset().parameter(TaxApiConstants.percentageParamName).value(percentage).notBlank().positiveAmount()
                    .notGreaterThanMax(BigDecimal.valueOf(100));
        }

        if (this.fromApiJsonHelper.parameterExists(TaxApiConstants.startDateParamName, element)) {
            final LocalDate startDate = this.fromApiJsonHelper.extractLocalDateNamed(TaxApiConstants.startDateParamName, element);
            baseDataValidator.reset().parameter(TaxApiConstants.startDateParamName).value(startDate)
                    .validateDateAfter(DateUtils.getBusinessLocalDate());
        }

        if (this.fromApiJsonHelper.parameterExists(TaxApiConstants.creditAccountTypeParamName, element)) {
            final Integer creditAccountType = this.fromApiJsonHelper
                    .extractIntegerSansLocaleNamed(TaxApiConstants.creditAccountTypeParamName, element);
            baseDataValidator.reset().parameter(TaxApiConstants.creditAccountTypeParamName).value(creditAccountType).notBlank()
                    .isOneOfTheseValues(GLAccountType.ASSET.getValue(), GLAccountType.LIABILITY.getValue(), GLAccountType.EQUITY.getValue(),
                            GLAccountType.INCOME.getValue(), GLAccountType.EXPENSE.getValue());
        }

        if (this.fromApiJsonHelper.parameterExists(TaxApiConstants.creditAccountIdParamName, element)) {
            final Long creditAccountId = this.fromApiJsonHelper.extractLongNamed(TaxApiConstants.creditAccountIdParamName, element);
            baseDataValidator.reset().parameter(TaxApiConstants.creditAccountIdParamName).value(creditAccountId).longGreaterThanZero();
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist",
                    dataValidationErrors);
        }
    }
}
