package com.paystack.fineract.portfolio.charge.service;

import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.commands.api.MakercheckersApiResource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.charge.domain.ChargeSlab;
import org.apache.fineract.portfolio.charge.domain.ChargeSlabRepository;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformService;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.tax.domain.TaxGroupRepositoryWrapper;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extended charge write platform service for Paystack custom module. Reuses all core functionality including fee split
 * creation and updates. Uses @Order(2) to avoid conflicts with core service while maintaining custom functionality.
 */
@Service
@Order(2)
public class PaystackChargeWritePlatformServiceImpl extends ChargeWritePlatformServiceJpaRepositoryImpl
        implements ChargeWritePlatformService {
    private final ChargeRepository chargeRepository;
    private final ChargeSlabRepository chargeSlabRepository;
    private final ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer;

    public PaystackChargeWritePlatformServiceImpl(PlatformSecurityContext context,
                                                  ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer, ChargeRepository chargeRepository,
                                                  LoanProductRepository loanProductRepository, JdbcTemplate jdbcTemplate, FineractEntityAccessUtil fineractEntityAccessUtil,
                                                  GLAccountRepositoryWrapper glAccountRepository, TaxGroupRepositoryWrapper taxGroupRepository,
                                                  PaymentTypeRepositoryWrapper paymentTyperepositoryWrapper, ChargeSlabRepository chargeSlabRepository) {
        super(context, fromApiJsonDeserializer, chargeRepository, loanProductRepository, jdbcTemplate, fineractEntityAccessUtil,
                glAccountRepository, taxGroupRepository, paymentTyperepositoryWrapper);

        this.chargeRepository = chargeRepository;
        this.chargeSlabRepository = chargeSlabRepository;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
    }

    // All fee split functionality is inherited from core - no overrides needed
    // Core charge creation, updates, and validation are reused through inheritance

    @Override
    public CommandProcessingResult createCharge(final JsonCommand command) {
        CommandProcessingResult result = super.createCharge(command);
        final boolean chargeVarying = command.parameterExists("chart");
        if (chargeVarying) {
            Optional<Charge> optionalCharge = chargeRepository.findById(result.getResourceId());
            if (optionalCharge.isPresent()) {
                Charge charge = optionalCharge.get();
                final List<ChargeSlab> slab = ChargeSlab.assembleFrom(command, charge);
                this.fromApiJsonDeserializer.validateChartSlabs(slab);
                chargeSlabRepository.saveAll(slab);
            }
        }
        return result;
    }

    @Override
    public CommandProcessingResult updateCharge(final Long chargeId, final JsonCommand command) {
        CommandProcessingResult result = super.updateCharge(chargeId, command);
        Map<String, Object> changes = result.getChanges();
        if (changes.containsKey("chargeSlabs")) {
            Optional<Charge> optionalCharge = chargeRepository.findById(chargeId);
            if (optionalCharge.isPresent()) {
                Charge chargeForUpdate = optionalCharge.get();
                this.fromApiJsonDeserializer.validateChartSlabs(chargeForUpdate.setOfChargeSlabs());
            }
        }
        return result;
    }
}
