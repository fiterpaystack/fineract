package com.paystack.fineract.client.charge.service;

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.portfolio.charge.domain.Charge;

/**
 * Resolves effective charge values using the precedence: client override -> savings-level amount (API) -> product
 * charge.
 */
public interface ClientChargeOverrideReadService {

    Optional<ClientChargeOverride> getActiveOverride(Long clientId, Long chargeId);

    /**
     * Resolve the effective primary value for a charge (amount for FLAT, percentage for PERCENT_OF_AMOUNT). If client
     * override has a non-null amount and is active, use it; else if savingsAmountFromApi is non-null use it; else fall
     * back to product charge amount.
     */
    BigDecimal resolvePrimaryAmount(Long clientId, Charge chargeDefinition, BigDecimal savingsAmountFromApi);

    /**
     * Resolve effective min cap: prefer client override if present, else product charge minCap.
     */
    BigDecimal resolveMinCap(Long clientId, Charge chargeDefinition);

    /**
     * Resolve effective max cap: prefer client override if present, else product charge maxCap.
     */
    BigDecimal resolveMaxCap(Long clientId, Charge chargeDefinition);
}
