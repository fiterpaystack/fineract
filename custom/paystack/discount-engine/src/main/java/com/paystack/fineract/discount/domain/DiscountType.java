package com.paystack.fineract.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Discount Type Enumeration
 * Defines the types of discounts available
 */
@Getter
@AllArgsConstructor
public enum DiscountType {
    PERCENTAGE("PERCENTAGE", "Percentage"),
    FLAT("FLAT", "Flat Amount");

    private final String code;
    private final String value;

    /**
     * Get discount type from code
     */
    public static DiscountType fromCode(String code) {
        for (DiscountType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid discount type code: " + code);
    }
}