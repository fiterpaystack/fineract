package com.paystack.fineract.portfolio.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Discount Context
 * Contains all information needed for discount evaluation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountContext {
    
    private String entityType;
    private Long entityId;
    private Long chargeId;
    private BigDecimal originalAmount;
    private LocalDate transactionDate;
    private Long clientId;
    private Long officeId;
    private Long productId;
    private String currencyCode;
    private BigDecimal transactionAmount;
    private Long transactionId;
    private Long userId;
    private Long accountId;
}
