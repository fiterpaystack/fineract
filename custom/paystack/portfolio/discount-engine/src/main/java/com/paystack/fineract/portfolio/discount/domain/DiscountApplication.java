/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.paystack.fineract.portfolio.discount.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Discount Application Entity Records each application of a discount rule
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

    @Column(name = "account_number", length = 20)
    private String accountNumber;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "discount_type", length = 50)
    private String discountType;

    @Column(name = "fee_type_category", length = 50)
    private String feeTypeCategory;

    @Column(name = "triggered_conditions", columnDefinition = "TEXT")
    private String triggeredConditions;

    /**
     * Create new discount application
     */
    public static DiscountApplication createNew(Long discountRuleId, String entityType, Long entityId, Long chargeId,
            BigDecimal originalAmount, BigDecimal discountAmount) {
        DiscountApplication application = new DiscountApplication();
        application.setDiscountRuleId(discountRuleId);
        application.setEntityType(entityType);
        application.setEntityId(entityId);
        application.setChargeId(chargeId);
        application.setOriginalAmount(originalAmount);
        application.setDiscountAmount(discountAmount);
        application.setFinalAmount(originalAmount.subtract(discountAmount));
        application.setApplicationDate(OffsetDateTime.now());
        // transaction_id will be set later after transaction is created
        application.setTransactionId(null);
        return application;
    }

    /**
     * Update transaction ID after transaction is created
     */
    public void updateTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }
}
