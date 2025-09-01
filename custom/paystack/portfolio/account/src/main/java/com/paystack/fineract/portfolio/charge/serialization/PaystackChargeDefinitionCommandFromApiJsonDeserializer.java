package com.paystack.fineract.portfolio.charge.serialization;

import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Extended charge definition command deserializer for Paystack custom module. Reuses all core functionality including
 * fee split validation.
 */
@Component
@Primary
public class PaystackChargeDefinitionCommandFromApiJsonDeserializer extends ChargeDefinitionCommandFromApiJsonDeserializer {

    @Autowired
    public PaystackChargeDefinitionCommandFromApiJsonDeserializer(FromJsonHelper fromApiJsonHelper) {
        super(fromApiJsonHelper);
    }

    // All fee split functionality is inherited from core - no overrides needed
    // Core validation, constants, and logic are reused through inheritance
}
