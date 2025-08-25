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

package com.paystack.fineract.tier.service.data;

import java.math.BigDecimal;

@lombok.Data
@lombok.Builder
public class SavingsAccountTransactionLimitsSettingData {

    private final Long id;
    private final String name;
    private final BigDecimal maxSingleDepositAmount;
    private final BigDecimal balanceCumulative;
    private final Boolean isActive;
    private final String description;

    public TransactionLimitData getTransactionLimits() {
        return TransactionLimitData.builder().maxSingleDepositAmount(maxSingleDepositAmount).balanceCumulative(balanceCumulative).build();
    }

    public static SavingsAccountTransactionLimitsSettingData lookup(final Long id, final String name) {
        final BigDecimal maxSingleDepositAmount = null;
        final BigDecimal balanceCumulative = null;
        final Boolean isActive = null;
        final String description = null;
        return new SavingsAccountTransactionLimitsSettingData(id, name, maxSingleDepositAmount, balanceCumulative, isActive, description);
    }
}
