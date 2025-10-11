package com.paystack.fineract.portfolio.discount.domain.policy;

/**
 * Strategy for combining discounts after eligibility gating.
 */
public enum DiscountCombinationStrategy {
    /**
     * Sum all individual discount amounts and cap at the original fee amount.
     */
    SUM_CAP
}
