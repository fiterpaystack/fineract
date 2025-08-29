package com.paystack.fineract.portfolio.savings.config;

import java.util.Set;
import org.apache.fineract.infrastructure.core.config.jpa.EntityManagerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = { "com.paystack.fineract.portfolio.savings.domain" })
public class PaystackSavingsJpaConfig implements EntityManagerFactoryCustomizer {

    @Override
    public Set<String> additionalPackagesToScan() {
        return Set.of("com.paystack.fineract.portfolio.savings.domain");
    }
}
