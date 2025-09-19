package com.paystack.fineract.portfolio.discount.calculator;

import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for discount rule calculators Each implementation handles a specific type of discount calculation
 */
public interface DiscountRuleCalculator {

    /**
     * Get the rule type identifier
     */
    String getRuleType();

    /**
     * Get the rule category for grouping
     */
    String getRuleCategory();

    /**
     * Get human-readable description of the rule
     */
    String getRuleDescription();

    /**
     * Get list of required parameters for this rule type
     */
    List<String> getRequiredParameters();

    /**
     * Get list of optional parameters for this rule type
     */
    List<String> getOptionalParameters();

    /**
     * Get descriptions of all parameters
     */
    Map<String, String> getParameterDescriptions();

    /**
     * Calculate discount amount for given original amount and context
     */
    BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context);

    /**
     * Check if this rule is applicable for the given context
     */
    boolean isApplicable(DiscountContext context);

    /**
     * Validate if the rule is properly configured
     */
    boolean isValid(DiscountContext context);

    /**
     * Configure the calculator with parameters
     */
    void configure(Map<String, Object> parameters);
}
