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

package com.paystack.fineract.portfolio.savings.data;

import com.google.gson.JsonObject;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Withdrawal Frequency Settings Used for API requests and responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalFrequencySettingData {

    private Integer maxWithdrawals;
    private TimePeriod timePeriod;
    private Boolean isActive = true;

    /**
     * Create from JSON object
     */
    public static WithdrawalFrequencySettingData fromJson(JsonObject json) {
        if (json == null) {
            return null;
        }

        WithdrawalFrequencySettingData data = new WithdrawalFrequencySettingData();

        if (json.has("maxWithdrawals") && !json.get("maxWithdrawals").isJsonNull()) {
            data.setMaxWithdrawals(json.get("maxWithdrawals").getAsInt());
        }

        if (json.has("timePeriod") && !json.get("timePeriod").isJsonNull()) {
            try {
                data.setTimePeriod(TimePeriod.fromString(json.get("timePeriod").getAsString()));
            } catch (IllegalArgumentException e) {
                // Invalid time period, return null
                return null;
            }
        }

        if (json.has("isActive") && !json.get("isActive").isJsonNull()) {
            data.setIsActive(json.get("isActive").getAsBoolean());
        }

        // Return null if the data is not valid
        if (!data.isValid()) {
            return null;
        }

        return data;
    }

    /**
     * Validate the data
     */
    public void validate() {
        if (maxWithdrawals != null && maxWithdrawals <= 0) {
            throw new IllegalArgumentException("Max withdrawals must be a positive integer");
        }
        if (timePeriod == null && maxWithdrawals != null) {
            throw new IllegalArgumentException("Time period is required when max withdrawals is specified");
        }
    }

    /**
     * Check if this setting is valid (has both maxWithdrawals and timePeriod)
     */
    public boolean isValid() {
        return maxWithdrawals != null && timePeriod != null && maxWithdrawals > 0 && isActive != null;
    }

    /**
     * Check if this setting should be removed (null maxWithdrawals)
     */
    public boolean shouldBeRemoved() {
        return maxWithdrawals == null;
    }
}
