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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.journalentry.domain.JournalEntry;
import org.apache.fineract.portfolio.fund.domain.Fund;

@Entity
@Table(name = "m_fee_split_detail")
@Getter
@Setter
public class FeeSplitDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private FeeSplitAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "split_amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal splitAmount;

    @Column(name = "split_percentage", scale = 2, precision = 5)
    private BigDecimal splitPercentage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private GLAccount glAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry;

    protected FeeSplitDetail() {
        //
    }

    public static FeeSplitDetail createNew(final FeeSplitAudit audit, final Fund fund, final BigDecimal splitAmount,
            final BigDecimal splitPercentage, final GLAccount glAccount, final JournalEntry journalEntry) {
        return new FeeSplitDetail(audit, fund, splitAmount, splitPercentage, glAccount, journalEntry);
    }

    private FeeSplitDetail(final FeeSplitAudit audit, final Fund fund, final BigDecimal splitAmount, final BigDecimal splitPercentage,
            final GLAccount glAccount, final JournalEntry journalEntry) {
        this.audit = audit;
        this.fund = fund;
        this.splitAmount = splitAmount;
        this.splitPercentage = splitPercentage;
        this.glAccount = glAccount;
        this.journalEntry = journalEntry;
    }
}
