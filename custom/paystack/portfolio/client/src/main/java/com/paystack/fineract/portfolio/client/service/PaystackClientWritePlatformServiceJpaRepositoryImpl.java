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
package com.paystack.fineract.portfolio.client.service;

import com.google.gson.JsonElement;
import com.paystack.fineract.portfolio.account.data.SavingsAccountTransactionLimitValidator;
import com.paystack.fineract.portfolio.client.api.PaystackClientApiConstants;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.address.service.AddressWritePlatformService;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.data.ClientDataValidator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientNonPersonRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.apache.fineract.portfolio.client.service.ClientFamilyMembersWritePlatformService;
import org.apache.fineract.portfolio.client.service.ClientWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
@Slf4j
public class PaystackClientWritePlatformServiceJpaRepositoryImpl extends ClientWritePlatformServiceJpaRepositoryImpl
        implements PaystackClientWritePlatformService {

    private final SavingsAccountTransactionLimitValidator savingsAccountTransactionLimitValidator;
    private final ClientRepositoryWrapper clientRepository;

    public PaystackClientWritePlatformServiceJpaRepositoryImpl(PlatformSecurityContext context, ClientRepositoryWrapper clientRepository,
            ClientNonPersonRepositoryWrapper clientNonPersonRepository, OfficeRepositoryWrapper officeRepositoryWrapper,
            NoteRepository noteRepository, GroupRepository groupRepository, ClientDataValidator fromApiJsonDeserializer,
            AccountNumberGenerator accountNumberGenerator, StaffRepositoryWrapper staffRepository,
            CodeValueRepositoryWrapper codeValueRepository, LoanRepositoryWrapper loanRepositoryWrapper,
            SavingsAccountRepositoryWrapper savingsRepositoryWrapper, SavingsProductRepository savingsProductRepository,
            SavingsApplicationProcessWritePlatformService savingsApplicationProcessWritePlatformService,
            CommandProcessingService commandProcessingService, ConfigurationDomainService configurationDomainService,
            AccountNumberFormatRepositoryWrapper accountNumberFormatRepository, FromJsonHelper fromApiJsonHelper,
            AddressWritePlatformService addressWritePlatformService,
            SavingsAccountTransactionLimitValidator savingsAccountTransactionLimitValidator,
            ClientFamilyMembersWritePlatformService clientFamilyMembersWritePlatformService,
            BusinessEventNotifierService businessEventNotifierService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, ExternalIdFactory externalIdFactory) {
        super(context, clientRepository, clientNonPersonRepository, officeRepositoryWrapper, noteRepository, groupRepository,
                fromApiJsonDeserializer, accountNumberGenerator, staffRepository, codeValueRepository, loanRepositoryWrapper,
                savingsRepositoryWrapper, savingsProductRepository, savingsApplicationProcessWritePlatformService, commandProcessingService,
                configurationDomainService, accountNumberFormatRepository, fromApiJsonHelper, addressWritePlatformService,
                clientFamilyMembersWritePlatformService, businessEventNotifierService, entityDatatableChecksWritePlatformService,
                externalIdFactory);
        this.savingsAccountTransactionLimitValidator = savingsAccountTransactionLimitValidator;
        this.clientRepository = clientRepository;
    }

    @Override
    public CommandProcessingResult createClient(final JsonCommand command) {
        String mobileNo = command.stringValueOfParameterNamed("mobileNo");
        if (mobileNo == null || mobileNo.trim().isEmpty()) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.mobileNo.required",
                    "The parameter Mobile Number is required", "mobileNo");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }
        return super.createClient(command);
    }

    @Override
    public CommandProcessingResult updateClient(final Long clientId, final JsonCommand command) {
        String mobileNo = command.stringValueOfParameterNamed("mobileNo");
        if (mobileNo == null || mobileNo.trim().isEmpty()) {
            ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.mobileNo.required",
                    "The parameter Mobile Number is required", "mobileNo");
            throw new PlatformApiDataValidationException(Collections.singletonList(error));
        }

        // Update client's saving accounts to UNBLOCKDEBIT or BLOCKDEBIT as per classification
        final Client clientForUpdate = clientRepository.findOneWithNotFoundDetection(clientId);
        if (command.isChangeInLongParameterNamed(ClientApiConstants.clientClassificationIdParamName,
                clientForUpdate.clientClassificationId())) {

            final Long newClassificationId = command.longValueOfParameterNamed(ClientApiConstants.clientClassificationIdParamName);
            savingsAccountTransactionLimitValidator.updateClientSavingsAccountsSubStatusForClassification(clientForUpdate,
                    newClassificationId);
        }

        return super.updateClient(clientId, command);
    }

    @Transactional
    @Override
    public CommandProcessingResult upgradeClientToEntity(final Long clientId, final JsonCommand command) {
        try {
            // Validate the upgrade request
            validateClientUpgradeToEntity(clientId, command);

            final Client client = this.clientRepository.findOneWithNotFoundDetection(clientId);

            // Check if client is already an entity
            if (client.getLegalForm() != null && LegalForm.fromInt(client.getLegalForm()).isEntity()) {
                throw new PlatformDataIntegrityException("error.msg.client.already.entity", "Client is already classified as an entity",
                        "legalForm", client.getLegalForm());
            }

            // Check if client is in a valid state for upgrade
            if (!client.isActive()) {
                throw new PlatformDataIntegrityException("error.msg.client.not.active.for.upgrade",
                        "Only active clients can be upgraded to entity", "status", client.getStatus());
            }

            // Extract upgrade parameters
            final LocalDate upgradeDate = command.localDateValueOfParameterNamed(PaystackClientApiConstants.upgradeDateParamName);
            final String entityName = command.stringValueOfParameterNamed(PaystackClientApiConstants.entityNameParamName);
            final Boolean migrateFirstName = command
                    .booleanPrimitiveValueOfParameterNamed(PaystackClientApiConstants.migrateFirstNameParamName);
            final Boolean migrateLastName = command
                    .booleanPrimitiveValueOfParameterNamed(PaystackClientApiConstants.migrateLastNameParamName);
            final LocalDate incorporationDate = command
                    .localDateValueOfParameterNamed(PaystackClientApiConstants.incorporationDateParamName);

            // Create audit log entry before making changes
            final Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("upgradeDate", upgradeDate);
            changes.put("previousLegalForm", client.getLegalForm());
            changes.put("newLegalForm", LegalForm.ENTITY.getValue());

            // Perform the upgrade
            upgradeClientLegalForm(client, entityName, migrateFirstName, migrateLastName, incorporationDate, changes);

            // Create ClientNonPerson record
            extractAndCreateClientNonPerson(client, command);

            // Save the updated client
            this.clientRepository.saveAndFlush(client);

            return new CommandProcessingResultBuilder() //
                    .withCommandId(command.commandId()) //
                    .withClientId(clientId) //
                    .withEntityId(clientId) //
                    .withEntityExternalId(client.getExternalId()) //
                    .with(changes) //
                    .build();

        } catch (final JpaSystemException | DataIntegrityViolationException dve) {
            handleDataIntegrityIssues(command, dve.getMostSpecificCause(), dve);
            return CommandProcessingResult.empty();
        } catch (final PersistenceException dve) {
            Throwable throwable = ExceptionUtils.getRootCause(dve.getCause());
            handleDataIntegrityIssues(command, throwable, dve);
            return CommandProcessingResult.empty();
        }
    }

    private void validateClientUpgradeToEntity(final Long clientId, final JsonCommand command) {
        // Validate required fields for entity upgrade
        if (!command.hasParameter(PaystackClientApiConstants.upgradeDateParamName)) {
            throw new PlatformDataIntegrityException("error.msg.client.upgrade.date.required",
                    "Upgrade date is required for client upgrade to entity", PaystackClientApiConstants.upgradeDateParamName, null);
        }

        if (!command.hasParameter(PaystackClientApiConstants.migrateFirstNameParamName)
                && !command.hasParameter(PaystackClientApiConstants.migrateLastNameParamName)) {
            if (!command.hasParameter(PaystackClientApiConstants.entityNameParamName)) {
                throw new PlatformDataIntegrityException("error.msg.client.entity.name.required",
                        "Entity name is required for client upgrade to entity", PaystackClientApiConstants.entityNameParamName, null);
            }
        }

        // Validate that client non-person details are provided
        final JsonElement clientNonPersonElement = this.fromApiJsonHelper
                .parse(command.jsonFragment(ClientApiConstants.clientNonPersonDetailsParamName));

        if (clientNonPersonElement == null || isEmpty(clientNonPersonElement)) {
            throw new PlatformDataIntegrityException("error.msg.client.nonperson.details.required",
                    "Client non-person details are required for entity upgrade", ClientApiConstants.clientNonPersonDetailsParamName, null);
        }

        final Long clientNonPersonConstitutionId = this.fromApiJsonHelper.extractLongNamed(ClientApiConstants.constitutionIdParamName,
                clientNonPersonElement);
        if (clientNonPersonConstitutionId == null) {
            throw new PlatformDataIntegrityException("error.msg.client.nonperson.constitutionid.required",
                    "Constitution ID is required in client non-person details for entity upgrade",
                    ClientApiConstants.constitutionIdParamName, null);
        }
    }

    private void upgradeClientLegalForm(final Client client, final String entityName, final Boolean migrateFirstName,
            final Boolean migrateLastName, final LocalDate incorporationDate, final Map<String, Object> changes) {

        // Store original name data for migration
        final String originalFirstName = client.getFirstname();
        final String originalLastName = client.getLastname();

        // Update legal form to ENTITY
        client.setLegalForm(LegalForm.ENTITY.getValue());
        client.setFullname(entityName);

        if (incorporationDate != null) {
            client.setDateOfBirth(incorporationDate);
            changes.put("incorporationDate", incorporationDate);
        }

        // Handle name migration based on parameters
        if (Boolean.TRUE.equals(migrateFirstName) && StringUtils.isNotBlank(originalFirstName)) {
            // Migrate first name to fullname or remarks
            if (StringUtils.isBlank(client.getFullname())) {
                client.setFullname(originalFirstName);
                changes.put("migratedFirstName", originalFirstName);
            }
        }

        if (Boolean.TRUE.equals(migrateLastName) && StringUtils.isNotBlank(originalLastName)) {
            // Append last name to fullname if it exists, otherwise set as fullname
            if (StringUtils.isNotBlank(client.getFullname())) {
                client.setFullname(client.getFullname() + " " + originalLastName);
            } else {
                client.setFullname(originalLastName);
            }
            changes.put("migratedLastName", originalLastName);
        }

        if (StringUtils.isEmpty(client.getFullname())) {
            client.setFullname(entityName);
        }

        // Clear individual name fields for entity
        client.setFirstname(null);
        client.setLastname(null);
        client.setMiddlename(null);

        // Reset derived names to reflect entity structure
        client.resetDerivedNames(LegalForm.ENTITY);

        // Update display name
        client.deriveDisplayName();

        changes.put("nameMigration", "Individual names cleared, migrated to entity structure");
    }
}
