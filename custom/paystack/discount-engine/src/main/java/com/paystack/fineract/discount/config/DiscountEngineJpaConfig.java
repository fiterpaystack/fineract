package com.paystack.fineract.discount.config;

import org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Discount Engine JPA Configuration
 * Registers discount engine entities with JPA
 */
@Component
public class DiscountEngineJpaConfig implements EntityManagerFactoryCustomizer {
    
    @Override
    public Set<String> additionalPackagesToScan() {
        return Set.of("com.paystack.fineract.discount.domain");
    }
}
