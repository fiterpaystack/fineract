package com.paystack.fineract.portfolio.charge.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import com.google.gson.JsonArray;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
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
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Extended charge write platform service for Paystack custom module. 
 * Uses simplified discount rule integration following core Fineract patterns.
 */
@Service
@Order(2)
@Slf4j
public class PaystackChargeWritePlatformServiceImpl extends ChargeWritePlatformServiceJpaRepositoryImpl
        implements ChargeWritePlatformService {

    private final ChargeRepository chargeRepository;
    private final ChargeSlabRepository chargeSlabRepository;
    private final ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final DiscountRuleService discountRuleService;

    public PaystackChargeWritePlatformServiceImpl(PlatformSecurityContext context,
            ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer, ChargeRepository chargeRepository,
            LoanProductRepository loanProductRepository, JdbcTemplate jdbcTemplate, FineractEntityAccessUtil fineractEntityAccessUtil,
            GLAccountRepositoryWrapper glAccountRepository, TaxGroupRepositoryWrapper taxGroupRepository,
            PaymentTypeRepositoryWrapper paymentTyperepositoryWrapper, ChargeSlabRepository chargeSlabRepository,
            DiscountRuleService discountRuleService) {
        super(context, fromApiJsonDeserializer, chargeRepository, loanProductRepository, jdbcTemplate, fineractEntityAccessUtil,
                glAccountRepository, taxGroupRepository, paymentTyperepositoryWrapper);

        this.chargeRepository = chargeRepository;
        this.chargeSlabRepository = chargeSlabRepository;
        this.fromApiJsonDeserializer = fromApiJsonDeserializer;
        this.discountRuleService = discountRuleService;
    }

    @Override
    public CommandProcessingResult createCharge(final JsonCommand command) {
        log.info("Starting charge creation");
        
        // Extract parameters BEFORE calling super method to avoid consumption by core Fineract processing
        boolean hasDiscountRules = command.parameterExists("discountRules");
        Integer chargeAppliesToInt = command.integerValueOfParameterNamed("chargeAppliesTo");
        log.debug("Extracted parameters - hasDiscountRules: {}, chargeAppliesTo: {}", hasDiscountRules, chargeAppliesToInt);
        
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
        
        // Handle discount rules if provided and charge applies to savings
        if (hasDiscountRules && chargeAppliesToInt != null && chargeAppliesToInt == 2) {
            log.info("Processing discount rules for new charge: {}", result.getResourceId());
            
            try {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");
                if (discountRulesArray != null && discountRulesArray.size() > 0) {
                    List<Long> ruleIds = new ArrayList<>();
                    for (int i = 0; i < discountRulesArray.size(); i++) {
                        ruleIds.add(discountRulesArray.get(i).getAsJsonObject().get("id").getAsLong());
                    }
                    
                    log.info("Discount rules to assign to new charge {}: {}", result.getResourceId(), ruleIds);
                    discountRuleService.assignDiscountRulesToCharge(result.getResourceId(), ruleIds);
                    log.info("Successfully assigned discount rules to new charge: {}", result.getResourceId());
                } else {
                    log.info("No discount rules to assign for new charge: {}", result.getResourceId());
                }
            } catch (Exception e) {
                log.error("Error assigning discount rules to charge {}: {}", result.getResourceId(), e.getMessage(), e);
                throw new RuntimeException("Failed to assign discount rules: " + e.getMessage(), e);
            }
        } else {
            log.debug("No discount rules processing needed for new charge: {} (chargeAppliesTo: {}, hasDiscountRules: {})", 
                    result.getResourceId(), chargeAppliesToInt, hasDiscountRules);
        }
        
        log.info("Completed charge creation with ID: {}", result.getResourceId());
        return result;
    }

    @Override
    public CommandProcessingResult updateCharge(final Long chargeId, final JsonCommand command) {
        log.info("Starting charge update for charge ID: {}, command: {}", chargeId, command.json());
        
        // Extract parameters BEFORE calling super method to avoid consumption by core Fineract processing
        boolean hasDiscountRules = command.parameterExists("discountRules");
        Integer chargeAppliesToInt = command.integerValueOfParameterNamed("chargeAppliesTo");
        log.info("Extracted parameters - hasDiscountRules: {}, chargeAppliesTo: {}", hasDiscountRules, chargeAppliesToInt);
        
        CommandProcessingResult result = super.updateCharge(chargeId, command);
        Map<String, Object> changes = result.getChanges();
        if (changes != null && changes.containsKey("chargeSlabs")) {
            Optional<Charge> optionalCharge = chargeRepository.findById(chargeId);
            if (optionalCharge.isPresent()) {
                Charge chargeForUpdate = optionalCharge.get();
                this.fromApiJsonDeserializer.validateChartSlabs(chargeForUpdate.setOfChargeSlabs());
            }
        }
        
        // Handle discount rules if provided and charge applies to savings
        if (hasDiscountRules && chargeAppliesToInt != null && chargeAppliesToInt == 2) {
            log.info("Processing discount rules for charge: {}", chargeId);
            
            try {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");
                List<Long> ruleIds = new ArrayList<>();
                
                if (discountRulesArray != null && discountRulesArray.size() > 0) {
                    for (int i = 0; i < discountRulesArray.size(); i++) {
                        ruleIds.add(discountRulesArray.get(i).getAsJsonObject().get("id").getAsLong());
                    }
                }
                
                log.info("Discount rules to assign to charge {}: {}", chargeId, ruleIds);
                
                // Remove all existing assignments and assign new ones
                discountRuleService.removeAllDiscountRulesFromCharge(chargeId);
                if (!ruleIds.isEmpty()) {
                    discountRuleService.assignDiscountRulesToCharge(chargeId, ruleIds);
                    log.info("Successfully updated discount rules for charge: {}", chargeId);
                } else {
                    log.info("No discount rules to assign for charge: {}", chargeId);
                }
            } catch (Exception e) {
                log.error("Error updating discount rules for charge {}: {}", chargeId, e.getMessage(), e);
                throw new RuntimeException("Failed to update discount rules: " + e.getMessage(), e);
            }
        } else {
            log.debug("No discount rules processing needed for charge: {} (chargeAppliesTo: {}, hasDiscountRules: {})", 
                    chargeId, chargeAppliesToInt, hasDiscountRules);
        }
        
        log.info("Completed charge update for charge ID: {}", chargeId);
        return result;
    }
}
