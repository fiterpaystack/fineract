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

package com.paystack.fineract.portfolio.account.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.savings.data.WithdrawalFrequencySettingData;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl {

    private final @Qualifier("savingsApplicationProcessWritePlatformService") SavingsApplicationProcessWritePlatformService delegate;
    private final AccountWithdrawalFrequencyService accountWithdrawalFrequencyService;

    @Transactional
    public CommandProcessingResult submitApplication(JsonCommand command) {
        CommandProcessingResult result = delegate.submitApplication(command);
        Long accountId = safeSavingsId(result);

        try {
            List<WithdrawalFrequencySettingData> settings = extractSettings(command);
            if (!settings.isEmpty()) {
                accountWithdrawalFrequencyService.createAccountSettings(accountId, settings);
            }
        } catch (Exception e) {
            log.error("Failed to apply withdrawal frequency settings on submit for account {}", accountId, e);
        }

        return result;
    }

    @Transactional
    public CommandProcessingResult modifyApplication(Long savingsId, JsonCommand command) {
        CommandProcessingResult result = delegate.modifyApplication(savingsId, command);
        Long accountId = safeSavingsId(result);

        try {
            List<WithdrawalFrequencySettingData> settings = extractSettings(command);
            // If array provided, upsert; if explicitly empty array provided, clear all
            if (settings.isEmpty() && hasSettingsArray(command)) {
                accountWithdrawalFrequencyService.removeAllAccountSettings(accountId);
            } else if (!settings.isEmpty()) {
                accountWithdrawalFrequencyService.createAccountSettings(accountId, settings);
            }
        } catch (Exception e) {
            log.error("Failed to apply withdrawal frequency settings on modify for account {}", accountId, e);
        }

        return result;
    }

    @Transactional
    public CommandProcessingResult deleteApplication(Long savingsId) {
        try {
            accountWithdrawalFrequencyService.removeAllAccountSettings(savingsId);
        } catch (Exception e) {
            log.warn("Failed to cleanup withdrawal frequency settings for account {} during delete", savingsId, e);
        }

        return delegate.deleteApplication(savingsId);
    }

    // Other methods delegate without customization
    public CommandProcessingResult approveApplication(Long savingsId, JsonCommand command) {
        return delegate.approveApplication(savingsId, command);
    }

    public CommandProcessingResult undoApplicationApproval(Long savingsId, JsonCommand command) {
        return delegate.undoApplicationApproval(savingsId, command);
    }

    public CommandProcessingResult rejectApplication(Long savingsId, JsonCommand command) {
        return delegate.rejectApplication(savingsId, command);
    }

    public CommandProcessingResult applicantWithdrawsFromApplication(Long savingsId, JsonCommand command) {
        return delegate.applicantWithdrawsFromApplication(savingsId, command);
    }

    public CommandProcessingResult createActiveApplication(org.apache.fineract.portfolio.savings.data.SavingsAccountDataDTO savingsAccountDataDTO, String noteText) {
        return delegate.createActiveApplication(savingsAccountDataDTO, noteText);
    }

    public CommandProcessingResult submitGSIMApplication(JsonCommand command) {
        return delegate.submitGSIMApplication(command);
    }

    public CommandProcessingResult approveGSIMApplication(Long gsimId, JsonCommand command) {
        return delegate.approveGSIMApplication(gsimId, command);
    }

    public CommandProcessingResult rejectGSIMApplication(Long gsimId, JsonCommand command) {
        return delegate.rejectGSIMApplication(gsimId, command);
    }

    public CommandProcessingResult undoGSIMApplicationApproval(Long gsimId, JsonCommand command) {
        return delegate.undoGSIMApplicationApproval(gsimId, command);
    }

    public CommandProcessingResult modifyGSIMApplication(Long gsimId, JsonCommand command) {
        return delegate.modifyGSIMApplication(gsimId, command);
    }

    private static Long safeSavingsId(CommandProcessingResult result) {
        return result.getSavingsId();
    }

    private static boolean hasSettingsArray(JsonCommand command) {
        try {
            JsonArray arr = command.arrayOfParameterNamed("withdrawalFrequencySettings");
            return arr != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<WithdrawalFrequencySettingData> extractSettings(JsonCommand command) {
        List<WithdrawalFrequencySettingData> settingsData = new ArrayList<>();
        try {
            if (command.parameterExists("withdrawalFrequencySettings")) {
                JsonArray settingsArray = command.arrayOfParameterNamed("withdrawalFrequencySettings");
                if (settingsArray != null) {
                    for (int i = 0; i < settingsArray.size(); i++) {
                        JsonObject settingObject = settingsArray.get(i).getAsJsonObject();
                        WithdrawalFrequencySettingData settingData = WithdrawalFrequencySettingData.fromJson(settingObject);
                        if (settingData != null && settingData.isValid()) {
                            settingsData.add(settingData);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If any parsing issue occurs, prefer to ignore and proceed
        }
        return settingsData;
    }
}


