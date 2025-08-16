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
package com.paystack.fineract.portfolio.client;

import java.util.Collections;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.address.service.AddressWritePlatformService;
import org.apache.fineract.portfolio.client.data.ClientDataValidator;
import org.apache.fineract.portfolio.client.domain.ClientNonPersonRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.service.ClientFamilyMembersWritePlatformService;
import org.apache.fineract.portfolio.client.service.ClientWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PaystackClientWritePlatformServiceJpaRepositoryImpl extends ClientWritePlatformServiceJpaRepositoryImpl {

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
            ClientFamilyMembersWritePlatformService clientFamilyMembersWritePlatformService,
            BusinessEventNotifierService businessEventNotifierService,
            EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService, ExternalIdFactory externalIdFactory) {
        super(context, clientRepository, clientNonPersonRepository, officeRepositoryWrapper, noteRepository, groupRepository,
                fromApiJsonDeserializer, accountNumberGenerator, staffRepository, codeValueRepository, loanRepositoryWrapper,
                savingsRepositoryWrapper, savingsProductRepository, savingsApplicationProcessWritePlatformService, commandProcessingService,
                configurationDomainService, accountNumberFormatRepository, fromApiJsonHelper, addressWritePlatformService,
                clientFamilyMembersWritePlatformService, businessEventNotifierService, entityDatatableChecksWritePlatformService,
                externalIdFactory);
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
        return super.updateClient(clientId, command);
    }
}
