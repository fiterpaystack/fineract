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
package com.paystack.fineract.tier.service.service;

import com.paystack.fineract.tier.service.api.SavingsAccountTransactionLimitApiConstant;
import com.paystack.fineract.tier.service.data.SavingsAccountTransactionLimitSettingDataValidator;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSettingRepository;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationMappingRepository;
import com.paystack.fineract.tier.service.domain.SavingsClientClassificationLimitMapping;
import com.paystack.fineract.tier.service.domain.TransactionLimits;
import com.paystack.fineract.tier.service.domain.SavingsAccountGlobalTransactionLimitSetting;
import com.paystack.fineract.tier.service.exception.GlobalSavingsAccountTransactionLimitSettingException;
import com.paystack.fineract.tier.service.exception.SavingsAccountTransactionLimitSettingNotFoundException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class SavingsAccountTransactionLimitWritePlatformServiceImpl implements SavingsAccountTransactionLimitWritePlatformService {

    private final SavingsAccountTransactionLimitSettingDataValidator savingsAccountTransactionLimitSettingDataValidator;
    private final SavingsAccountGlobalTransactionLimitSettingRepository savingsAccountTransactionLimitsSettingRepository;
    private final SavingsClientClassificationMappingRepository savingsClientClassificationMappingRepository;
    private final CodeValueRepositoryWrapper codeValueRepositoryWrapper;

    @Override
    public CommandProcessingResult create(JsonCommand command) {

        savingsAccountTransactionLimitSettingDataValidator.validateForCreate(command.json());
        SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting = SavingsAccountGlobalTransactionLimitSetting
                .fromJson(command);

        if (savingsAccountGlobalTransactionLimitSetting.getIsGlobalLimit() != null
                && savingsAccountGlobalTransactionLimitSetting.getIsGlobalLimit()) {
            updateExistingGlobalLimitValues(savingsAccountGlobalTransactionLimitSetting,
                    savingsAccountGlobalTransactionLimitSetting.getIsMerchantLimit());
        }

        SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSettingResponse = savingsAccountTransactionLimitsSettingRepository
                .saveAndFlush(savingsAccountGlobalTransactionLimitSetting);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId())
                .withEntityId(savingsAccountGlobalTransactionLimitSettingResponse.getId()).build();
    }

    private void updateCurrentlyActiveGlobalLimitToInactive(SavingsAccountGlobalTransactionLimitSetting currentGlobalLimit,
            Boolean isMerchantLimit) {

        if (currentGlobalLimit != null && currentGlobalLimit.getIsGlobalLimit() != null && currentGlobalLimit.getIsGlobalLimit()) {
            currentGlobalLimit.setIsGlobalLimit(false);
            currentGlobalLimit.setIsMerchantLimit(false);
        } else {
            Optional<SavingsAccountGlobalTransactionLimitSetting> savingsAccountGlobalTransactionLimitSetting = savingsAccountTransactionLimitsSettingRepository
                    .findByIsGlobalLimitAndIsActiveAndIsMerchantLimit(true, true, isMerchantLimit);

            if (savingsAccountGlobalTransactionLimitSetting.isPresent()) {

                savingsAccountGlobalTransactionLimitSetting.get().setIsGlobalLimit(false);
                savingsAccountGlobalTransactionLimitSetting.get().setIsMerchantLimit(false);
                savingsAccountTransactionLimitsSettingRepository.save(savingsAccountGlobalTransactionLimitSetting.get());
            }
        }
    }

    private void updateExistingGlobalLimitValues(SavingsAccountGlobalTransactionLimitSetting currentGlobalLimit, Boolean isMerchantLimit) {

        Optional<SavingsAccountGlobalTransactionLimitSetting> savingsAccountGlobalTransactionLimitSetting = savingsAccountTransactionLimitsSettingRepository
                .findByIsGlobalLimitAndIsActiveAndIsMerchantLimit(true, true, isMerchantLimit);

        if (savingsAccountGlobalTransactionLimitSetting.isPresent()) {

            savingsAccountGlobalTransactionLimitSetting.get().setIsGlobalLimit(false);
            savingsAccountGlobalTransactionLimitSetting.get().setIsMerchantLimit(false);
            savingsAccountTransactionLimitsSettingRepository.save(savingsAccountGlobalTransactionLimitSetting.get());
        }

        if (currentGlobalLimit != null) {
            currentGlobalLimit.setIsGlobalLimit(true);
            currentGlobalLimit.setIsMerchantLimit(isMerchantLimit);
        }
    }

    @Override
    public CommandProcessingResult update(Long transactionLimitId, JsonCommand command) {

        savingsAccountTransactionLimitSettingDataValidator.validateForCreate(command.json());

        SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting = savingsAccountTransactionLimitsSettingRepository
                .findById(transactionLimitId)
                .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

        boolean currentGlobalLimitSetting = savingsAccountGlobalTransactionLimitSetting.getIsGlobalLimit();
        boolean currentMerchantLimitSetting = savingsAccountGlobalTransactionLimitSetting.getIsMerchantLimit();

        if (command.parameterExists(SavingsAccountTransactionLimitApiConstant.IS_GLOBAL_LIMIT_PARAM_NAME)
                && command.isChangeInBooleanParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_GLOBAL_LIMIT_PARAM_NAME,
                        currentGlobalLimitSetting)) {

            Boolean merchantLimit = command
                    .booleanPrimitiveValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_MERCHANT_LIMIT_PARAM_NAME);

            if (!command.booleanPrimitiveValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_GLOBAL_LIMIT_PARAM_NAME)) {
                updateCurrentlyActiveGlobalLimitToInactive(savingsAccountGlobalTransactionLimitSetting, merchantLimit);
            } else {
                updateExistingGlobalLimitValues(savingsAccountGlobalTransactionLimitSetting, merchantLimit);
            }
        }

        if (command.parameterExists(SavingsAccountTransactionLimitApiConstant.IS_MERCHANT_LIMIT_PARAM_NAME)
                && command.isChangeInBooleanParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_MERCHANT_LIMIT_PARAM_NAME,
                        currentMerchantLimitSetting)) {

            boolean merchantLimit = command
                    .booleanPrimitiveValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_MERCHANT_LIMIT_PARAM_NAME);

            if (merchantLimit) {
                updateExistingGlobalLimitValues(savingsAccountGlobalTransactionLimitSetting, true);
            } else {
                updateCurrentlyActiveGlobalLimitToInactive(savingsAccountGlobalTransactionLimitSetting, false);
            }
        }

        if (command.parameterExists(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME)
                && !command.booleanPrimitiveValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME)
                && savingsAccountGlobalTransactionLimitSetting.getIsGlobalLimit()) {

            throw new GlobalSavingsAccountTransactionLimitSettingException(
                    "Global transaction limit setting cannot be inactive at any time");
        }

        final Map<String, Object> changes = new LinkedHashMap<>(9);

        if (command.isChangeInStringParameterNamed(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME,
                savingsAccountGlobalTransactionLimitSetting.getName())) {
            final String newValue = command.stringValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME, newValue);
            savingsAccountGlobalTransactionLimitSetting.setName(newValue);
        }

        if (command.isChangeInStringParameterNamed(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME,
                savingsAccountGlobalTransactionLimitSetting.getDescription())) {
            final String newValue = command.stringValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.DESCRIPTION_PARAM_NAME, newValue);
            savingsAccountGlobalTransactionLimitSetting.setDescription(newValue);
        }

        if (command.isChangeInBooleanParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME,
                savingsAccountGlobalTransactionLimitSetting.getIsActive())) {
            final Boolean newValue = command
                    .booleanObjectValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.IS_ACTIVE_PARAM_NAME, newValue);
            savingsAccountGlobalTransactionLimitSetting.setIsActive(newValue);

        }

        final TransactionLimits limits = savingsAccountGlobalTransactionLimitSetting.getTransactionLimits();

        if (command.isChangeInBigDecimalParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME,
                limits.getMaxDailyWithdrawalAmount())) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME, newValue);
            limits.setMaxDailyWithdrawalAmount(newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME,
                limits.getMaxSingleDepositAmount())) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME, newValue);
            limits.setMaxSingleDepositAmount(newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME,
                limits.getMaxSingleWithdrawalAmount())) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME, newValue);
            limits.setMaxSingleWithdrawalAmount(newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_ON_HOLD_AMOUNT_PARAM_NAME,
                limits.getMaxOnHoldAmount())) {
            final BigDecimal newValue = command
                    .bigDecimalValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.MAX_ON_HOLD_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_ON_HOLD_AMOUNT_PARAM_NAME, newValue);
            limits.setMaxOnHoldAmount(newValue);
        }

        savingsAccountGlobalTransactionLimitSetting.setTransactionLimits(limits);

        if (command.isChangeInBigDecimalParameterNamed(
                SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME,
                limits.getMaxOnHoldAmount())) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(
                    SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME, newValue);
            savingsAccountGlobalTransactionLimitSetting.setMaxClientSpecificDailyWithdrawalAmount(newValue);
        }

        if (command.isChangeInBigDecimalParameterNamed(
                SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME,
                limits.getMaxOnHoldAmount())) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(
                    SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME);
            changes.put(SavingsAccountTransactionLimitApiConstant.MAX_CLIENT_SPECIFIC_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME, newValue);
            savingsAccountGlobalTransactionLimitSetting.setMaxClientSpecificSingleWithdrawalAmount(newValue);
        }

        savingsAccountTransactionLimitsSettingRepository.save(savingsAccountGlobalTransactionLimitSetting);

        return new CommandProcessingResultBuilder().withCommandId(command.commandId())
                .withEntityId(savingsAccountGlobalTransactionLimitSetting.getId()).with(changes).build();
    }

    @Override
    public CommandProcessingResult createClassificationLimitMapping(JsonCommand jsonCommand) {

        if (!jsonCommand.parameterExists(SavingsAccountTransactionLimitApiConstant.LIMIT_ID_PARAM_NAME)
                || !jsonCommand.parameterExists(SavingsAccountTransactionLimitApiConstant.CLIENT_CLASSIFICATION_PARAM_NAME)) {
            throw new GlobalSavingsAccountTransactionLimitSettingException("Limit Id and Client Classification Id are required");
        }

        final Long transactionLimitId = jsonCommand
                .longValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.LIMIT_ID_PARAM_NAME);
        final Long classificationId = jsonCommand
                .longValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.CLIENT_CLASSIFICATION_PARAM_NAME);

        savingsClientClassificationMappingRepository.findByClassificationId(classificationId)
                .ifPresent(savingsClientClassificationLimitMapping -> {
                    throw new GlobalSavingsAccountTransactionLimitSettingException("Client Classification Limit Mapping already exists");
                });

        SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting = savingsAccountTransactionLimitsSettingRepository
                .findById(transactionLimitId)
                .orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(transactionLimitId));

        CodeValue codeValue = codeValueRepositoryWrapper.findOneWithNotFoundDetection(classificationId);

        if (!codeValue.getCode().getName().equalsIgnoreCase(SavingsAccountTransactionLimitApiConstant.CLASSIFICATION_CODE_NAME)) {
            throw new GlobalSavingsAccountTransactionLimitSettingException("Client Classification Code is not valid");
        }

        SavingsClientClassificationLimitMapping mapping = new SavingsClientClassificationLimitMapping(
                savingsAccountGlobalTransactionLimitSetting, codeValue);
        mapping = savingsClientClassificationMappingRepository.saveAndFlush(mapping);

        return new CommandProcessingResultBuilder().withCommandId(jsonCommand.commandId())
                .withEntityId(Objects.requireNonNull(mapping.getId()).longValue()).build();
    }

    @Override
    public CommandProcessingResult updateClassificationLimitMapping(Integer classificationMappingId, JsonCommand jsonCommand) {

        if (!jsonCommand.parameterExists(SavingsAccountTransactionLimitApiConstant.LIMIT_ID_PARAM_NAME)
                || !jsonCommand.parameterExists(SavingsAccountTransactionLimitApiConstant.CLIENT_CLASSIFICATION_PARAM_NAME)) {
            throw new GlobalSavingsAccountTransactionLimitSettingException("Limit Id and Client Classification Id are required");
        }

        SavingsClientClassificationLimitMapping mapping = savingsClientClassificationMappingRepository.findById(classificationMappingId)
                .orElseThrow(() -> new GlobalSavingsAccountTransactionLimitSettingException(
                        "Client Classification Limit Mapping does not exist"));

        Map<String, Object> changes = new HashMap<>();
        if (jsonCommand.isChangeInLongParameterNamed(SavingsAccountTransactionLimitApiConstant.LIMIT_ID_PARAM_NAME,
                mapping.getSavingsAccountGlobalTransactionLimitSetting().getId())) {

            final Long newValue = jsonCommand.longValueOfParameterNamed(SavingsAccountTransactionLimitApiConstant.LIMIT_ID_PARAM_NAME);

            // Check that it exists
            SavingsAccountGlobalTransactionLimitSetting savingsAccountGlobalTransactionLimitSetting = savingsAccountTransactionLimitsSettingRepository
                    .findById(newValue).orElseThrow(() -> new SavingsAccountTransactionLimitSettingNotFoundException(newValue));

            changes.put(SavingsAccountTransactionLimitApiConstant.NAME_PARAM_NAME, newValue);
            mapping.setSavingsAccountGlobalTransactionLimitSetting(savingsAccountGlobalTransactionLimitSetting);
        }

        return new CommandProcessingResultBuilder().withCommandId(jsonCommand.commandId()).withEntityId(classificationMappingId.longValue())
                .with(changes).build();
    }

}
