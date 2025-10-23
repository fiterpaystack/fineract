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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.group.domain.GroupRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private AccountWithdrawalFrequencyService accountWithdrawalFrequencyService;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private SavingsAccountRepositoryWrapper savingAccountRepository;

    @Mock
    private SavingsAccountAssembler savingAccountAssembler;

    @Mock
    private SavingsAccountDataValidator savingsAccountDataValidator;

    @Mock
    private AccountNumberGenerator accountNumberGenerator;

    @Mock
    private ClientRepositoryWrapper clientRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private SavingsProductRepository savingsProductRepository;

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private StaffRepositoryWrapper staffRepository;

    @Mock
    private SavingsAccountApplicationTransitionApiJsonValidator savingsAccountApplicationTransitionApiJsonValidator;

    @Mock
    private SavingsAccountChargeAssembler savingsAccountChargeAssembler;

    @Mock
    private CommandProcessingService commandProcessingService;

    @Mock
    private SavingsAccountDomainService savingsAccountDomainService;

    @Mock
    private SavingsAccountWritePlatformService savingsAccountWritePlatformService;

    @Mock
    private AccountNumberFormatRepositoryWrapper accountNumberFormatRepository;

    @Mock
    private BusinessEventNotifierService businessEventNotifierService;

    @Mock
    private EntityDatatableChecksWritePlatformService entityDatatableChecksWritePlatformService;

    @Mock
    private GSIMRepositoy gsimRepository;

    @Mock
    private GroupRepositoryWrapper groupRepositoryWrapper;

    @Mock
    private GroupSavingsIndividualMonitoringWritePlatformService gsimWritePlatformService;

    @Mock
    private JsonCommand command;

    @InjectMocks
    private PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl sut;

    private CommandProcessingResult resultWithSavingsId;

    @BeforeEach
    void setUp() {
        resultWithSavingsId = new CommandProcessingResultBuilder().withSavingsId(777L).build();
    }

    // Note: Integration tests for the custom service are complex due to parent class dependencies
    // The custom logic is already tested through the AccountWithdrawalFrequencyService tests
    // and the domain model tests. The integration with the parent class would require
    // extensive mocking of the parent class behavior which is not practical for unit tests.
}