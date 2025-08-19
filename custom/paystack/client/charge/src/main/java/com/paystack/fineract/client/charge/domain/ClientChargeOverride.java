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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.client.domain.Client;

/**
 * Client-specific Charge override for calculation-impacting fields only. When an override field is null, fallback to
 * the underlying Charge definition.
 */
@Entity
@Table(name = "m_client_charge_override", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "client_id", "charge_id" }, name = "uk_client_charge_override_client_charge") })
@Getter
@Setter
public class ClientChargeOverride extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", referencedColumnName = "id", nullable = false)
    private Client client;

    @ManyToOne(optional = false)
    @JoinColumn(name = "charge_id", referencedColumnName = "id", nullable = false)
    private Charge charge;

    // Overrides based on org.apache.fineract.portfolio.charge.domain.Charge
    @Column(name = "amount", precision = 19, scale = 6)
    private BigDecimal amount; // nullable -> fallback to Charge.amount

    @Column(name = "min_cap", precision = 19, scale = 6)
    private BigDecimal minCap; // nullable -> fallback to Charge.minCap

    @Column(name = "max_cap", precision = 19, scale = 6)
    private BigDecimal maxCap; // nullable -> fallback to Charge.maxCap

    protected ClientChargeOverride() {
        // for JPA
    }

    public ClientChargeOverride(Client client, Charge charge, BigDecimal amount, BigDecimal minCap, BigDecimal maxCap) {
        this.client = client;
        this.charge = charge;
        this.amount = amount;
        this.minCap = minCap;
        this.maxCap = maxCap;
    }

}
