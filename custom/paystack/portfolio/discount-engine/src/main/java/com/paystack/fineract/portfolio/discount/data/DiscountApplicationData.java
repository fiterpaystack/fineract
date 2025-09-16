package com.paystack.fineract.portfolio.discount.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Discount Application Data Transfer Object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountApplicationData {
    
    private Long id;
    private Long discountRuleId;
    private String entityType;
    private Long entityId;
    private Long chargeId;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private OffsetDateTime applicationDate;
    private Long transactionId;
    private OffsetDateTime createdDate;
    private OffsetDateTime lastModifiedDate;
    private Long createdBy;
    private Long lastModifiedBy;
    
    // Additional fields for display
    private String ruleName;
    private String entityName;
    private String chargeName;
}
