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

import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Withdrawal Frequency Status Represents the current status of withdrawal frequency limits for
 * an account
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalFrequencyStatus {

    private boolean withdrawalAllowed;
    private List<PeriodStatus> periodStatuses;

    /**
     * Constructor with just period statuses - calculates withdrawalAllowed automatically
     */
    public WithdrawalFrequencyStatus(List<PeriodStatus> periodStatuses) {
        this.periodStatuses = periodStatuses;
        this.withdrawalAllowed = periodStatuses == null || periodStatuses.isEmpty()
                || periodStatuses.stream().allMatch(PeriodStatus::isAllowed);
    }

    /**
     * Check if withdrawal is allowed for all periods
     */
    public boolean isWithdrawalAllowed() {
        return withdrawalAllowed;
    }

    /**
     * Get the most restrictive period (the one with the least remaining withdrawals)
     */
    public PeriodStatus getMostRestrictivePeriod() {
        if (periodStatuses == null || periodStatuses.isEmpty()) {
            return null;
        }
        return periodStatuses.stream().filter(ps -> !ps.isAllowed())
                .min((ps1, ps2) -> Integer.compare(ps1.getRemaining(), ps2.getRemaining())).orElse(null);
    }

    /**
     * Get status for a specific time period
     */
    public PeriodStatus getStatusForPeriod(TimePeriod timePeriod) {
        if (periodStatuses == null) {
            return null;
        }
        return periodStatuses.stream().filter(ps -> ps.getTimePeriod() == timePeriod).findFirst().orElse(null);
    }

    /**
     * Period Status for a specific time period
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodStatus {

        private TimePeriod timePeriod;
        private Integer maxWithdrawals;
        private Integer currentCount;
        private Integer remaining;
        private Boolean allowed;

        /**
         * Create a period status
         */
        public static PeriodStatus create(TimePeriod timePeriod, Integer maxWithdrawals, Integer currentCount) {
            int remaining = Math.max(0, maxWithdrawals - currentCount);
            boolean allowed = currentCount < maxWithdrawals;

            return new PeriodStatus(timePeriod, maxWithdrawals, currentCount, remaining, allowed);
        }

        /**
         * Get the percentage of limit used
         */
        public double getUsagePercentage() {
            if (maxWithdrawals == null || maxWithdrawals == 0) {
                return 0.0;
            }
            return (double) currentCount / maxWithdrawals * 100.0;
        }

        /**
         * Check if this period is at or near the limit (80% or more)
         */
        public boolean isNearLimit() {
            return getUsagePercentage() >= 80.0;
        }

        // Getter methods
        public TimePeriod getTimePeriod() {
            return timePeriod;
        }

        public Integer getMaxWithdrawals() {
            return maxWithdrawals;
        }

        public Integer getCurrentCount() {
            return currentCount;
        }

        public Integer getRemaining() {
            return remaining;
        }

        public Boolean isAllowed() {
            return allowed;
        }

        // Check if limit is exceeded
        public boolean isLimitExceeded() {
            return allowed != null && !allowed;
        }
    }
}
