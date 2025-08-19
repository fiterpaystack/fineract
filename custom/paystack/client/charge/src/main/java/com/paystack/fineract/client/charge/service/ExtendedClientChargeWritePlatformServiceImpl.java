/*
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

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import com.paystack.fineract.client.charge.domain.ClientChargeOverrideRepository;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class ExtendedClientChargeWritePlatformServiceImpl implements ExtendedClientChargeWritePlatformService {

    private final ClientChargeOverrideRepository overrideRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final ChargeRepositoryWrapper chargeRepository;

    @Override
    public ClientChargeOverrideResult create(ClientChargeOverrideRequest request) {
        validateRequest(request);

        Client client = clientRepository.findOneWithNotFoundDetection(request.getClientId());

        Charge charge = chargeRepository.findOneWithNotFoundDetection(request.getChargeId());

        validateChargeForClientOverride(charge, request);

        ClientChargeOverride entity = overrideRepository.findByClient_IdAndCharge_Id(client.getId(), charge.getId())
                .orElse(new ClientChargeOverride(client, charge, null, null, null));

        entity.setAmount(request.getAmount());
        entity.setMinCap(request.getMinCap());
        entity.setMaxCap(request.getMaxCap());

        ClientChargeOverride saved = overrideRepository.saveAndFlush(entity);
        return ClientChargeOverrideResult.fromEntity(saved);
    }

    @Override
    public ClientChargeOverrideResult update(Long id, ClientChargeOverrideRequest request) {
        ClientChargeOverride entity = overrideRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + id));

        if (request.getClientId() != null && !request.getClientId().equals(entity.getClient().getId())) {
            Client client = clientRepository.findOneWithNotFoundDetection(request.getClientId());
            entity.setClient(client);
        }
        if (request.getChargeId() != null && !request.getChargeId().equals(entity.getCharge().getId())) {
            Charge charge = chargeRepository.findOneWithNotFoundDetection(request.getChargeId());

            validateChargeForClientOverride(charge, request);
            entity.setCharge(charge);
        }

        // Allow nulls to clear overrides
        entity.setAmount(request.getAmount());
        entity.setMinCap(request.getMinCap());
        entity.setMaxCap(request.getMaxCap());

        ClientChargeOverride saved = overrideRepository.save(entity);
        return ClientChargeOverrideResult.fromEntity(saved);
    }

    @Override
    public void delete(Long id) {
        ClientChargeOverride entity = overrideRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + id));
        overrideRepository.delete(entity);
    }

    private void validateRequest(ClientChargeOverrideRequest request) {

        if (request == null) {
            throwValidationError("error.msg.request.body.null", "The provided JSON is invalid.", "body");
        }
        if (request.getClientId() == null) {
            throwValidationError("error.msg.client.id.required", "clientId is required", "clientId");

        }
        if (request.getChargeId() == null) {
            throwValidationError("error.msg.charge.id.required", "chargeId is required.", "chargeId");

        }
    }

    private void validateChargeForClientOverride(Charge charge, ClientChargeOverrideRequest request) {
        if (!charge.isSavingsCharge()) {
            throwValidationError("error.msg.charge.not.supported", "Charge does not apply to savings: " + charge.getId(), "chargeId");
        }

        // At least one override value must be provided
        if (request.getAmount() == null && request.getMinCap() == null && request.getMaxCap() == null) {
            throwValidationError("error.msg.charge.override.required",
                    "At least one override value (amount, minCap, or maxCap) must be provided", "amount");
        }

        // If caps are provided, ensure base charge supports caps (percentage-based types)
        if ((request.getMinCap() != null || request.getMaxCap() != null)
                && !(charge.isPercentageOfApprovedAmount() || charge.isPercentageOfDisbursementAmount())) {
            throwValidationError("error.msg.charge.values.not.supported", "Caps are only allowed for percentage-based charges", "minCap");
        }
    }

    private void throwValidationError(String errorCode, String defaultMessage, String parameterName) {
        List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError(errorCode, defaultMessage, parameterName));
        throw new PlatformApiDataValidationException(errorCode, defaultMessage, errors);
    }
}
