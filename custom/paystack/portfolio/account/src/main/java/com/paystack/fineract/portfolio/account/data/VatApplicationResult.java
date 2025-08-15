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
import java.time.LocalDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;

/**
 * Result object for VAT application on charges
 */
@Data
@NoArgsConstructor
public class VatApplicationResult {

    // Core result fields
    private boolean vatApplied = false;
    private BigDecimal vatAmount = BigDecimal.ZERO;
    private BigDecimal vatPercentage = BigDecimal.ZERO;

    // Transaction references
    private SavingsAccountTransaction vatTransaction;
    private SavingsAccountTransaction parentFeeTransaction;
    private Long vatTransactionId;
    private Long parentTransactionId;

    // Processing metadata
    private LocalDate transactionDate;
    private String vatDescription;
    private String vatReferenceNumber;
    private boolean backdatedTransaction = false;

    // Error handling
    private boolean hasErrors = false;
    private String errorMessage;

    // Audit fields
    private String processedBy;
    private LocalDate processedDate;

    /**
     * Create a successful VAT application result
     */
    public static VatApplicationResult success(SavingsAccountTransaction vatTransaction, BigDecimal vatAmount, BigDecimal vatPercentage) {

        VatApplicationResult result = new VatApplicationResult();
        result.vatApplied = true;
        result.vatTransaction = vatTransaction;
        result.vatAmount = vatAmount;
        result.vatPercentage = vatPercentage;
        result.vatTransactionId = vatTransaction.getId();
        result.transactionDate = vatTransaction.getDateOf();
        result.processedDate = LocalDate.now();

        return result;
    }

    // Convenience methods
    public boolean isSuccessful() {
        return vatApplied && !hasErrors;
    }

    public boolean requiresBalanceUpdate() {
        return vatApplied || backdatedTransaction;
    }

    public BigDecimal getTotalChargeWithVat() {
        if (parentFeeTransaction != null) {
            return parentFeeTransaction.getAmount().add(vatAmount);
        }
        return vatAmount;
    }

    public VatApplicationResult setBackdatedTransaction(boolean backdated) {
        this.backdatedTransaction = backdated;
        return this;
    }

}
