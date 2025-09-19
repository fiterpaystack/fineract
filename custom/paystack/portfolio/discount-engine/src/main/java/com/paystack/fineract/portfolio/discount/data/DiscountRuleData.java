package com.paystack.fineract.portfolio.discount.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discount Rule Data Transfer Object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountRuleData {

    private Long id;
    private String name;
    private String description;
    private boolean active;
    private Integer rulePriority;
    private String ruleType;
    private String ruleParametersJson;
    private OffsetDateTime createdOnUtc;
    private OffsetDateTime lastModifiedOnUtc;
    private Long createdBy;
    private Long lastModifiedBy;

    // Additional fields for display
    private Long applicationCount;
    private BigDecimal totalDiscountAmount;
}
