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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.fund.domain.Fund;

@Entity
@Table(name = "m_charge_split", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "charge_id", "fund_id" }, name = "unique_charge_fund_split") })
@Getter
@Setter
public class ChargeSplit extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fund_id", nullable = false)
    private Fund fund;

    @Column(name = "split_type", nullable = false, length = 20)
    private String splitType;

    @Column(name = "split_value", scale = 6, precision = 19, nullable = false)
    private BigDecimal splitValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gl_account_id", nullable = false)
    private GLAccount glAccount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected ChargeSplit() {
        //
    }

    public static ChargeSplit createNew(final Charge charge, final Fund fund, final String splitType, final BigDecimal splitValue,
            final GLAccount glAccount) {
        return new ChargeSplit(charge, fund, splitType, splitValue, glAccount);
    }

    private ChargeSplit(final Charge charge, final Fund fund, final String splitType, final BigDecimal splitValue,
            final GLAccount glAccount) {
        this.charge = charge;
        this.fund = fund;
        this.splitType = splitType;
        this.splitValue = splitValue;
        this.glAccount = glAccount;
        this.active = true;
    }

    public static ChargeSplit fromJson(final JsonCommand command, final Charge charge, final Fund fund, final GLAccount glAccount) {
        final String splitType = command.stringValueOfParameterNamed("splitType");
        final BigDecimal splitValue = command.bigDecimalValueOfParameterNamed("splitValue");
        return new ChargeSplit(charge, fund, splitType, splitValue, glAccount);
    }

    public Map<String, Object> update(final JsonCommand command) {
        final Map<String, Object> actualChanges = new LinkedHashMap<>(7);

        final String splitTypeParamName = "splitType";
        if (command.isChangeInStringParameterNamed(splitTypeParamName, this.splitType)) {
            final String newValue = command.stringValueOfParameterNamed(splitTypeParamName);
            actualChanges.put(splitTypeParamName, newValue);
            this.splitType = newValue;
        }

        final String splitValueParamName = "splitValue";
        if (command.isChangeInBigDecimalParameterNamed(splitValueParamName, this.splitValue)) {
            final BigDecimal newValue = command.bigDecimalValueOfParameterNamed(splitValueParamName);
            actualChanges.put(splitValueParamName, newValue);
            this.splitValue = newValue;
        }

        final String fundIdParamName = "fundId";
        if (command.isChangeInLongParameterNamed(fundIdParamName, this.fund.getId())) {
            final Long newValue = command.longValueOfParameterNamed(fundIdParamName);
            actualChanges.put(fundIdParamName, newValue);
        }

        final String glAccountIdParamName = "glAccountId";
        if (command.isChangeInLongParameterNamed(glAccountIdParamName, this.glAccount.getId())) {
            final Long newValue = command.longValueOfParameterNamed(glAccountIdParamName);
            actualChanges.put(glAccountIdParamName, newValue);
        }

        final String activeParamName = "active";
        if (command.isChangeInBooleanParameterNamed(activeParamName, this.active)) {
            final Boolean newValue = command.booleanPrimitiveValueOfParameterNamed(activeParamName);
            actualChanges.put(activeParamName, newValue);
            this.active = newValue;
        }

        return actualChanges;
    }

    public boolean isPercentageSplit() {
        return "PERCENTAGE".equals(this.splitType);
    }

    public boolean isFlatAmountSplit() {
        return "FLAT_AMOUNT".equals(this.splitType);
    }

    public void setFund(Fund fund) {
        this.fund = fund;
    }

    public void setGlAccount(GLAccount glAccount) {
        this.glAccount = glAccount;
    }

    public BigDecimal calculateSplitAmount(final BigDecimal totalFeeAmount) {
        if (isPercentageSplit()) {
            return totalFeeAmount.multiply(this.splitValue).divide(BigDecimal.valueOf(100), 6, BigDecimal.ROUND_HALF_UP);
        } else {
            return this.splitValue;
        }
    }
}
