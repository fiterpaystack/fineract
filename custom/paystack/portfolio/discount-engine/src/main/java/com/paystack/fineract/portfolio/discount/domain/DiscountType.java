package com.paystack.fineract.portfolio.discount.domain;

/**
 * Discount Type Enumeration Defines the types of discounts available
 */
public enum DiscountType {

    PERCENTAGE("Percentage"), FLAT("Flat Amount");

    private final String displayName;

    DiscountType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
