package com.paystack.fineract.portfolio.discount.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for auto-discovery of discount rule calculators
 * Similar to CommandType annotation pattern used in Fineract
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface DiscountRuleType {
    /**
     * Rule type identifier (e.g., "PERCENTAGE", "CUSTOMER_SEGMENT")
     */
    String value();
    
    /**
     * Rule category for grouping (e.g., "BASIC", "CUSTOMER_BASED", "TIME_BASED")
     */
    String category() default "GENERAL";
}
