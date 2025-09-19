package com.paystack.fineract.portfolio.discount.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformResourceNotFoundException;

/**
 * Exception thrown when discount rule is not found
 */
public class DiscountRuleNotFoundException extends AbstractPlatformResourceNotFoundException {

    public DiscountRuleNotFoundException(final Long id) {
        super("error.msg.discountrule.id.invalid", "Discount rule with identifier " + id + " does not exist", id);
    }

    public DiscountRuleNotFoundException(final String name) {
        super("error.msg.discountrule.name.invalid", "Discount rule with name " + name + " does not exist", name);
    }
}
