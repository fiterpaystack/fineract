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

import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;

/**
 * Exception thrown when withdrawal frequency limit is exceeded
 */
public class WithdrawalFrequencyExceededException extends PlatformApiDataValidationException {

    private static final String ERROR_CODE = "error.msg.withdrawal.frequency.exceeded";
    private static final String DEFAULT_MESSAGE = "Withdrawal limit exceeded for the current period";

    public WithdrawalFrequencyExceededException(String message) {
        super(ERROR_CODE, message, List.of(ApiParameterError.parameterError(ERROR_CODE, message, "withdrawalFrequency", null)));
    }

    public WithdrawalFrequencyExceededException(String message, String details) {
        super(ERROR_CODE, message, List.of(ApiParameterError.parameterError(ERROR_CODE, message, "withdrawalFrequency", details)));
    }

    public WithdrawalFrequencyExceededException(String message, Throwable cause) {
        super(ERROR_CODE, message, List.of(ApiParameterError.parameterError(ERROR_CODE, message, "withdrawalFrequency", null)), cause);
    }

    public static WithdrawalFrequencyExceededException forPeriod(String timePeriod, int currentCount, int maxWithdrawals) {
        String message = String.format("Withdrawal limit exceeded for %s period: %d/%d withdrawals used", timePeriod, currentCount,
                maxWithdrawals);
        return new WithdrawalFrequencyExceededException(message);
    }

    public static WithdrawalFrequencyExceededException forMultiplePeriods(String violatedPeriods) {
        String message = String.format("Withdrawal limit exceeded for the following periods: %s", violatedPeriods);
        return new WithdrawalFrequencyExceededException(message);
    }
}
