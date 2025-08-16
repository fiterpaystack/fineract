package com.paystack.fineract.tier.service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories({ "com.paystack.fineract.tier.service.domain", "com.paystack.fineract.tier.service.repository" })
public class TierJpaConfig {}
