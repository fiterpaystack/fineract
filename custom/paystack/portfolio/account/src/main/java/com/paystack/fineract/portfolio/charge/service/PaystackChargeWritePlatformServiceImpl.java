package com.paystack.fineract.portfolio.charge.service;

import com.google.gson.JsonArray;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.stereotype.Service;

/**
 * Extended charge write platform service for Paystack custom module. Uses simplified discount rule integration
 * following core Fineract patterns.
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

        // Extract parameters BEFORE calling super method to avoid consumption by core Fineract processing
        boolean hasDiscountRules = command.parameterExists("discountRules");
        Integer chargeAppliesToInt = command.integerValueOfParameterNamed("chargeAppliesTo");

        CommandProcessingResult result = super.createCharge(command);
        final boolean chargeVarying = command.parameterExists("chart");
        if (chargeVarying) {
            Optional<Charge> optionalCharge = chargeRepository.findById(result.getResourceId());
            if (optionalCharge.isPresent()) {
                final List<ChargeSlab> slab = ChargeSlab.assembleFrom(command, optionalCharge.get());
                this.fromApiJsonDeserializer.validateChartSlabs(slab);
                chargeSlabRepository.saveAll(slab);
            }
        }

        // Handle discount rules if provided and charge applies to savings
        if (hasDiscountRules && chargeAppliesToInt != null && chargeAppliesToInt == 2) {

            try {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");
                if (discountRulesArray != null && discountRulesArray.size() > 0) {
                    List<Long> ruleIds = new ArrayList<>();
                    for (int i = 0; i < discountRulesArray.size(); i++) {
                        ruleIds.add(discountRulesArray.get(i).getAsJsonObject().get("id").getAsLong());
                    }

                    discountRuleService.assignDiscountRulesToCharge(result.getResourceId(), ruleIds);
                }
            } catch (Exception e) {
                log.error("Error assigning discount rules to charge {}", result.getResourceId(), e);
                throw new RuntimeException("Failed to assign discount rules: " + e.getMessage(), e);
            }
        }

        return result;
    }

    @Override
    public CommandProcessingResult updateCharge(final Long chargeId, final JsonCommand command) {

        // Extract parameters BEFORE calling super method to avoid consumption by core Fineract processing
        boolean hasDiscountRules = command.parameterExists("discountRules");
        Integer chargeAppliesToInt = command.integerValueOfParameterNamed("chargeAppliesTo");

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

            try {
                JsonArray discountRulesArray = command.arrayOfParameterNamed("discountRules");
                List<Long> ruleIds = new ArrayList<>();

                if (discountRulesArray != null && discountRulesArray.size() > 0) {
                    for (int i = 0; i < discountRulesArray.size(); i++) {
                        ruleIds.add(discountRulesArray.get(i).getAsJsonObject().get("id").getAsLong());
                    }
                }

                // Remove all existing assignments and assign new ones
                discountRuleService.removeAllDiscountRulesFromCharge(chargeId);
                if (!ruleIds.isEmpty()) {
                    discountRuleService.assignDiscountRulesToCharge(chargeId, ruleIds);
                }
            } catch (Exception e) {
                log.error("Error updating discount rules for charge {}", chargeId, e);
                throw new RuntimeException("Failed to update discount rules: " + e.getMessage(), e);
            }
        }

        return result;
    }
}
