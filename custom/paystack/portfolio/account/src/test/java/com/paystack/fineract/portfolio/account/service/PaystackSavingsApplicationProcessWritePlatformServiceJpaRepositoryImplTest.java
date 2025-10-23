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
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImplTest {

    @Mock
    private SavingsApplicationProcessWritePlatformService delegate;

    @Mock
    private AccountWithdrawalFrequencyService accountService;

    @Mock
    private JsonCommand command;

    @InjectMocks
    private PaystackSavingsApplicationProcessWritePlatformServiceJpaRepositoryImpl sut;

    private CommandProcessingResult resultWithSavingsId;

    @BeforeEach
    void setUp() {
        resultWithSavingsId = new CommandProcessingResultBuilder().withSavingsId(777L).build();
    }

    @Test
    void submitApplication_appliesSettingsWhenProvided() {
        when(delegate.submitApplication(any())).thenReturn(resultWithSavingsId);
        when(command.parameterExists("withdrawalFrequencySettings")).thenReturn(true);
        JsonArray arr = new JsonArray();
        JsonObject obj = new JsonObject();
        obj.addProperty("maxWithdrawals", 2);
        obj.addProperty("timePeriod", TimePeriod.MONTHLY.name());
        obj.addProperty("isActive", true);
        arr.add(obj);
        when(command.arrayOfParameterNamed("withdrawalFrequencySettings")).thenReturn(arr);

        sut.submitApplication(command);

        verify(delegate, times(1)).submitApplication(any());
        verify(accountService, times(1)).createAccountSettings(any(), any());
    }

    @Test
    void modifyApplication_clearsWhenProvidedEmptyArray() {
        when(delegate.modifyApplication(any(), any())).thenReturn(resultWithSavingsId);
        when(command.parameterExists("withdrawalFrequencySettings")).thenReturn(true);
        when(command.arrayOfParameterNamed("withdrawalFrequencySettings")).thenReturn(new JsonArray());

        sut.modifyApplication(777L, command);

        verify(delegate, times(1)).modifyApplication(any(), any());
        verify(accountService, times(1)).removeAllAccountSettings(any());
    }

    @Test
    void deleteApplication_cleansUp() {
        when(delegate.deleteApplication(any())).thenReturn(resultWithSavingsId);

        sut.deleteApplication(777L);

        verify(accountService, times(1)).removeAllAccountSettings(any());
        verify(delegate, times(1)).deleteApplication(any());
    }
}
