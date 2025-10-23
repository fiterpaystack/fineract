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

package com.paystack.fineract.portfolio.savings.domain;

/**
 * Time Period Enumeration Defines the time periods for withdrawal frequency controls
 */
public enum TimePeriod {

    DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly");

    private final String displayName;

    TimePeriod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the TimePeriod from string value (case-insensitive)
     */
    public static TimePeriod fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return TimePeriod.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid time period: " + value + ". Valid values are: DAILY, WEEKLY, MONTHLY, YEARLY", e);
        }
    }

    /**
     * Check if this time period is more restrictive than another (shorter periods are more restrictive)
     */
    public boolean isMoreRestrictiveThan(TimePeriod other) {
        if (other == null) {
            return true;
        }

        return switch (this) {
            case DAILY -> other != DAILY;
            case WEEKLY -> other == MONTHLY || other == YEARLY;
            case MONTHLY -> other == YEARLY;
            case YEARLY -> false;
        };
    }

    /**
     * Get the ordinal value for sorting purposes
     */
    public int getOrder() {
        return ordinal();
    }
}
