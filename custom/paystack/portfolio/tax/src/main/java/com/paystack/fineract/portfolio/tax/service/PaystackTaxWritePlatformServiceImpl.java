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

package com.paystack.fineract.portfolio.tax.service;

import com.paystack.fineract.portfolio.tax.repository.PaystackTaxComponentRepository;
import com.paystack.fineract.portfolio.tax.serialization.PaystackTaxValidator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.tax.api.TaxApiConstants;
import org.apache.fineract.portfolio.tax.domain.*;
import org.apache.fineract.portfolio.tax.serialization.TaxValidator;
import org.apache.fineract.portfolio.tax.service.TaxAssembler;
import org.apache.fineract.portfolio.tax.service.TaxWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackTaxWritePlatformServiceImpl implements TaxWritePlatformService {

    private final PaystackTaxValidator paystackValidator;
    private final TaxValidator coreValidator;
    private final TaxAssembler taxAssembler;
    private final TaxComponentRepository taxComponentRepository;
    private final TaxComponentRepositoryWrapper taxComponentRepositoryWrapper;
    private final TaxGroupRepository taxGroupRepository;
    private final TaxGroupRepositoryWrapper taxGroupRepositoryWrapper;
    private final GLAccountRepositoryWrapper glAccountRepositoryWrapper;
    private final PaystackTaxComponentRepository paystackTaxComponentRepository;
    private final PlatformSecurityContext securityContext;

    public PaystackTaxWritePlatformServiceImpl(final PaystackTaxValidator paystackValidator, final TaxValidator coreValidator,
            final TaxAssembler taxAssembler, final TaxComponentRepository taxComponentRepository,
            final TaxComponentRepositoryWrapper taxComponentRepositoryWrapper, final TaxGroupRepository taxGroupRepository,
            final TaxGroupRepositoryWrapper taxGroupRepositoryWrapper, final GLAccountRepositoryWrapper glAccountRepositoryWrapper,
            final PaystackTaxComponentRepository paystackTaxComponentRepository, final PlatformSecurityContext securityContext) {
        this.paystackValidator = paystackValidator;
        this.coreValidator = coreValidator;
        this.taxAssembler = taxAssembler;
        this.taxComponentRepository = taxComponentRepository;
        this.taxComponentRepositoryWrapper = taxComponentRepositoryWrapper;
        this.taxGroupRepository = taxGroupRepository;
        this.taxGroupRepositoryWrapper = taxGroupRepositoryWrapper;
        this.glAccountRepositoryWrapper = glAccountRepositoryWrapper;
        this.paystackTaxComponentRepository = paystackTaxComponentRepository;
        this.securityContext = securityContext;
    }

    @Override
    public CommandProcessingResult createTaxComponent(final JsonCommand command) {
        TaxComponent taxComponent = this.taxAssembler.assembleTaxComponentFrom(command);
        this.taxComponentRepository.saveAndFlush(taxComponent);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(taxComponent.getId()).build();
    }

    @Override
    public CommandProcessingResult updateTaxComponent(final Long id, final JsonCommand command) {
        this.paystackValidator.validateForTaxComponentUpdate(command.json());

        final TaxComponent taxComponent = this.taxComponentRepositoryWrapper.findOneWithNotFoundDetection(id);
        Map<String, Object> changes = taxComponent.update(command);

        boolean creditTypePresent = command.parameterExists(TaxApiConstants.creditAccountTypeParamName);
        boolean creditIdPresent = command.parameterExists(TaxApiConstants.creditAccountIdParamName);
        if (creditTypePresent || creditIdPresent) {
            changes = updateCreditAccounts(id, command, taxComponent, creditTypePresent, creditIdPresent, changes);
        }

        this.taxComponentRepository.saveAndFlush(taxComponent);
        return new CommandProcessingResultBuilder().withEntityId(id).with(changes).build();
    }

    private Map<String, Object> updateCreditAccounts(final Long id, final JsonCommand command, final TaxComponent taxComponent,
            final boolean creditTypePresent, final boolean creditIdPresent, Map<String, Object> changes) {
        Integer oldType = taxComponent.getCreditAccountType();
        Long oldAccountId = taxComponent.getCreditAcount() != null ? taxComponent.getCreditAcount().getId() : null;

        Integer newType = creditTypePresent ? command.integerValueOfParameterNamed(TaxApiConstants.creditAccountTypeParamName) : oldType;
        Long newAccountId = determineNewAccountId(command, taxComponent, creditIdPresent);

        validateAccountTypeMatch(newAccountId, newType);

        // Use native SQL update with proper audit field handling
        Long userId = this.securityContext.authenticatedUser().getId();
        java.time.LocalDateTime timestamp = DateUtils.getAuditLocalDateTime();
        this.paystackTaxComponentRepository.updateCreditAccounts(id, newType, newAccountId, userId, timestamp);

        return addAccountChangesToMap(changes, newType, newAccountId, oldType, oldAccountId);
    }

    private Long determineNewAccountId(final JsonCommand command, final TaxComponent taxComponent, final boolean creditIdPresent) {
        if (creditIdPresent) {
            return command.longValueOfParameterNamed(TaxApiConstants.creditAccountIdParamName);
        }
        return taxComponent.getCreditAcount() != null ? taxComponent.getCreditAcount().getId() : null;
    }

    private void validateAccountTypeMatch(final Long newAccountId, final Integer newType) {
        if (newAccountId != null) {
            GLAccount account = glAccountRepositoryWrapper.findOneWithNotFoundDetection(newAccountId);
            if (account.getType() == null || !account.getType().equals(newType)) {
                throw new IllegalArgumentException("Credit account type does not match the selected GL account's type");
            }
        }
    }

    private Map<String, Object> addAccountChangesToMap(Map<String, Object> changes, final Integer newType, final Long newAccountId,
            final Integer oldType, final Long oldAccountId) {
        if (changes == null) {
            changes = new HashMap<>();
        }

        // Only add to changes if values actually changed
        if (!newType.equals(oldType)) {
            changes.put(TaxApiConstants.creditAccountTypeParamName, newType);
        }
        if (!java.util.Objects.equals(newAccountId, oldAccountId)) {
            changes.put(TaxApiConstants.creditAccountIdParamName, newAccountId);
        }

        return changes;
    }

    @Override
    public CommandProcessingResult createTaxGroup(final JsonCommand command) {
        TaxGroup taxGroup = this.taxAssembler.assembleTaxGroupFrom(command);
        this.taxGroupRepository.saveAndFlush(taxGroup);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(taxGroup.getId()).build();
    }

    @Override
    public CommandProcessingResult updateTaxGroup(final Long id, final JsonCommand command) {
        this.coreValidator.validateForTaxGroupUpdate(command.json());
        final TaxGroup taxGroup = this.taxGroupRepositoryWrapper.findOneWithNotFoundDetection(id);
        final boolean isUpdate = true;
        Set<TaxGroupMappings> groupMappings = this.taxAssembler.assembleTaxGroupMappingsFrom(command, isUpdate);
        this.coreValidator.validateTaxGroupEndDateAndTaxComponent(taxGroup, groupMappings);
        Map<String, Object> changes = taxGroup.update(command, groupMappings);
        this.coreValidator.validateTaxGroup(taxGroup);
        taxGroup.getTaxGroupMappings().forEach(t -> {
            if (t.getTaxGroup() == null) {
                t.setTaxGroup(taxGroup);
            }
        });
        this.taxGroupRepository.saveAndFlush(taxGroup);
        return new CommandProcessingResultBuilder().withEntityId(id).with(changes).build();
    }
}
