package com.paystack.fineract.portfolio.charge.service;

import com.google.gson.JsonArray;
import com.paystack.fineract.portfolio.discount.service.DiscountRuleService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.entityaccess.service.FineractEntityAccessUtil;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.charge.domain.ChargeSlab;
import org.apache.fineract.portfolio.charge.domain.ChargeSlabRepository;
import org.apache.fineract.portfolio.charge.serialization.ChargeDefinitionCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.charge.service.ChargeWritePlatformServiceJpaRepositoryImpl;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProductRepository;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeRepositoryWrapper;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
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
public class PaystackChargeWritePlatformServiceImpl extends ChargeWritePlatformServiceJpaRepositoryImpl {

    private final ChargeRepository chargeRepository;
    private final ChargeSlabRepository chargeSlabRepository;
    private final ChargeDefinitionCommandFromApiJsonDeserializer fromApiJsonDeserializer;
    private final DiscountRuleService discountRuleService;
    private final TaxGroupRepositoryWrapper taxGroupRepository;
    private final JdbcTemplate jdbcTemplate;

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
        this.taxGroupRepository = taxGroupRepository;
        this.jdbcTemplate = jdbcTemplate;
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

        // If taxGroupId is explicitly present, handle update/removal here to bypass core immutability
        if (command.parameterExists("taxGroupId")) {
            // Basic validation on other fields (keeps parity with core validation)
            this.fromApiJsonDeserializer.validateForUpdate(command.json());

            Optional<Charge> optionalCharge = chargeRepository.findById(chargeId);
            if (optionalCharge.isEmpty()) {
                // Delegate to super to throw consistent not-found exception
                return super.updateCharge(chargeId, command);
            }

            Charge chargeForUpdate = optionalCharge.get();

            Long previousTaxGroupId = chargeForUpdate.getTaxGroup() != null ? chargeForUpdate.getTaxGroup().getId() : null;
            Long requestedTaxGroupId = null;
            try {
                requestedTaxGroupId = command.longValueOfParameterNamed("taxGroupId");
            } catch (Exception ignore) {
                // leave as null if unable to parse; null means removal when parameter present
            }

            if (Objects.equals(previousTaxGroupId, requestedTaxGroupId)) {
                // No change, fall back to core to process other fields
                return super.updateCharge(chargeId, command);
            }

            // Config gating
            if (!isConfigEnabled("allow-charge-taxgroup-edit", true)) {
                final List<ApiParameterError> errors = new ArrayList<>();
                new DataValidatorBuilder(errors).resource("charges").parameter("taxGroupId")
                        .failWithCodeNoParameterAddedToErrorCode("editing.taxgroup.disabled");
                throw new PlatformApiDataValidationException(errors);
            }

            // Usage checks (loans, savings, client, share charges)
            long usageCount = countChargeUsage(chargeId);
            if (usageCount > 0 && !isConfigEnabled("allow-charge-taxgroup-edit-if-used", false)) {
                final List<ApiParameterError> errors = new ArrayList<>();
                new DataValidatorBuilder(errors).resource("charges").parameter("taxGroupId")
                        .failWithCodeNoParameterAddedToErrorCode("editing.taxgroup.not.allowed.when.used");
                throw new PlatformApiDataValidationException(errors);
            }

            TaxGroup newTaxGroup = null;
            if (requestedTaxGroupId != null) {
                newTaxGroup = this.taxGroupRepository.findOneWithNotFoundDetection(requestedTaxGroupId);
            }

            chargeForUpdate.setTaxGroup(newTaxGroup);
            this.chargeRepository.save(chargeForUpdate);

            // Build changes map including audit-friendly fields and warnings
            Map<String, Object> changes = new LinkedHashMap<>();
            changes.put("previousTaxGroupId", previousTaxGroupId);
            changes.put("newTaxGroupId", requestedTaxGroupId);
            if (usageCount > 0) {
                List<String> warnings = new ArrayList<>();
                warnings.add("Charge has been used in " + usageCount + " historical records. No retroactive changes.");
                changes.put("warnings", warnings);
            }

            return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(chargeId).with(changes).build();
        }

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

    private long countChargeUsage(Long chargeId) {
        long total = 0;
        total += safeCount("select count(1) from m_loan_charge where charge_id = ?", chargeId);
        total += safeCount("select count(1) from m_savings_account_charge where charge_id = ?", chargeId);
        total += safeCount("select count(1) from m_client_charge where charge_id = ?", chargeId);
        total += safeCount("select count(1) from m_share_account_charge where charge_id = ?", chargeId);
        return total;
    }

    private long safeCount(String sql, Long id) {
        try {
            Long cnt = this.jdbcTemplate.queryForObject(sql, Long.class, id);
            return cnt != null ? cnt : 0L;
        } catch (Exception e) {
            log.warn("Count query failed: {} -- {}", sql, e.getMessage());
            return 0L;
        }
    }

    private boolean isConfigEnabled(String name, boolean defaultValue) {
        try {
            Boolean val = this.jdbcTemplate.queryForObject(
                    "select coalesce(value, default_value) from c_configuration where name = ?", Boolean.class, name);
            return val != null ? val : defaultValue;
        } catch (Exception e) {
            log.warn("Could not read config {}: {} -- using default {}", name, e.getMessage(), defaultValue);
            return defaultValue;
        }
    }
}
