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

package com.paystack.fineract.tier.service.data;

import static org.apache.fineract.portfolio.savings.SavingsApiConstants.nameParamName;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import com.paystack.fineract.tier.service.api.SavingsAccountTransactionLimitApiConstant;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SavingsAccountTransactionLimitSettingDataValidator {

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json,
                SavingsAccountTransactionLimitApiConstant.CREATE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SavingsAccountTransactionLimitApiConstant.RESOURCE_NAME);
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        final String name = this.fromApiJsonHelper.extractStringNamed(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME, element);
        baseDataValidator.reset().parameter(nameParamName).value(name).notBlank().notExceedingLengthOf(255);

        final BigDecimal maxSingleDepositAmount = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME, element);
        baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME)
                .value(maxSingleDepositAmount).notNull().zeroOrPositiveAmount();

        final BigDecimal balanceCumulative = this.fromApiJsonHelper
                .extractBigDecimalWithLocaleNamed(SavingsAccountTransactionLimitApiConstant.BALANCE_CUMULATIVE_PARAM_NAME, element);
        baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.BALANCE_CUMULATIVE_PARAM_NAME)
                .value(balanceCumulative).notNull().zeroOrPositiveAmount();

        final Boolean isActive = this.fromApiJsonHelper.extractBooleanNamed(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME,
                element);
        baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME).value(isActive).notNull()
                .validateForBooleanValue();

        final String description = this.fromApiJsonHelper
                .extractStringNamed(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME, element);
        baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME).value(description)
                .ignoreIfNull().notExceedingLengthOf(1000);

        throwExceptionIfValidationWarningsExist(dataValidationErrors);
    }

    public void validateForUpdate(String json) {

        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json,
                SavingsAccountTransactionLimitApiConstant.CREATE_REQUEST_DATA_PARAMETERS);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SavingsAccountTransactionLimitApiConstant.RESOURCE_NAME);
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (fromApiJsonHelper.parameterExists(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME, element)) {
            final String name = this.fromApiJsonHelper.extractStringNamed(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME,
                    element);
            baseDataValidator.reset().parameter(nameParamName).value(name).notBlank().notExceedingLengthOf(255);
        }

        if (fromApiJsonHelper.parameterExists(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME, element)) {
            final BigDecimal maxSingleDepositAmount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(
                    SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME, element);
            baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME)
                    .value(maxSingleDepositAmount).notNull().zeroOrPositiveAmount();
        }

        if (fromApiJsonHelper.parameterExists(SavingsAccountTransactionLimitApiConstant.BALANCE_CUMULATIVE_PARAM_NAME, element)) {
            final BigDecimal balanceCumulative = this.fromApiJsonHelper
                    .extractBigDecimalWithLocaleNamed(SavingsAccountTransactionLimitApiConstant.BALANCE_CUMULATIVE_PARAM_NAME, element);
            baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.BALANCE_CUMULATIVE_PARAM_NAME)
                    .value(balanceCumulative).notNull().zeroOrPositiveAmount();
        }

        if (fromApiJsonHelper.parameterExists(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME, element)) {
            final Boolean isActive = this.fromApiJsonHelper
                    .extractBooleanNamed(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME, element);
            baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME).value(isActive).notNull()
                    .validateForBooleanValue();
        }

        if (fromApiJsonHelper.parameterExists(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME, element)) {
            final String description = this.fromApiJsonHelper
                    .extractStringNamed(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME, element);
            baseDataValidator.reset().parameter(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME).value(description)
                    .ignoreIfNull().notExceedingLengthOf(1000);
        }

        throwExceptionIfValidationWarningsExist(dataValidationErrors);

    }

    private void throwExceptionIfValidationWarningsExist(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

}
