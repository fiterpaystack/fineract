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
import com.paystack.fineract.client.charge.domain.ClientChargeOverrideSlab;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideRequest;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideResult;
import com.paystack.fineract.client.charge.dto.ClientChargeOverrideSlabRequest;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
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
@RequiredArgsConstructor
public class ExtendedClientChargeWritePlatformServiceImpl implements ExtendedClientChargeWritePlatformService {

    private final ClientChargeOverrideRepository overrideRepository;
    private final ClientRepositoryWrapper clientRepository;
    private final ChargeRepositoryWrapper chargeRepository;

    @Transactional
    @Override
    public ClientChargeOverrideResult create(ClientChargeOverrideRequest request) {
        validateRequest(request);

        Client client = clientRepository.findOneWithNotFoundDetection(request.getClientId());

        Charge charge = chargeRepository.findOneWithNotFoundDetection(request.getChargeId());

        validateChargeForClientOverride(charge, request);

        ClientChargeOverride entity = overrideRepository.findWithSlabsByClient_IdAndCharge_Id(client.getId(), charge.getId())
                .orElse(new ClientChargeOverride(client, charge, null, null, null));

        entity.setAmount(request.getAmount());
        entity.setMinCap(request.getMinCap());
        entity.setMaxCap(request.getMaxCap());
        if (request.getActive() != null) {
            entity.setIsActive(request.getActive());
        }
        applySlabs(entity, request, charge);

        ClientChargeOverride saved = overrideRepository.saveAndFlush(entity);
        return ClientChargeOverrideResult.fromEntity(saved);
    }

    @Transactional
    @Override
    public ClientChargeOverrideResult update(Long id, ClientChargeOverrideRequest request) {
        ClientChargeOverride entity = overrideRepository.findWithSlabsById(id)
                .orElseThrow(() -> new IllegalArgumentException("Override not found: " + id));

        if (request.getClientId() != null && !request.getClientId().equals(entity.getClient().getId())) {
            Client client = clientRepository.findOneWithNotFoundDetection(request.getClientId());
            entity.setClient(client);
        }
        if (request.getChargeId() != null && !request.getChargeId().equals(entity.getCharge().getId())) {
            Charge charge = chargeRepository.findOneWithNotFoundDetection(request.getChargeId());

            validateChargeForClientOverride(charge, request);
            entity.setCharge(charge);
            applySlabs(entity, request, charge);
        } else {
            applySlabs(entity, request, entity.getCharge());
        }

        // Allow nulls to clear overrides
        entity.setAmount(request.getAmount());
        entity.setMinCap(request.getMinCap());
        entity.setMaxCap(request.getMaxCap());
        if (request.getActive() != null) {
            entity.setIsActive(request.getActive());
        }

        ClientChargeOverride saved = overrideRepository.saveAndFlush(entity);
        return ClientChargeOverrideResult.fromEntity(saved);
    }

    @Transactional
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
        boolean hasScalarOverride = request.getAmount() != null || request.getMinCap() != null || request.getMaxCap() != null;
        boolean hasSlabOverride = request.getSlabs() != null && !request.getSlabs().isEmpty();
        if (!hasScalarOverride && !hasSlabOverride) {
            throwValidationError("error.msg.charge.override.required",
                    "At least one override value (amount, minCap, maxCap, or slabs) must be provided", "amount");
        }

        // If caps are provided, ensure base charge supports caps (percentage-based types)
        if ((request.getMinCap() != null || request.getMaxCap() != null)
                && !(charge.isPercentageOfApprovedAmount() || charge.isPercentageOfDisbursementAmount())) {
            throwValidationError("error.msg.charge.values.not.supported", "Caps are only allowed for percentage-based charges", "minCap");
        }
        if (hasSlabOverride && !Boolean.TRUE.equals(charge.getHasVaryingCharge())) {
            throwValidationError("error.msg.charge.slabs.not.supported", "Charge does not support slab overrides: " + charge.getId(),
                    "slabs");
        }
    }

    private void throwValidationError(String errorCode, String defaultMessage, String parameterName) {
        List<ApiParameterError> errors = new ArrayList<>();
        errors.add(ApiParameterError.parameterError(errorCode, defaultMessage, parameterName));
        throw new PlatformApiDataValidationException(errorCode, defaultMessage, errors);
    }

    private void applySlabs(ClientChargeOverride entity, ClientChargeOverrideRequest request, Charge charge) {
        List<ClientChargeOverrideSlabRequest> slabRequests = request.getSlabs();
        if (slabRequests == null) {
            return;
        }
        if (slabRequests.isEmpty()) {
            entity.replaceSlabs(List.of());
            return;
        }
        List<ClientChargeOverrideSlabRequest> ordered = validateSlabOverrides(slabRequests);

        List<ClientChargeOverrideSlab> mapped = ordered.stream()
                .map(slab -> new ClientChargeOverrideSlab(entity, slab.getFromAmount(), slab.getToAmount(), slab.getValue())).toList();
        entity.replaceSlabs(mapped);
    }

    private List<ClientChargeOverrideSlabRequest> validateSlabOverrides(List<ClientChargeOverrideSlabRequest> slabRequests) {
        if (slabRequests == null || slabRequests.isEmpty()) {
            return List.of();
        }
        List<ClientChargeOverrideSlabRequest> sorted = slabRequests.stream()
                .sorted(Comparator.comparing(ClientChargeOverrideSlabRequest::getFromAmount, Comparator.nullsFirst(BigDecimal::compareTo)))
                .toList();

        ClientChargeOverrideSlabRequest previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            ClientChargeOverrideSlabRequest current = sorted.get(i);
            String baseParam = "slabs[" + i + "]";

            if (current.getFromAmount() == null) {
                throwValidationError("error.msg.charge.slabs.from.required", "fromAmount is required for slab overrides", baseParam);
            }
            if (current.getValue() == null) {
                throwValidationError("error.msg.charge.slabs.value.required", "value is required for slab overrides", baseParam);
            }
            ensureNonNegative(current.getFromAmount(), baseParam + ".fromAmount");
            ensureNonNegative(current.getValue(), baseParam + ".value");

            if (previous != null) {
                if (current.getFromAmount().compareTo(previous.getFromAmount()) <= 0) {
                    throwValidationError("error.msg.charge.slabs.order.invalid", "fromAmount must be strictly increasing", baseParam);
                }
                if (previous.getToAmount() != null && current.getFromAmount().compareTo(previous.getToAmount()) <= 0) {
                    throwValidationError("error.msg.charge.slabs.overlap", "Slab ranges cannot overlap", baseParam);
                }
                if (previous.getToAmount() == null) {
                    throwValidationError("error.msg.charge.slabs.open.range.must.be.last",
                            "Only the last slab can omit toAmount (open ended)", baseParam);
                }
            }
            if (current.getToAmount() != null) {
                ensureNonNegative(current.getToAmount(), baseParam + ".toAmount");
                if (current.getToAmount().compareTo(current.getFromAmount()) < 0) {
                    throwValidationError("error.msg.charge.slabs.range.invalid", "toAmount must be greater than or equal to fromAmount",
                            baseParam);
                }
            }
            previous = current;
        }
        return sorted;
    }

    private void ensureNonNegative(BigDecimal value, String parameterName) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throwValidationError("error.msg.charge.slabs.negative", "Value cannot be negative", parameterName);
        }
    }
}
