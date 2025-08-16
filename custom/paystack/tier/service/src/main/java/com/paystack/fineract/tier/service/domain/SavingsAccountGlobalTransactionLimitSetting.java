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

package com.paystack.fineract.tier.service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableCustom;

@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "m_savings_global_transaction_limits_setting")
public class SavingsAccountGlobalTransactionLimitSetting extends AbstractAuditableCustom {

    @Column(name = "name")
    private String name;

    @Embedded
    private TransactionLimits transactionLimits;

    @Column(name = "max_client_specific_single_withdrawal_amount")
    private BigDecimal maxClientSpecificSingleWithdrawalAmount;

    @Column(name = "max_client_specific_daily_withdrawal_amount")
    private BigDecimal maxClientSpecificDailyWithdrawalAmount;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "is_global_limit")
    private Boolean isGlobalLimit;

    @Column(name = "is_merchant_limit")
    private Boolean isMerchantLimit;

    @Column(name = "description")
    private String description;

    public static SavingsAccountGlobalTransactionLimitSetting fromJson(JsonCommand command) {

        final String name = command.stringValueOfParameterNamed("name");
        final BigDecimal maxSingleWithdrawalAmount = command.bigDecimalValueOfParameterNamed("maxSingleWithdrawalAmount");
        final BigDecimal maxSingleDepositAmount = command.bigDecimalValueOfParameterNamed("maxSingleDepositAmount");
        final BigDecimal maxDailyWithdrawalAmount = command.bigDecimalValueOfParameterNamed("maxDailyWithdrawalAmount");
        final BigDecimal maxOnHoldAmount = command.bigDecimalValueOfParameterNamed("maxOnHoldAmount");
        final BigDecimal maxClientSpecificSingleWithdrawalAmount = command
                .bigDecimalValueOfParameterNamed("maxClientSpecificSingleWithdrawalAmount");
        final BigDecimal maxClientSpecificDailyWithdrawalAmount = command
                .bigDecimalValueOfParameterNamed("maxClientSpecificDailyWithdrawalAmount");
        final Boolean isActive = command.booleanPrimitiveValueOfParameterNamed("isActive");
        final Boolean isGlobalLimit = command.booleanPrimitiveValueOfParameterNamed("isGlobalLimit");
        final Boolean isMerchantLimit = command.booleanPrimitiveValueOfParameterNamed("isMerchantLimit");
        final String description = command.stringValueOfParameterNamed("description");

        final TransactionLimits limits = TransactionLimits.builder().maxDailyWithdrawalAmount(maxDailyWithdrawalAmount)
                .maxOnHoldAmount(maxOnHoldAmount).maxSingleDepositAmount(maxSingleDepositAmount)
                .maxSingleWithdrawalAmount(maxSingleWithdrawalAmount).build();

        return new SavingsAccountGlobalTransactionLimitSetting(name, limits, maxClientSpecificSingleWithdrawalAmount,
                maxClientSpecificDailyWithdrawalAmount, isActive, isGlobalLimit, isMerchantLimit, description);
    }
}
