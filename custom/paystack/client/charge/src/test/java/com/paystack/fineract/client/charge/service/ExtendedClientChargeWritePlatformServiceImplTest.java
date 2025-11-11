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
package com.paystack.fineract.client.charge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import com.paystack.fineract.client.charge.domain.ClientChargeOverrideRepository;
import com.paystack.fineract.client.charge.domain.ClientChargeOverrideSlab;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideSlabRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ExtendedClientChargeWritePlatformServiceImplTest {

    @Mock
    private ClientChargeOverrideRepository overrideRepository;

    @Mock
    private ClientRepositoryWrapper clientRepository;

    @Mock
    private ChargeRepositoryWrapper chargeRepository;

    @InjectMocks
    private ExtendedClientChargeWritePlatformServiceImpl service;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void updateShouldPersistSlabOverrides() {
        ClientChargeOverride override = mock(ClientChargeOverride.class);
        Client client = mock(Client.class);
        Charge charge = mock(Charge.class);

        when(override.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(1L);
        when(override.getCharge()).thenReturn(charge);
        when(charge.getId()).thenReturn(2L);
        when(charge.isSavingsCharge()).thenReturn(true);
        when(charge.isPercentageOfApprovedAmount()).thenReturn(false);
        when(charge.isPercentageOfDisbursementAmount()).thenReturn(false);
        when(charge.getHasVaryingCharge()).thenReturn(true);

        when(overrideRepository.findWithSlabsById(99L)).thenReturn(Optional.of(override));
        when(overrideRepository.saveAndFlush(override)).thenReturn(override);

        ClientChargeOverrideRequest request = new ClientChargeOverrideRequest();
        request.setClientId(1L);
        request.setChargeId(2L);
        request.setSlabs(List.of(
                ClientChargeOverrideSlabRequest.builder().fromAmount(BigDecimal.ZERO).toAmount(new BigDecimal("5000"))
                        .value(new BigDecimal("10")).build(),
                ClientChargeOverrideSlabRequest.builder().fromAmount(new BigDecimal("5000.01")).toAmount(null).value(new BigDecimal("25"))
                        .build()));

        service.update(99L, request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ClientChargeOverrideSlab>> captor = ArgumentCaptor.forClass(List.class);
        verify(override).replaceSlabs(captor.capture());

        List<ClientChargeOverrideSlab> captured = captor.getValue();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getFromAmount()).isEqualByComparingTo("0");
        assertThat(captured.get(0).getToAmount()).isEqualByComparingTo("5000");
        assertThat(captured.get(0).getValue()).isEqualByComparingTo("10");
        assertThat(captured.get(1).getFromAmount()).isEqualByComparingTo("5000.01");
        assertThat(captured.get(1).getToAmount()).isNull();
        assertThat(captured.get(1).getValue()).isEqualByComparingTo("25");

        verify(overrideRepository).saveAndFlush(override);
    }

    @Test
    void updateShouldClearSlabsWhenEmptyPayload() {
        ClientChargeOverride override = mock(ClientChargeOverride.class);
        Client client = mock(Client.class);
        Charge charge = mock(Charge.class);

        when(override.getClient()).thenReturn(client);
        when(client.getId()).thenReturn(1L);
        when(override.getCharge()).thenReturn(charge);
        when(charge.getId()).thenReturn(2L);
        when(charge.isSavingsCharge()).thenReturn(true);
        when(charge.isPercentageOfApprovedAmount()).thenReturn(false);
        when(charge.isPercentageOfDisbursementAmount()).thenReturn(false);

        when(overrideRepository.findWithSlabsById(100L)).thenReturn(Optional.of(override));
        when(overrideRepository.saveAndFlush(override)).thenReturn(override);

        ClientChargeOverrideRequest request = new ClientChargeOverrideRequest();
        request.setClientId(1L);
        request.setChargeId(2L);
        request.setAmount(new BigDecimal("15"));
        request.setSlabs(new ArrayList<>());

        service.update(100L, request);

        verify(override).replaceSlabs(List.of());
        verify(overrideRepository).saveAndFlush(override);
    }
}
