package com.paystack.fineract.client.charge.service;

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import com.paystack.fineract.client.charge.domain.ClientChargeOverrideRepository;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientChargeOverrideReadServiceImpl implements ClientChargeOverrideReadService {

    private final ClientChargeOverrideRepository repository;

    @Override
    public Optional<ClientChargeOverride> getActiveOverride(Long clientId, Long chargeId) {
        if (clientId == null || chargeId == null) {
            return Optional.empty();
        }
        return repository.findByClient_IdAndCharge_Id(clientId, chargeId).filter(ov -> Boolean.TRUE.equals(ov.getIsActive()));
    }

    @Override
    public BigDecimal resolvePrimaryAmount(Long clientId, Charge chargeDefinition, BigDecimal savingsAmountFromApi) {
        Optional<ClientChargeOverride> ov = getActiveOverride(clientId, chargeDefinition.getId());
        if (ov.isPresent() && ov.get().getAmount() != null) {
            return ov.get().getAmount();
        }
        if (savingsAmountFromApi != null) {
            return savingsAmountFromApi;
        }
        return chargeDefinition.getAmount();
    }

    @Override
    public BigDecimal resolveMinCap(Long clientId, Charge chargeDefinition) {
        return getActiveOverride(clientId, chargeDefinition.getId()).map(ClientChargeOverride::getMinCap)
                .orElse(chargeDefinition.getMinCap());
    }

    @Override
    public BigDecimal resolveMaxCap(Long clientId, Charge chargeDefinition) {
        return getActiveOverride(clientId, chargeDefinition.getId()).map(ClientChargeOverride::getMaxCap)
                .orElse(chargeDefinition.getMaxCap());
    }
}
