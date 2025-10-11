package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.data.DiscountAssignmentPolicyData;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleAssignmentData;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleData;
import com.paystack.fineract.portfolio.discount.data.DiscountRuleTypeInfo;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountPolicyEntityType;
import com.paystack.fineract.portfolio.discount.factory.DiscountRuleCalculatorFactory;
import com.paystack.fineract.portfolio.discount.repository.DiscountRuleRepository;
import com.paystack.fineract.portfolio.discount.repository.DiscountRuleRepositoryWrapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final DiscountRuleRepository discountRuleRepository;
    private final ChargeRepository chargeRepository;
    private final SavingsProductRepository savingsProductRepository;
    private final DiscountRuleCalculatorFactory calculatorFactory;
    private final DiscountAssignmentPolicyService policyService;

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

        int removedCount = discountRuleRepository.deleteAssignmentsByCharge(chargeId);
        if (removedCount > 0) {
            policyService.deletePolicyIfExists(DiscountPolicyEntityType.CHARGE, chargeId);
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

        int removedCount = discountRuleRepository.deleteAssignmentsByProduct(productId);
        if (removedCount > 0) {
            policyService.deletePolicyIfExists(DiscountPolicyEntityType.SAVINGS_PRODUCT, productId);
        }
    }

    /**
     * Get assigned discount rules for an entity
     */
    @Transactional(readOnly = true)
    public List<DiscountRule> getAssignedDiscountRules(DiscountPolicyEntityType entityType, Long entityId) {
        return switch (entityType) {
            case CHARGE -> discountRuleRepository.findActiveByChargeOrdered(entityId);
            case SAVINGS_PRODUCT -> discountRuleRepository.findActiveByProductOrdered(entityId);
        };
    }

    /**
     * Backward-compatible variant using String; prefer enum overload.
     */
    public List<DiscountRule> getAssignedDiscountRules(String entityType, Long entityId) {
        if (entityType == null) {
            return List.of();
        }
        try {
            DiscountPolicyEntityType type = DiscountPolicyEntityType.valueOf(entityType);
            return getAssignedDiscountRules(type, entityId);
        } catch (IllegalArgumentException ex) {
            // Support case-insensitive inputs like "SAVINGS_PRODUCT"
            String normalized = entityType.trim().toUpperCase();
            try {
                DiscountPolicyEntityType type = DiscountPolicyEntityType.valueOf(normalized);
                return getAssignedDiscountRules(type, entityId);
            } catch (Exception ignore) {
                return List.of();
            }
        }
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
     * Get assignment data for a charge with priority and policy information
     */
    @Transactional(readOnly = true)
    public List<DiscountRuleAssignmentData> getAssignmentDataForCharge(Long chargeId) {
        List<Object[]> results = discountRuleRepository.findAssignmentDataByCharge(chargeId);
        return results.stream().map(this::mapToAssignmentData).peek(data -> {
            data.setEntityType(DiscountPolicyEntityType.CHARGE.name());
            data.setEntityId(chargeId);
        }).toList();
    }

    /**
     * Get assignment data for a product with priority and policy information
     */
    @Transactional(readOnly = true)
    public List<DiscountRuleAssignmentData> getAssignmentDataForProduct(Long productId) {
        List<Object[]> results = discountRuleRepository.findAssignmentDataByProduct(productId);
        return results.stream().map(this::mapToAssignmentData).peek(data -> {
            data.setEntityType(DiscountPolicyEntityType.SAVINGS_PRODUCT.name());
            data.setEntityId(productId);
        }).toList();
    }

    /**
     * Get policy data for an entity
     */
    @Transactional(readOnly = true)
    public DiscountAssignmentPolicyData getPolicyDataForEntity(DiscountPolicyEntityType entityType, Long entityId) {
        DiscountAssignmentPolicy policy = policyService.resolvePolicyOrDefault(entityType, entityId);
        return mapToPolicyData(policy, entityType, entityId);
    }

    /**
     * Map raw query result to assignment data
     */
    private DiscountRuleAssignmentData mapToAssignmentData(Object[] row) {
        return DiscountRuleAssignmentData.builder().ruleId(getLong(row[0])).ruleName(getString(row[1])).ruleDescription(getString(row[2]))
                .active(getBoolean(row[3])).rulePriority(getInteger(row[4])).ruleType(getString(row[5]))
                .ruleParametersJson(getString(row[6])).createdOnUtc(getOffsetDateTime(row[7])).lastModifiedOnUtc(getOffsetDateTime(row[8]))
                .createdBy(getLong(row[9])).lastModifiedBy(getLong(row[10])).assignmentPriority(getInteger(row[11])).build();
    }

    /**
     * Map policy entity to policy data
     */
    private DiscountAssignmentPolicyData mapToPolicyData(DiscountAssignmentPolicy policy, DiscountPolicyEntityType entityType,
            Long entityId) {
        return DiscountAssignmentPolicyData.builder().id(policy.getId()).entityType(entityType.name()).entityId(entityId)
                .allRulesRequired(policy.isAndRequired()).combinationStrategy(policy.getCombinationStrategy().name())
                .createdOnUtc(policy.getCreatedDate().orElse(null)).lastModifiedOnUtc(policy.getLastModifiedDate().orElse(null))
                .createdBy(policy.getCreatedBy().orElse(null)).lastModifiedBy(policy.getLastModifiedBy().orElse(null)).build();
    }

    // Helper methods for safe type conversion
    private Long getLong(Object value) {
        return value != null ? ((Number) value).longValue() : null;
    }

    private String getString(Object value) {
        return value != null ? value.toString() : null;
    }

    private Boolean getBoolean(Object value) {
        return value != null ? (Boolean) value : false;
    }

    private Integer getInteger(Object value) {
        return value != null ? ((Number) value).intValue() : null;
    }

    private OffsetDateTime getOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime) {
            return (OffsetDateTime) value;
        } else if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).atOffset(ZoneOffset.UTC);
        }
        return null;
    }

    /**
     * Apply discount using the new calculator system with policy-based AND gating and combination.
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

        // Resolve policy for the target entity
        DiscountPolicyEntityType policyEntityType = "CHARGE".equals(entityType) ? DiscountPolicyEntityType.CHARGE
                : DiscountPolicyEntityType.SAVINGS_PRODUCT;
        DiscountAssignmentPolicy policy = policyService.resolvePolicyOrDefault(policyEntityType, entityId);

        // If AND is required, check all rules are applicable/valid
        if (policy.isAndRequired()) {
            for (DiscountRule rule : rules) {
                if (!isRuleApplicableAndValid(rule, context)) {
                    log.debug("AND policy requires all rules to be applicable; rule {} failed, returning original amount", rule.getName());
                    return originalAmount;
                }
            }
        }

        // Calculate discounts for applicable rules
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

        // Apply combination strategy
        BigDecimal finalDiscount = applyCombinationStrategy(totalDiscount, originalAmount, policy.getCombinationStrategy());
        return originalAmount.subtract(finalDiscount);
    }

    /**
     * Check if rule is both applicable and valid for the context.
     */
    private boolean isRuleApplicableAndValid(DiscountRule rule, DiscountContext context) {
        if (rule.getRuleType() != null && rule.getRuleParametersJson() != null) {
            try {
                DiscountRuleCalculator calculator = calculatorFactory.createCalculator(rule.getRuleType(), rule.getRuleParameters());
                return calculator.isApplicable(context) && calculator.isValid(context);
            } catch (Exception e) {
                log.warn("Failed to check rule {} applicability: {}", rule.getName(), e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Apply combination strategy to total discount amount.
     */
    private BigDecimal applyCombinationStrategy(BigDecimal totalDiscount, BigDecimal originalAmount, DiscountCombinationStrategy strategy) {
        return switch (strategy) {
            case SUM_CAP -> totalDiscount.min(originalAmount);
        };
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
                log.warn("Failed to use calculator for rule {}: {}", rule.getName(), e.getMessage());
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
