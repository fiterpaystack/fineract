package com.paystack.fineract.portfolio.discount.data;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
