package com.paystack.fineract.portfolio.charge.service;

import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.tax.domain.TaxGroupRepositoryWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Extended charge write platform service for Paystack custom module. Reuses all core functionality including fee split
 * creation and updates. Uses @Order(2) to avoid conflicts with core service while maintaining custom functionality.
 */
@Service
@Order(2)
public class PaystackChargeWritePlatformServiceImpl extends ChargeWritePlatformServiceJpaRepositoryImpl
        implements ChargeWritePlatformService {

    public PaystackChargeWritePlatformServiceImpl(PlatformSecurityContext context,
            ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer, ChargeRepository chargeRepository,
            LoanProductRepository loanProductRepository, JdbcTemplate jdbcTemplate, FineractEntityAccessUtil fineractEntityAccessUtil,
            GLAccountRepositoryWrapper glAccountRepository, TaxGroupRepositoryWrapper taxGroupRepository,
            PaymentTypeRepositoryWrapper paymentTyperepositoryWrapper) {
        super(context, fromApiJsonDeserializer, chargeRepository, loanProductRepository, jdbcTemplate, fineractEntityAccessUtil,
                glAccountRepository, taxGroupRepository, paymentTyperepositoryWrapper);
    }

    // All fee split functionality is inherited from core - no overrides needed
    // Core charge creation, updates, and validation are reused through inheritance
}
