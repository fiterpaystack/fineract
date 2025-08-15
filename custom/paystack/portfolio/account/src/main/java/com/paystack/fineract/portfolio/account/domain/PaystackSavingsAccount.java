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

package com.paystack.fineract.portfolio.account.domain;

import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.CREATED_BY_DB_FIELD;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.CREATED_DATE_DB_FIELD;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.LAST_MODIFIED_BY_DB_FIELD;
import static org.apache.fineract.infrastructure.core.domain.AuditableFieldsConstants.LAST_MODIFIED_DATE_DB_FIELD;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ps_m_savings_account")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class PaystackSavingsAccount {

    @Id
    @Column(name = "savings_account_id")
    private Long id;

    @Column(name = "total_vat_amount_derived")
    private BigDecimal totalVatAmountDerived;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "savings_account_id")
    private SavingsAccount savingsAccount;

    @Column(name = CREATED_BY_DB_FIELD, updatable = false, nullable = false)
    private Long createdBy;

    @Column(name = CREATED_DATE_DB_FIELD, updatable = false, nullable = false)
    private OffsetDateTime createdDate;

    @Column(name = LAST_MODIFIED_BY_DB_FIELD, nullable = false)
    private Long lastModifiedBy;

    @Column(name = LAST_MODIFIED_DATE_DB_FIELD, nullable = false)
    private OffsetDateTime lastModifiedDate;

}
