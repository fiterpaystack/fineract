package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleTypeInfo;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.factory.DiscountRuleCalculatorFactory;
import com.paystack.fineract.portfolio.discount.repository.DiscountRuleRepositoryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.common.service.Validator;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consolidated Discount Rule Service following core Fineract patterns. Handles all discount rule operations including
 * CRUD and assignments.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountRuleService {

    private final DiscountRuleRepositoryWrapper repositoryWrapper;
    private final ChargeRepository chargeRepository;
    private final SavingsProductRepository savingsProductRepository;
    private final DiscountRuleCalculatorFactory calculatorFactory;

    /**
     * Create a new discount rule from JsonCommand
     */
    @Transactional
    public CommandProcessingResult createDiscountRule(JsonCommand command) {

        // Extract data from JsonCommand
        DiscountRuleData ruleData = extractDiscountRuleDataFromCommand(command);
        return createDiscountRule(ruleData);
    }

    /**
     * Create a new discount rule
     */
    @Transactional
    public CommandProcessingResult createDiscountRule(DiscountRuleData ruleData) {

        // Validate input data
        validateDiscountRuleData(ruleData);

        DiscountRule rule = new DiscountRule();
        rule.setName(ruleData.getName());
        rule.setDescription(ruleData.getDescription());
        rule.setActive(ruleData.isActive());
        rule.setRulePriority(ruleData.getRulePriority() != null ? ruleData.getRulePriority() : 0);
        rule.setRuleType(ruleData.getRuleType());
        rule.setRuleParametersJson(ruleData.getRuleParametersJson());

        DiscountRule savedRule = repositoryWrapper.save(rule);

        return new CommandProcessingResultBuilder().withEntityId(savedRule.getId()).withCommandId(null).build();
    }

    /**
     * Update an existing discount rule from JsonCommand
     */
    @Transactional
    public CommandProcessingResult updateDiscountRule(Long ruleId, JsonCommand command) {

        // Extract data from JsonCommand
        DiscountRuleData ruleData = extractDiscountRuleDataFromCommand(command);
        return updateDiscountRule(ruleId, ruleData);
    }

    /**
     * Update an existing discount rule
     */
    @Transactional
    public CommandProcessingResult updateDiscountRule(Long ruleId, DiscountRuleData ruleData) {

        DiscountRule rule = repositoryWrapper.findOneWithNotFoundDetection(ruleId);

        // Validate input data
        validateDiscountRuleData(ruleData);

        rule.setName(ruleData.getName());
        rule.setDescription(ruleData.getDescription());
        rule.setActive(ruleData.isActive());
        rule.setRulePriority(ruleData.getRulePriority());
        rule.setRuleType(ruleData.getRuleType());
        rule.setRuleParametersJson(ruleData.getRuleParametersJson());

        DiscountRule savedRule = repositoryWrapper.save(rule);

        return new CommandProcessingResultBuilder().withEntityId(savedRule.getId()).withCommandId(null).build();
    }

    /**
     * Delete a discount rule (soft delete)
     */
    @Transactional
    public CommandProcessingResult deleteDiscountRule(Long ruleId) {
        DiscountRule rule = repositoryWrapper.findOneWithNotFoundDetection(ruleId);

        rule.setActive(false);
        repositoryWrapper.save(rule);

        return new CommandProcessingResultBuilder().withEntityId(ruleId).withCommandId(null).build();
    }

    /**
     * Assign discount rules to a charge
     */
    @Transactional
    public void assignDiscountRulesToCharge(Long chargeId, List<Long> ruleIds) {

        // Validate charge exists
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("Charge not found: " + chargeId));

        // Validate all discount rules exist and are active
        repositoryWrapper.validateAllExistAndActive(ruleIds);

        // Get all discount rules
        List<DiscountRule> rules = ruleIds.stream().map(repositoryWrapper::findOneWithNotFoundDetection).collect(Collectors.toList());

        // Assign each rule to the charge
        int assignedCount = 0;
        int skippedCount = 0;

        for (DiscountRule rule : rules) {
            try {
                rule.assignToCharge(charge);
                repositoryWrapper.save(rule);
                assignedCount++;
            } catch (IllegalArgumentException e) {
                skippedCount++;
                log.warn("Discount rule {} already assigned to charge {}: {}", rule.getId(), chargeId, e.getMessage());
            }
        }
    }

    /**
     * Remove all discount rules from a charge
     */
    @Transactional
    public void removeAllDiscountRulesFromCharge(Long chargeId) {

        // Validate charge exists
        Charge charge = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new IllegalArgumentException("Charge not found: " + chargeId));

        // Find all discount rules assigned to this charge
        List<DiscountRule> allRules = repositoryWrapper.findAllActive();

        int removedCount = 0;

        for (DiscountRule rule : allRules) {
            if (rule.isAssignedToCharge(chargeId)) {
                rule.unassignFromCharge(charge);
                repositoryWrapper.save(rule);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            // Rules were removed
        }
    }

    /**
     * Assign discount rules to a product
     */
    @Transactional
    public void assignDiscountRulesToProduct(Long productId, List<Long> ruleIds) {

        // Validate product exists
        SavingsProduct product = savingsProductRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Validate all discount rules exist and are active
        repositoryWrapper.validateAllExistAndActive(ruleIds);

        // Get all discount rules
        List<DiscountRule> rules = ruleIds.stream().map(repositoryWrapper::findOneWithNotFoundDetection).collect(Collectors.toList());

        // Assign each rule to the product
        for (DiscountRule rule : rules) {
            try {
                rule.assignToProduct(product);
                repositoryWrapper.save(rule);
            } catch (IllegalArgumentException e) {
                log.warn("Discount rule {} already assigned to product {}: {}", rule.getId(), productId, e.getMessage());
            }
        }
    }

    /**
     * Remove all discount rules from a product
     */
    @Transactional
    public void removeAllDiscountRulesFromProduct(Long productId) {

        // Validate product exists
        SavingsProduct product = savingsProductRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        // Find all discount rules assigned to this product
        List<DiscountRule> allRules = repositoryWrapper.findAllActive();
        int removedCount = 0;

        for (DiscountRule rule : allRules) {
            if (rule.isAssignedToProduct(productId)) {
                rule.unassignFromProduct(product);
                repositoryWrapper.save(rule);
                removedCount++;
            }
        }

    }

    /**
     * Get assigned discount rules for an entity
     */
    @Transactional(readOnly = true)
    public List<DiscountRule> getAssignedDiscountRules(String entityType, Long entityId) {
        List<DiscountRule> allRules = repositoryWrapper.findAllActive();

        return allRules.stream().filter(rule -> {
            if ("CHARGE".equals(entityType)) {
                return rule.isAssignedToCharge(entityId);
            } else if ("SAVINGS_PRODUCT".equals(entityType)) {
                return rule.isAssignedToProduct(entityId);
            }
            return false;
        }).collect(Collectors.toList());
    }

    /**
     * Validate discount rule data using core Fineract validation
     */
    private void validateDiscountRuleData(DiscountRuleData ruleData) {
        Validator.validateOrThrow("discountrule", baseDataValidator -> {
            baseDataValidator.reset().parameter("name").value(ruleData.getName()).notBlank().notExceedingLengthOf(100);
            baseDataValidator.reset().parameter("ruleType").value(ruleData.getRuleType()).notBlank();

            if (ruleData.getRulePriority() != null) {
                baseDataValidator.reset().parameter("rulePriority").value(ruleData.getRulePriority()).integerGreaterThanZero();
            }
        });
    }

    /**
     * Map domain entity to data transfer object
     */
    public DiscountRuleData mapToData(DiscountRule rule) {
        DiscountRuleData data = new DiscountRuleData();
        data.setId(rule.getId());
        data.setName(rule.getName());
        data.setDescription(rule.getDescription());
        data.setActive(rule.isActive());
        data.setRulePriority(rule.getRulePriority());
        data.setRuleType(rule.getRuleType());
        data.setRuleParametersJson(rule.getRuleParametersJson());
        data.setCreatedOnUtc(rule.getCreatedDate().orElse(null));
        data.setLastModifiedOnUtc(rule.getLastModifiedDate().orElse(null));
        data.setCreatedBy(rule.getCreatedBy().orElse(null));
        data.setLastModifiedBy(rule.getLastModifiedBy().orElse(null));
        return data;
    }

    /**
     * Apply discount using the new calculator system
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscountWithCalculator(String entityType, Long entityId, BigDecimal originalAmount, DiscountContext context) {

        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }

        List<DiscountRule> rules = getAssignedDiscountRules(entityType, entityId);

        if (rules.isEmpty()) {
            return originalAmount;
        }

        BigDecimal totalDiscount = BigDecimal.ZERO;
        LocalDate evaluationDate = LocalDate.now();

        for (DiscountRule rule : rules) {
            if (rule.isValidForDate(evaluationDate)) {
                BigDecimal discountAmount = calculateDiscountWithRule(rule, originalAmount, context);
                if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    totalDiscount = totalDiscount.add(discountAmount);
                }
            }
        }

        BigDecimal finalAmount = originalAmount.subtract(totalDiscount);

        return finalAmount;
    }

    /**
     * Calculate discount for a specific rule using the calculator system
     */
    private BigDecimal calculateDiscountWithRule(DiscountRule rule, BigDecimal originalAmount, DiscountContext context) {
        // Try new calculator system first
        if (rule.getRuleType() != null && rule.getRuleParametersJson() != null) {
            try {
                DiscountRuleCalculator calculator = calculatorFactory.createCalculator(rule.getRuleType(), rule.getRuleParameters());

                if (calculator.isApplicable(context) && calculator.isValid(context)) {
                    return calculator.calculateDiscount(originalAmount, context);
                }
            } catch (Exception e) {
                log.warn("Failed to use calculator for rule {}: {} - Exception: {} - Stack trace:", rule.getName(), e.getMessage(),
                        e.getClass().getSimpleName(), e);
            }
        }

        // No calculator available or calculator failed - return zero discount
        return BigDecimal.ZERO;
    }

    /**
     * Get available discount rule types
     */
    public List<DiscountRuleTypeInfo> getAvailableRuleTypes() {
        List<DiscountRuleTypeInfo> ruleTypes = new ArrayList<>();

        for (String ruleType : calculatorFactory.getAvailableRuleTypes()) {
            DiscountRuleCalculator template = calculatorFactory.getCalculatorTemplate(ruleType);
            if (template != null) {
                DiscountRuleTypeInfo info = new DiscountRuleTypeInfo();
                info.setRuleType(template.getRuleType());
                info.setCategory(template.getRuleCategory());
                info.setDescription(template.getRuleDescription());
                info.setRequiredParameters(template.getRequiredParameters());
                info.setOptionalParameters(template.getOptionalParameters());
                info.setParameterDescriptions(template.getParameterDescriptions());
                ruleTypes.add(info);
            }
        }

        return ruleTypes;
    }

    /**
     * Get rule type information for a specific type
     */
    public DiscountRuleTypeInfo getRuleTypeInfo(String ruleType) {
        DiscountRuleCalculator template = calculatorFactory.getCalculatorTemplate(ruleType);
        if (template == null) {
            throw new IllegalArgumentException("Unknown rule type: " + ruleType);
        }

        DiscountRuleTypeInfo info = new DiscountRuleTypeInfo();
        info.setRuleType(template.getRuleType());
        info.setCategory(template.getRuleCategory());
        info.setDescription(template.getRuleDescription());
        info.setRequiredParameters(template.getRequiredParameters());
        info.setOptionalParameters(template.getOptionalParameters());
        info.setParameterDescriptions(template.getParameterDescriptions());
        return info;
    }

    /**
     * Extract DiscountRuleData from JsonCommand
     */
    private DiscountRuleData extractDiscountRuleDataFromCommand(JsonCommand command) {
        DiscountRuleData ruleData = new DiscountRuleData();

        ruleData.setName(command.stringValueOfParameterNamed("name"));
        ruleData.setDescription(command.stringValueOfParameterNamed("description"));
        ruleData.setRuleType(command.stringValueOfParameterNamed("ruleType"));
        ruleData.setRuleParametersJson(command.stringValueOfParameterNamed("ruleParametersJson"));
        ruleData.setActive(command.booleanPrimitiveValueOfParameterNamed("active"));
        ruleData.setRulePriority(command.integerValueSansLocaleOfParameterNamed("rulePriority"));

        return ruleData;
    }
}
