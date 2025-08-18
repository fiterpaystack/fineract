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

package com.paystack.fineract.portfolio.account.data;

import java.math.BigDecimal;
import lombok.Data;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;

/**
 * Result of charge payment including VAT if applicable
 */
@Data
public class ChargePaymentResult {

    // Fee transaction details
    private final SavingsAccountTransaction feeTransaction;
    private final BigDecimal feeAmount;
    private final Long feeTransactionId;

    // VAT details (if applicable)
    private final VatApplicationResult vatResult;
    private final BigDecimal vatAmount;
    private final boolean vatApplied;

    // Total amounts
    private final BigDecimal totalAmount;
    private final BigDecimal netAmount;

    // Status
    private final boolean successful;
    private final String message;

    public ChargePaymentResult(SavingsAccountTransaction feeTransaction, VatApplicationResult vatResult) {

        this.feeTransaction = feeTransaction;
        this.feeTransactionId = feeTransaction.getId();
        this.feeAmount = feeTransaction.getAmount();

        this.vatResult = vatResult;
        this.vatApplied = vatResult != null && vatResult.isVatApplied();
        this.vatAmount = vatApplied ? vatResult.getVatAmount() : BigDecimal.ZERO;

        this.netAmount = feeAmount;
        this.totalAmount = feeAmount.add(vatAmount);

        this.successful = feeTransaction != null;
        this.message = buildMessage();
    }

    private String buildMessage() {
        if (!successful) {
            return "Charge payment failed";
        }
        if (vatApplied) {
            return String.format("Charge of %s paid with VAT of %s (Total: %s)", feeAmount, vatAmount, totalAmount);
        }
        return String.format("Charge of %s paid (no VAT applicable)", feeAmount);
    }

    public boolean hasVat() {
        return vatApplied && vatAmount.compareTo(BigDecimal.ZERO) > 0;
    }
}
