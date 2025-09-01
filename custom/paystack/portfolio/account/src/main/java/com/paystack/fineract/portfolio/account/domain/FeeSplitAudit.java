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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;

@Entity
@Table(name = "m_fee_split_audit")
@Getter
@Setter
public class FeeSplitAudit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "transaction_id", nullable = false, length = 50)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "total_fee_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal totalFeeAmount;

    @Column(name = "split_date", nullable = false)
    private LocalDate splitDate;

    @OneToMany(mappedBy = "audit", fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<FeeSplitDetail> splitDetails = new ArrayList<>();

    protected FeeSplitAudit() {
        //
    }

    public static FeeSplitAudit createNew(final String transactionId, final Charge charge, final BigDecimal totalFeeAmount,
            final LocalDate splitDate) {
        return new FeeSplitAudit(transactionId, charge, totalFeeAmount, splitDate);
    }

    private FeeSplitAudit(final String transactionId, final Charge charge, final BigDecimal totalFeeAmount, final LocalDate splitDate) {
        this.transactionId = transactionId;
        this.charge = charge;
        this.totalFeeAmount = totalFeeAmount;
        this.splitDate = splitDate;
    }

    public void addSplitDetail(final FeeSplitDetail splitDetail) {
        this.splitDetails.add(splitDetail);
        splitDetail.setAudit(this);
    }

    public BigDecimal getTotalSplitAmount() {
        return this.splitDetails.stream().map(FeeSplitDetail::getSplitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
