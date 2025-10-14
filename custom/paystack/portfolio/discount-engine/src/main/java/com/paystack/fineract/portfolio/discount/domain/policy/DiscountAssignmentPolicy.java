package com.paystack.fineract.portfolio.discount.domain.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_discount_assignment_policy")
@Getter
@Setter
public class DiscountAssignmentPolicy extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 50)
    private DiscountPolicyEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "all_rules_required", nullable = false)
    private boolean allRulesRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "combination_strategy", nullable = false, length = 50)
    private DiscountCombinationStrategy combinationStrategy = DiscountCombinationStrategy.SUM_CAP;

    // Convenience getters
    public boolean isAndRequired() {
        return allRulesRequired;
    }

    public DiscountAssignmentPolicy() {
        // defaults via field initializers
    }
}
