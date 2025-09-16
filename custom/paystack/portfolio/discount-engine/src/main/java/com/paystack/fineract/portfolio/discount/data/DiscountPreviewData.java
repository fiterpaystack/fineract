package com.paystack.fineract.portfolio.discount.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Discount Preview Data Transfer Object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountPreviewData {
    
    private BigDecimal originalAmount;
    private BigDecimal totalDiscountAmount;
    private BigDecimal finalAmount;
    private Integer applicableRulesCount;
    private String currencyCode;
}
