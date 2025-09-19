package com.paystack.fineract.portfolio.discount.config;

import java.util.Set;
import org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Configuration for Discount Engine Module
 */
@Configuration
@EnableJpaRepositories(basePackages = { "com.paystack.fineract.portfolio.discount.domain",
        "com.paystack.fineract.portfolio.discount.repository" })
public class DiscountEngineJpaConfig implements EntityManagerFactoryCustomizer {

    @Override
    public Set<String> additionalPackagesToScan() {
        return Set.of("com.paystack.fineract.portfolio.discount.domain", "com.paystack.fineract.portfolio.discount.repository");
    }
}
