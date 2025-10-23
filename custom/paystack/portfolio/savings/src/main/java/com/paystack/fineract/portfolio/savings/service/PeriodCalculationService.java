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

package com.paystack.fineract.portfolio.savings.service;

import com.paystack.fineract.portfolio.savings.domain.PeriodBoundaries;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Service for calculating time period boundaries Handles date calculations for different time periods (daily, weekly,
 * monthly, yearly)
 */
@Service
public class PeriodCalculationService {

    /**
     * Calculate period boundaries for a given date and time period
     */
    public PeriodBoundaries calculatePeriod(LocalDate date, TimePeriod timePeriod) {
        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (timePeriod == null) {
            throw new IllegalArgumentException("Time period cannot be null");
        }

        return switch (timePeriod) {
            case DAILY -> calculateDailyPeriod(date);
            case WEEKLY -> calculateWeeklyPeriod(date);
            case MONTHLY -> calculateMonthlyPeriod(date);
            case YEARLY -> calculateYearlyPeriod(date);
        };
    }

    /**
     * Calculate daily period (same day)
     */
    private PeriodBoundaries calculateDailyPeriod(LocalDate date) {
        return new PeriodBoundaries(date, date);
    }

    /**
     * Calculate weekly period (Monday to Sunday)
     */
    private PeriodBoundaries calculateWeeklyPeriod(LocalDate date) {
        // Get the Monday of the week containing the given date
        LocalDate startOfWeek = date.minusDays(date.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        return new PeriodBoundaries(startOfWeek, endOfWeek);
    }

    /**
     * Calculate monthly period (first day to last day of month)
     */
    private PeriodBoundaries calculateMonthlyPeriod(LocalDate date) {
        LocalDate startOfMonth = date.withDayOfMonth(1);
        LocalDate endOfMonth = date.withDayOfMonth(date.lengthOfMonth());
        return new PeriodBoundaries(startOfMonth, endOfMonth);
    }

    /**
     * Calculate yearly period (January 1st to December 31st)
     */
    private PeriodBoundaries calculateYearlyPeriod(LocalDate date) {
        LocalDate startOfYear = date.withDayOfYear(1);
        LocalDate endOfYear = date.withDayOfYear(date.lengthOfYear());
        return new PeriodBoundaries(startOfYear, endOfYear);
    }

    /**
     * Check if two dates are in the same period
     */
    public boolean areInSamePeriod(LocalDate date1, LocalDate date2, TimePeriod timePeriod) {
        if (date1 == null || date2 == null || timePeriod == null) {
            return false;
        }

        PeriodBoundaries period1 = calculatePeriod(date1, timePeriod);
        return period1.contains(date2);
    }

    /**
     * Get the number of days in a period
     */
    public long getDaysInPeriod(LocalDate date, TimePeriod timePeriod) {
        PeriodBoundaries period = calculatePeriod(date, timePeriod);
        return period.getDaysInPeriod();
    }

    /**
     * Get the start date of the period containing the given date
     */
    public LocalDate getPeriodStart(LocalDate date, TimePeriod timePeriod) {
        PeriodBoundaries period = calculatePeriod(date, timePeriod);
        return period.getStartDate();
    }

    /**
     * Get the end date of the period containing the given date
     */
    public LocalDate getPeriodEnd(LocalDate date, TimePeriod timePeriod) {
        PeriodBoundaries period = calculatePeriod(date, timePeriod);
        return period.getEndDate();
    }
}
