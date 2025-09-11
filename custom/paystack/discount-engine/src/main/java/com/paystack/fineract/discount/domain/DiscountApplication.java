package com.paystack.fineract.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    @Column(name = "charge_id", nullable = false)
    private Long chargeId;

    @Column(name = "original_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal discountAmount;

    @Column(name = "application_date", nullable = false)
    private LocalDateTime applicationDate;

    /**
     * Create new discount application
     */
    public static DiscountApplication createNew(Long discountRuleId, Long chargeId,
                                               BigDecimal originalAmount, BigDecimal discountAmount) {
        DiscountApplication application = new DiscountApplication();
        application.setDiscountRuleId(discountRuleId);
        application.setChargeId(chargeId);
        application.setOriginalAmount(originalAmount);
        application.setDiscountAmount(discountAmount);
        application.setApplicationDate(LocalDateTime.now());
        return application;
    }
}