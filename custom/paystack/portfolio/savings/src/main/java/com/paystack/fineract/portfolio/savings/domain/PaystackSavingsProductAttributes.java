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
package com.paystack.fineract.portfolio.savings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * EMT (Electronic Money Transfer) levy configuration for a Savings Product. Externalizes EMT related attributes that
 * were previously stored directly on the savings product table.
 */
@Entity
@Table(name = "m_savings_product_attributes", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "savings_product_id" }, name = "unq_paystack_emt_config_product") })
@Getter
@Setter
@NoArgsConstructor
public class PaystackSavingsProductAttributes extends AbstractPersistableCustom<Long> {

    @Column(name = "savings_product_id", nullable = false, unique = true)
    private Long savingsProductId;

    @Column(name = "emt_applicable_for_deposit")
    private Boolean isEmtLevyApplicableForDeposit;

    @Column(name = "emt_applicable_for_withdraw")
    private Boolean isEmtLevyApplicableForWithdraw;

    @Column(name = "emt_override_global")
    private Boolean overrideGlobalEmtLevy;

    @Column(name = "emt_levy_amount", precision = 19, scale = 6)
    private BigDecimal emtLevyAmount;

    @Column(name = "emt_levy_threshold", precision = 19, scale = 6)
    private BigDecimal emtLevyThreshold;

    public static PaystackSavingsProductAttributes of(Long savingsProductId) {
        PaystackSavingsProductAttributes c = new PaystackSavingsProductAttributes();
        c.setSavingsProductId(savingsProductId);
        return c;
    }
}
