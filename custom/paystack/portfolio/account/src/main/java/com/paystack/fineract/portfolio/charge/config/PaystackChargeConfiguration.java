package com.paystack.fineract.portfolio.charge.config;

import com.paystack.fineract.portfolio.charge.serialization.PaystackChargeDefinitionCommandFromApiJsonDeserializer;
import com.paystack.fineract.portfolio.charge.service.PaystackChargeReadPlatformServiceImpl;
import com.paystack.fineract.portfolio.charge.service.PaystackChargeWritePlatformServiceImpl;
import org.apache.fineract.accounting.common.AccountingDropdownReadPlatformService;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainServiceJpa;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.service.CurrencyReadPlatformService;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.charge.domain.ChargeSlabRepository;
import org.apache.fineract.portfolio.charge.service.ChargeDropdownReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeReadPlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformService;
import org.apache.fineract.portfolio.common.service.DropdownReadPlatformService;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.tax.domain.TaxGroupRepositoryWrapper;
import org.apache.fineract.portfolio.tax.service.TaxReadPlatformService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Configuration class for Paystack charge services. Overrides core charge service beans with custom implementations.
 */
@Configuration
public class PaystackChargeConfiguration {

    /**
     * Override the core ChargeReadPlatformService bean with Paystack implementation. This service handles all charge
     * reading operations including fee split data.
     */
    @Bean
    @Primary
    public ChargeReadPlatformService chargeReadPlatformService(CurrencyReadPlatformService currencyReadPlatformService,
            ChargeDropdownReadPlatformService chargeDropdownReadPlatformService, JdbcTemplate jdbcTemplate,
            DropdownReadPlatformService dropdownReadPlatformService, FineractEntityAccessUtil fineractEntityAccessUtil,
            AccountingDropdownReadPlatformService accountingDropdownReadPlatformService, TaxReadPlatformService taxReadPlatformService,
            ConfigurationDomainServiceJpa configurationDomainServiceJpa, NamedParameterJdbcTemplate namedParameterJdbcTemplate) {
        return new PaystackChargeReadPlatformServiceImpl(currencyReadPlatformService, chargeDropdownReadPlatformService, jdbcTemplate,
                dropdownReadPlatformService, fineractEntityAccessUtil, accountingDropdownReadPlatformService, taxReadPlatformService,
                configurationDomainServiceJpa, namedParameterJdbcTemplate);
    }

    /**
     * Override the core ChargeWritePlatformService bean with Paystack implementation. This service handles all charge
     * creation and update operations including fee split.
     */
    @Bean
    @Primary
    public ChargeWritePlatformService chargeWritePlatformService(PlatformSecurityContext context,
            PaystackChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer, // Use custom deserializer
            ChargeRepository chargeRepository, LoanProductRepository loanProductRepository, JdbcTemplate jdbcTemplate,
            FineractEntityAccessUtil fineractEntityAccessUtil, GLAccountRepositoryWrapper glAccountRepository,
            TaxGroupRepositoryWrapper taxGroupRepository, PaymentTypeRepositoryWrapper paymentTyperepositoryWrapper,
            ChargeSlabRepository chargeSlabRepository) {
        return new PaystackChargeWritePlatformServiceImpl(context, fromApiJsonDeserializer, chargeRepository, loanProductRepository,
                jdbcTemplate, fineractEntityAccessUtil, glAccountRepository, taxGroupRepository, paymentTyperepositoryWrapper,
                chargeSlabRepository);
    }
}
