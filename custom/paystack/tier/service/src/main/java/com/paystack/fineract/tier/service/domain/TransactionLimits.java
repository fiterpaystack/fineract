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
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.Data;

@Embeddable
@Data
@lombok.Builder
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class TransactionLimits {

    @Column(name = "max_single_withdrawal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxSingleWithdrawalAmount;

    @Column(name = "max_single_deposit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxSingleDepositAmount;

    @Column(name = "max_daily_withdrawal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal maxDailyWithdrawalAmount;

    @Column(name = "balance_cumulative", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceCumulative;

}
