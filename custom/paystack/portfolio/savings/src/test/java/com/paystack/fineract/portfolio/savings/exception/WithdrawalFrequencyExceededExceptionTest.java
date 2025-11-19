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

package com.paystack.fineract.portfolio.savings.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for WithdrawalFrequencyExceededException
 */
class WithdrawalFrequencyExceededExceptionTest {

    @Test
    void testConstructor_WithMessage() {
        String message = "Withdrawal limit exceeded";
        WithdrawalFrequencyExceededException exception = new WithdrawalFrequencyExceededException(message);

        assertEquals("error.msg.withdrawal.frequency.exceeded", exception.getGlobalisationMessageCode());
        assertEquals(message, exception.getDefaultUserMessage());
    }

    @Test
    void testConstructor_WithMessageAndDetails() {
        String message = "Withdrawal limit exceeded";
        String details = "Additional details";
        WithdrawalFrequencyExceededException exception = new WithdrawalFrequencyExceededException(message, details);

        assertEquals("error.msg.withdrawal.frequency.exceeded", exception.getGlobalisationMessageCode());
        assertEquals(message, exception.getDefaultUserMessage());
    }

    @Test
    void testConstructor_WithMessageAndCause() {
        String message = "Withdrawal limit exceeded";
        RuntimeException cause = new RuntimeException("Root cause");
        WithdrawalFrequencyExceededException exception = new WithdrawalFrequencyExceededException(message, cause);

        assertEquals("error.msg.withdrawal.frequency.exceeded", exception.getGlobalisationMessageCode());
        assertEquals(message, exception.getDefaultUserMessage());
        assertEquals(cause, exception.getCause());
    }

    @Test
    void testForPeriod_SinglePeriod() {
        String timePeriod = "Monthly";
        int currentCount = 2;
        int maxWithdrawals = 2;

        WithdrawalFrequencyExceededException exception = WithdrawalFrequencyExceededException.forPeriod(timePeriod, currentCount,
                maxWithdrawals);

        String expectedMessage = "Withdrawal limit exceeded for Monthly period: 2/2 withdrawals used";
        assertEquals(expectedMessage, exception.getDefaultUserMessage());
        assertEquals("error.msg.withdrawal.frequency.exceeded", exception.getGlobalisationMessageCode());
    }

    @Test
    void testForPeriod_DifferentCounts() {
        String timePeriod = "Daily";
        int currentCount = 5;
        int maxWithdrawals = 3;

        WithdrawalFrequencyExceededException exception = WithdrawalFrequencyExceededException.forPeriod(timePeriod, currentCount,
                maxWithdrawals);

        String expectedMessage = "Withdrawal limit exceeded for Daily period: 5/3 withdrawals used";
        assertEquals(expectedMessage, exception.getDefaultUserMessage());
    }

    @Test
    void testForMultiplePeriods() {
        String violatedPeriods = "Daily, Weekly";

        WithdrawalFrequencyExceededException exception = WithdrawalFrequencyExceededException.forMultiplePeriods(violatedPeriods);

        String expectedMessage = "Withdrawal limit exceeded for the following periods: Daily, Weekly";
        assertEquals(expectedMessage, exception.getDefaultUserMessage());
        assertEquals("error.msg.withdrawal.frequency.exceeded", exception.getGlobalisationMessageCode());
    }

    @Test
    void testInheritance() {
        assertTrue(org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException.class
                .isAssignableFrom(WithdrawalFrequencyExceededException.class));
    }

    @Test
    void testErrorCode() {
        WithdrawalFrequencyExceededException exception1 = new WithdrawalFrequencyExceededException("Message 1");
        WithdrawalFrequencyExceededException exception2 = WithdrawalFrequencyExceededException.forPeriod("Daily", 1, 1);
        WithdrawalFrequencyExceededException exception3 = WithdrawalFrequencyExceededException.forMultiplePeriods("Daily");

        // All should have the same error code
        assertEquals("error.msg.withdrawal.frequency.exceeded", exception1.getGlobalisationMessageCode());
        assertEquals("error.msg.withdrawal.frequency.exceeded", exception2.getGlobalisationMessageCode());
        assertEquals("error.msg.withdrawal.frequency.exceeded", exception3.getGlobalisationMessageCode());
    }
}
