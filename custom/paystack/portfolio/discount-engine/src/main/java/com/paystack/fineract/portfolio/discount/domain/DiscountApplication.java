package com.paystack.fineract.portfolio.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Discount Application Entity
 * Records each application of a discount rule
 */
@Entity
@Table(name = "m_discount_application")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscountApplication extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "discount_rule_id", nullable = false)
    private Long discountRuleId;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "charge_id", nullable = false)
    private Long chargeId;

    @Column(name = "original_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "final_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal finalAmount;

    @Column(name = "application_date", nullable = false)
    private OffsetDateTime applicationDate;

    @Column(name = "transaction_id")
    private Long transactionId;

    /**
     * Create new discount application
     */
    public static DiscountApplication createNew(Long discountRuleId, String entityType, Long entityId, 
                                               Long chargeId, BigDecimal originalAmount, BigDecimal discountAmount) {
        DiscountApplication application = new DiscountApplication();
        application.setDiscountRuleId(discountRuleId);
        application.setEntityType(entityType);
        application.setEntityId(entityId);
        application.setChargeId(chargeId);
        application.setOriginalAmount(originalAmount);
        application.setDiscountAmount(discountAmount);
        application.setFinalAmount(originalAmount.subtract(discountAmount));
        application.setApplicationDate(OffsetDateTime.now());
        return application;
    }
}
