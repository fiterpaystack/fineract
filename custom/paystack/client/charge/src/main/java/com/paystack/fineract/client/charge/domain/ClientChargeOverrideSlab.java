/*
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
package com.paystack.fineract.client.charge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

/**
 * Client-specific tier definition linked to {@link ClientChargeOverride}. When present, these slabs override the
 * product-level charge slabs for the associated client.
 */
@Entity
@Table(name = "m_client_charge_override_slab")
@Getter
@Setter
@NoArgsConstructor
public class ClientChargeOverrideSlab extends AbstractAuditableCustom {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_charge_override_id", referencedColumnName = "id", nullable = false)
    private ClientChargeOverride override;

    @Column(name = "from_amount", precision = 19, scale = 6, nullable = false)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", precision = 19, scale = 6)
    private BigDecimal toAmount;

    @Column(name = "value", precision = 19, scale = 6, nullable = false)
    private BigDecimal value;

    public ClientChargeOverrideSlab(ClientChargeOverride override, BigDecimal fromAmount, BigDecimal toAmount, BigDecimal value) {
        this.override = override;
        this.fromAmount = fromAmount;
        this.toAmount = toAmount;
        this.value = value;
    }
}
