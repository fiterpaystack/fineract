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
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.apache.fineract.portfolio.savings.data.SavingsAccountDataValidator;
import org.apache.fineract.portfolio.savings.domain.GSIMRepositoy;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.GroupSavingsIndividualMonitoringWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountApplicationTransitionApiJsonValidator;
import org.apache.fineract.portfolio.savings.service.SavingsAccountDomainService;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@Slf4j
public class PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl
        extends SavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl {

    private final AccountWithdrawalFrequencyService accountWithdrawalFrequencyService;
    private final PaystackAccountNameService paystackAccountNameService;

    public PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl(
            AccountWithdrawalFrequencyService accountWithdrawalFrequencyService, PaystackAccountNameService paystackAccountNameService,
            // Parent class dependencies
            PlatformSecurityContext context, SavingsAccountRepositoryWrapper savingAccountRepository,
            SavingsAccountAssembler savingAccountAssembler, SavingsAccountDataValidator savingsAccountDataValidator,
            AccountNumberGenerator accountNumberGenerator, ClientRepositoryWrapper clientRepository, GroupRepository groupRepository,
            SavingsProductRepository savingsProductRepository, NoteRepository noteRepository, StaffRepositoryWrapper staffRepository,
            SavingsAccountApplicationTransitionApiJsonValidator savingsAccountApplicationTransitionApiJsonValidator,
            SavingsAccountChargeAssembler savingsAccountChargeAssembler, CommandProcessingService commandProcessingService,
            SavingsAccountDomainService savingsAccountDomainService, SavingsAccountWritePlatformService savingsAccountWritePlatformService,
            AccountNumberFormatRepositoryWrapper accountNumberFormatRepository, BusinessEventNotifierService businessEventNotifierService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, GSIMRepositoy gsimRepository,
            GroupRepositoryWrapper groupRepositoryWrapper, GroupSavingsIndividualMonitoringWritePlatformService gsimWritePlatformService) {
        super(context, savingAccountRepository, savingAccountAssembler, savingsAccountDataValidator, accountNumberGenerator,
                clientRepository, groupRepository, savingsProductRepository, noteRepository, staffRepository,
                savingsAccountApplicationTransitionApiJsonValidator, savingsAccountChargeAssembler, commandProcessingService,
                savingsAccountDomainService, savingsAccountWritePlatformService, accountNumberFormatRepository,
                businessEventNotifierService, entityDatatableChecksWritePlatformService, gsimRepository, groupRepositoryWrapper,
                gsimWritePlatformService);
        this.accountWithdrawalFrequencyService = accountWithdrawalFrequencyService;
        this.paystackAccountNameService = paystackAccountNameService;
    }

    @Override
    @Transactional
    public CommandProcessingResult submitApplication(JsonCommand command) {
        CommandProcessingResult result = submitApplicationInternal(command);
        Long accountId = safeSavingsId(result);

        applyAccountName(accountId, command, false);

        try {
            List<WithdrawalFrequencySettingData> settings = extractSettings(command);
            if (!settings.isEmpty()) {
                accountWithdrawalFrequencyService.createAccountSettings(accountId, settings);
            } else {
                log.warn("No settings found for accountId {}", accountId);
            }
        } catch (Exception e) {
            log.error("Failed to apply withdrawal frequency settings on submit for account {}", accountId, e);
        }

        return result;
    }

    @Override
    @Transactional
    public CommandProcessingResult modifyApplication(Long savingsId, JsonCommand command) {
        CommandProcessingResult result = modifyApplicationInternal(savingsId, command);
        Long accountId = safeSavingsId(result);

        applyAccountName(accountId, command, true);

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

    protected CommandProcessingResult submitApplicationInternal(JsonCommand command) {
        return super.submitApplication(command);
    }

    protected CommandProcessingResult modifyApplicationInternal(Long savingsId, JsonCommand command) {
        return super.modifyApplication(savingsId, command);
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteApplication(Long savingsId) {
        try {
            accountWithdrawalFrequencyService.removeAllAccountSettings(savingsId);
        } catch (Exception e) {
            log.warn("Failed to cleanup withdrawal frequency settings for account {} during delete", savingsId, e);
        }

        return super.deleteApplication(savingsId);
    }

    // Other methods delegate without customization
    @Override
    public CommandProcessingResult approveApplication(Long savingsId, JsonCommand command) {
        return super.approveApplication(savingsId, command);
    }

    @Override
    public CommandProcessingResult undoApplicationApproval(Long savingsId, JsonCommand command) {
        return super.undoApplicationApproval(savingsId, command);
    }

    @Override
    public CommandProcessingResult rejectApplication(Long savingsId, JsonCommand command) {
        return super.rejectApplication(savingsId, command);
    }

    @Override
    public CommandProcessingResult applicantWithdrawsFromApplication(Long savingsId, JsonCommand command) {
        return super.applicantWithdrawsFromApplication(savingsId, command);
    }

    @Override
    public CommandProcessingResult createActiveApplication(
            org.apache.fineract.portfolio.savings.data.SavingsAccountDataDTO savingsAccountDataDTO, String noteText) {
        return super.createActiveApplication(savingsAccountDataDTO, noteText);
    }

    @Override
    public CommandProcessingResult submitGSIMApplication(JsonCommand command) {
        return super.submitGSIMApplication(command);
    }

    @Override
    public CommandProcessingResult approveGSIMApplication(Long gsimId, JsonCommand command) {
        return super.approveGSIMApplication(gsimId, command);
    }

    @Override
    public CommandProcessingResult rejectGSIMApplication(Long gsimId, JsonCommand command) {
        return super.rejectGSIMApplication(gsimId, command);
    }

    @Override
    public CommandProcessingResult undoGSIMApplicationApproval(Long gsimId, JsonCommand command) {
        return super.undoGSIMApplicationApproval(gsimId, command);
    }

    @Override
    public CommandProcessingResult modifyGSIMApplication(Long gsimId, JsonCommand command) {
        return super.modifyGSIMApplication(gsimId, command);
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

    private void applyAccountName(Long accountId, JsonCommand command, boolean onlyWhenPresent) {
        if (accountId == null) {
            return;
        }

        boolean hasParameter = command.parameterExists(SavingsApiConstants.accountNameParamName);
        if (onlyWhenPresent && !hasParameter) {
            return;
        }

        try {
            String requestedName = command.stringValueOfParameterNamed(SavingsApiConstants.accountNameParamName);
            paystackAccountNameService.syncAccountName(accountId, requestedName);
        } catch (Exception ex) {
            log.error("Failed to synchronize account name for savings account {}", accountId, ex);
        }
    }
}
