package com.paystack.fineract.tier.service.config;

import java.util.Set;
import org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer;
import org.springframework.stereotype.Component;

@Component
public class TierJpaCustomizer implements EntityManagerFactoryCustomizer {

    @Override
    public Set<String> additionalPackagesToScan() {
        return Set.of("com.paystack.fineract.tier");
    }
}
