package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.ChargeDiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountPolicyEntityType;
import com.paystack.fineract.portfolio.discount.factory.DiscountRuleCalculatorFactory;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product Discount Service Handles discount application logic for products and charges Implements charge-first
 * priority: charge rules take precedence over product rules Uses the consolidated DiscountRuleService for rule
 * management
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDiscountService {

    private final DiscountRuleService discountRuleService;
    private final DiscountRuleCalculatorFactory calculatorFactory;
    private final DiscountAssignmentPolicyService policyService;
    private final DiscountApplicationService discountApplicationService;
    private final ThreadLocal<Set<String>> appliedDiscounts = ThreadLocal.withInitial(HashSet::new);

    /**
     * Apply discount to an amount based on discount context
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(BigDecimal originalAmount, com.paystack.fineract.portfolio.discount.domain.DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }

        if (context == null || context.getProductId() == null) {
            return originalAmount;
        }

        return applyDiscount(context.getProductId(), originalAmount, context.getChargeId());
    }

    /**
     * Apply discount for a specific product and charge Implements charge-first priority: check charge rules first, fall
     * back to product rules Uses the new calculator system when available FIXED: Added proper ThreadLocal cleanup to
     * prevent memory leaks
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(Long productId, BigDecimal originalAmount, Long chargeId) {
        return applyDiscount(productId, originalAmount, chargeId, null);
    }

    /**
     * Apply discount for a specific product and charge with account context Implements charge-first priority: check
     * charge rules first, fall back to product rules Uses the new calculator system when available FIXED: Added proper
     * ThreadLocal cleanup to prevent memory leaks
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(Long productId, BigDecimal originalAmount, Long chargeId, Long accountId) {
        String discountKey = productId + ":" + chargeId + ":" + originalAmount;

        try {
            if (appliedDiscounts.get().contains(discountKey)) {
                warnDuplicate(productId, chargeId, originalAmount);
                return originalAmount;
            }

            appliedDiscounts.get().add(discountKey);

            // Create discount context for the new calculator system
            DiscountContext context = createDiscountContext(productId, chargeId, originalAmount, accountId);
            // 1. Check for charge-level rules first
            List<DiscountRule> chargeRules = discountRuleService.getAssignedDiscountRules("CHARGE", chargeId);

            if (!chargeRules.isEmpty()) {
                // For charge-level rules, we need to fetch Charge entity for logging
                // Since we don't have it here, pass null and it will be fetched in the service if needed
                return applyRulesWithCalculator(chargeRules, originalAmount, context, "CHARGE", chargeId, null);
            }

            // 2. Fall back to product-level rules
            return applyRulesWithCalculatorForProduct(productId, originalAmount, context, null);

        } finally {
            // Clean up ThreadLocal after each discount application to prevent memory leaks
            appliedDiscounts.remove();
        }
    }

    public void clearAppliedDiscounts() {
        appliedDiscounts.remove();
    }

    @PreDestroy
    public void cleanup() {
        appliedDiscounts.remove();
    }

    /**
     * Apply discount using a ChargeDiscountContext to carry all inputs (account, charge, amount, date).
     */
    @Transactional(readOnly = true)
    public BigDecimal applyDiscount(ChargeDiscountContext ctx) {
        if (ctx == null || ctx.originalAmount() == null || ctx.originalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ctx != null ? ctx.originalAmount() : BigDecimal.ZERO;
        }

        Long productId = ctx.account() != null ? ctx.account().productId() : null;
        Long chargeId = ctx.charge() != null ? ctx.charge().getCharge().getId() : null;
        Long accountId = ctx.account() != null ? ctx.account().getId() : null;

        if (productId == null) {
            return ctx.originalAmount();
        }

        String discountKey = productId + ":" + chargeId + ":" + ctx.originalAmount();
        try {
            if (appliedDiscounts.get().contains(discountKey)) {
                warnDuplicate(productId, chargeId, ctx.originalAmount());
                return ctx.originalAmount();
            }

            appliedDiscounts.get().add(discountKey);

            DiscountContext context = new DiscountContext();
            context.setProductId(productId);
            context.setChargeId(chargeId);
            context.setTransactionAmount(ctx.originalAmount());
            context.setTransactionDate(ctx.transactionDate() != null ? ctx.transactionDate() : DateUtils.getBusinessLocalDate());
            context.setAccountId(accountId);

            List<DiscountRule> chargeRules = discountRuleService.getAssignedDiscountRules("CHARGE", chargeId);
            if (!chargeRules.isEmpty()) {
                // Get Charge entity from context for logging
                org.apache.fineract.portfolio.charge.domain.Charge charge = ctx.charge() != null 
                        ? ctx.charge().getCharge() : null;
                return applyRulesWithCalculator(chargeRules, ctx.originalAmount(), context, "CHARGE", chargeId, charge);
            }

            // For product-level rules, we don't have direct charge access, so pass null
            return applyRulesWithCalculatorForProduct(productId, ctx.originalAmount(), context, 
                    ctx.charge() != null ? ctx.charge().getCharge() : null);
        } finally {
            appliedDiscounts.remove();
        }
    }

    /**
     * Create discount context for calculator system
     */
    private DiscountContext createDiscountContext(Long productId, Long chargeId, BigDecimal originalAmount, Long accountId) {
        DiscountContext context = new DiscountContext();
        context.setProductId(productId);
        context.setChargeId(chargeId);
        context.setTransactionAmount(originalAmount);
        context.setTransactionDate(DateUtils.getBusinessLocalDate());
        context.setAccountId(accountId); // Set the account ID for balance-based calculations
        // Add more context fields as needed
        return context;
    }

    /**
     * Apply discount rules to an amount using the new calculator system with policy-based AND gating and combination.
     * Saves discount application records for audit trail.
     */
    private BigDecimal applyRulesWithCalculator(List<DiscountRule> rules, BigDecimal originalAmount, DiscountContext context,
            String entityType, Long entityId, org.apache.fineract.portfolio.charge.domain.Charge charge) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }

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

        // Calculate discounts for applicable rules and save audit records
        BigDecimal totalDiscount = BigDecimal.ZERO;
        java.time.LocalDate evaluationDate = context.getTransactionDate() != null ? context.getTransactionDate()
                : DateUtils.getBusinessLocalDate();

        Long chargeId = context.getChargeId() != null ? context.getChargeId() : charge != null ? charge.getId() : null;

        for (DiscountRule rule : rules) {
            if (rule.isValidForDate(evaluationDate)) {
                BigDecimal discountAmount = calculateDiscountWithRule(rule, originalAmount, context);
                if (discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    totalDiscount = totalDiscount.add(discountAmount);
                    
                    // Save discount application record for audit trail (one record per rule)
                    if (chargeId != null) {
                        discountApplicationService.saveDiscountApplication(
                                rule, entityType, entityId, chargeId, originalAmount, discountAmount, context, charge);
                    }
                }
            }
        }

        // Apply combination strategy
        BigDecimal finalDiscount = applyCombinationStrategy(totalDiscount, originalAmount, policy.getCombinationStrategy());
        return originalAmount.subtract(finalDiscount);
    }

    /**
     * Apply discount rules for product-level (fallback when no charge rules)
     */
    private BigDecimal applyRulesWithCalculatorForProduct(Long productId, BigDecimal originalAmount, DiscountContext context,
            org.apache.fineract.portfolio.charge.domain.Charge charge) {
        // Get product-level rules and apply them
        List<DiscountRule> productRules = discountRuleService.getAssignedDiscountRules("SAVINGS_PRODUCT", productId);
        
        if (productRules.isEmpty()) {
            return originalAmount;
        }

        return applyRulesWithCalculator(productRules, originalAmount, context, "SAVINGS_PRODUCT", productId, charge);
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
     * Calculate discount for a specific rule using the calculator system FIXED: Eliminated circular dependency by using
     * direct calculator invocation
     */
    private BigDecimal calculateDiscountWithRule(DiscountRule rule, BigDecimal originalAmount, DiscountContext context) {
        if (rule.getRuleType() != null && rule.getRuleParametersJson() != null) {
            try {
                // Direct calculator usage - no recursive service calls
                DiscountRuleCalculator calculator = calculatorFactory.createCalculator(rule.getRuleType(), rule.getRuleParameters());

                if (calculator.isApplicable(context) && calculator.isValid(context)) {
                    return calculator.calculateDiscount(originalAmount, context);
                }
            } catch (Exception e) {
                log.warn("DISCOUNT ENGINE: Failed to use calculator for rule {}: {}", rule.getName(), e.getMessage());
            }
        }

        // Fall back to zero if calculator fails
        return BigDecimal.ZERO;
    }

    private void warnDuplicate(Long productId, Long chargeId, BigDecimal amount) {
        log.warn("DISCOUNT ENGINE: Duplicate discount application detected for product: {}, charge: {}, amount: {}", productId, chargeId,
                amount);
    }

}
