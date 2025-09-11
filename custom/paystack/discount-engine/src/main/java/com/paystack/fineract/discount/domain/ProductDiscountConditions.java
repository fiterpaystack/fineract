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

package com.paystack.fineract.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product Discount Conditions
 * Defines the conditions for applying discounts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDiscountConditions {
    
    private AccountBalanceCondition accountBalance;
    private TransactionCondition transaction;
    private TimeBasedCondition timeBased;
    private ClientCondition client;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceCondition {
        private boolean enabled;
        private BigDecimal minimumBalance;
        private BigDecimal maximumBalance;
        private String balanceType; // CURRENT, AVAILABLE, TOTAL
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionCondition {
        private boolean enabled;
        private BigDecimal minimumAmount;
        private BigDecimal maximumAmount;
        private List<String> transactionTypes;
        private String frequency; // FIRST_TIME, REGULAR, VIP
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeBasedCondition {
        private boolean enabled;
        private String validFrom;
        private String validTo;
        private List<Integer> daysOfWeek;
        private TimeRange timeOfDay;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientCondition {
        private boolean enabled;
        private String clientType; // NEW, EXISTING, VIP
        private Integer minimumAccountAge;
        private Integer maximumAccountAge;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeRange {
        private String start;
        private String end;
    }
}
