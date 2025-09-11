package com.paystack.fineract.discount.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Product Discount Rule Entity
 * Represents discount rules configured for specific savings products
 */
@Entity
@Table(name = "m_product_discount_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductDiscountRule extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", scale = 6, precision = 19, nullable = false)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", scale = 6, precision = 19)
    private BigDecimal maxDiscountAmount;

    @Column(name = "conditions", columnDefinition = "json")
    private String conditionsJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private Integer priority = 0;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calculate discount amount for given original amount
     */
    public BigDecimal calculateDiscount(BigDecimal originalAmount) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal discount = BigDecimal.ZERO;

        if (discountType == DiscountType.PERCENTAGE) {
            discount = originalAmount.multiply(discountValue)
                .divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
        } else if (discountType == DiscountType.FLAT) {
            discount = discountValue;
        }

        // Apply maximum discount limit
        if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
            discount = maxDiscountAmount;
        }

        // Ensure discount doesn't exceed original amount
        if (discount.compareTo(originalAmount) > 0) {
            discount = originalAmount;
        }

        return discount;
    }

    /**
     * Get parsed conditions from JSON
     */
    public ProductDiscountConditions getConditions() {
        if (conditionsJson == null || conditionsJson.trim().isEmpty()) {
            return new ProductDiscountConditions();
        }
        
        try {
            return objectMapper.readValue(conditionsJson, ProductDiscountConditions.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse conditions JSON: " + conditionsJson, e);
        }
    }

    /**
     * Set conditions as JSON
     */
    public void setConditions(ProductDiscountConditions conditions) {
        if (conditions == null) {
            this.conditionsJson = null;
            return;
        }
        
        try {
            this.conditionsJson = objectMapper.writeValueAsString(conditions);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize conditions to JSON", e);
        }
    }

    /**
     * Check if rule is currently valid based on validity period
     */
    public boolean isValidForDate(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        
        if (validFrom != null && date.isBefore(validFrom)) {
            return false;
        }
        
        if (validTo != null && date.isAfter(validTo)) {
            return false;
        }
        
        return true;
    }
}