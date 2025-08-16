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

package com.paystack.fineract.tier.service.api;

import java.util.Set;

public final class SavingsAccountTransactionLimitApiConstant {

    private SavingsAccountTransactionLimitApiConstant() {
        // empty constructor for constants
    }

    public static final String RESOURCE_NAME = "savingsaccounttransactionlimit";
    public static final String NAME_PARAM_NAME = "name";
    public static final String MAX_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME = "maxSingleWithdrawalAmount";
    public static final String MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME = "maxSingleDepositAmount";
    public static final String MAX_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME = "maxDailyWithdrawalAmount";
    public static final String MAX_CLIENT_SPECIFIC_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME = "maxClientSpecificSingleWithdrawalAmount";
    public static final String MAX_CLIENT_SPECIFIC_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME = "maxClientSpecificDailyWithdrawalAmount";
    public static final String BALANCE_CUMULATIVE_PARAM_NAME = "balanceCumulative";
    public static final String IS_ACTIVE_PARAM_NAME = "isActive";
    public static final String IS_GLOBAL_LIMIT_PARAM_NAME = "isGlobalLimit";
    public static final String IS_MERCHANT_LIMIT_PARAM_NAME = "isMerchantLimit";
    public static final String DESCRIPTION_PARAM_NAME = "description";
    public static final String LIMIT_ID_PARAM_NAME = "globalLimitId";
    public static final String CLIENT_CLASSIFICATION_PARAM_NAME = "clientClassificationId";
    public static final String CLASSIFICATION_CODE_NAME = "clientClassification";

    public static final Set<String> CREATE_REQUEST_DATA_PARAMETERS = Set.of(NAME_PARAM_NAME, MAX_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME,
            MAX_SINGLE_DEPOSIT_AMOUNT_PARAM_NAME, MAX_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME, BALANCE_CUMULATIVE_PARAM_NAME,
            IS_ACTIVE_PARAM_NAME, IS_GLOBAL_LIMIT_PARAM_NAME, DESCRIPTION_PARAM_NAME,
            MAX_CLIENT_SPECIFIC_DAILY_WITHDRAWAL_AMOUNT_PARAM_NAME, MAX_CLIENT_SPECIFIC_SINGLE_WITHDRAWAL_AMOUNT_PARAM_NAME, "locale",
            "dateFormat", "isMerchantLimit");

}
