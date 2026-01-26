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
package com.paystack.fineract.infrastructure.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Units for retry interval configuration. Supports SECONDS, MINUTES, HOURS, and DAYS.
 */
public enum RetryIntervalUnit {

    SECOND(ChronoUnit.SECONDS), MINUTE(ChronoUnit.MINUTES), HOUR(ChronoUnit.HOURS), DAY(ChronoUnit.DAYS);

    private final ChronoUnit chronoUnit;

    RetryIntervalUnit(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    /**
     * Convert value and unit to Duration.
     *
     * @param value
     *            the numeric value
     * @return Duration representing the interval
     */
    public Duration toDuration(long value) {
        return Duration.of(value, chronoUnit);
    }

    /**
     * Get the ChronoUnit for database queries.
     *
     * @return ChronoUnit
     */
    public ChronoUnit getChronoUnit() {
        return chronoUnit;
    }

    /**
     * Get the SQL interval unit name for database queries.
     *
     * @return SQL interval unit (SECOND, MINUTE, HOUR, DAY)
     */
    public String getSqlIntervalUnit() {
        return name();
    }
}
