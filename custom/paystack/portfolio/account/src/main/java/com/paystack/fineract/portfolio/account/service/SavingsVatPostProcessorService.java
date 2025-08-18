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

package com.paystack.fineract.portfolio.account.service;

import static org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction.updateTaxDetails;

import com.paystack.fineract.portfolio.account.data.VatApplicationResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.savings.SavingsAccountTransactionType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.apache.fineract.portfolio.tax.domain.TaxGroup;
import org.apache.fineract.portfolio.tax.domain.TaxGroupMappings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsVatPostProcessorService {

    /**
     * Process VAT for a withdrawal fee transaction that was just created This method is called AFTER the fee
     * transaction has been created
     */
    @Transactional
    public VatApplicationResult processVatForFeeTransaction(BigDecimal amount, LocalDate transactionDate, SavingsAccountCharge charge,
            SavingsAccount account, boolean isBackdatedTransaction) {

        // Check if VAT should be applied
        if (!shouldApplyVat(charge, account)) {
            return new VatApplicationResult();
        }

        // Calculate VAT amount
        BigDecimal vatAmount = calculateVatAmount(amount, charge);

        if (vatAmount.compareTo(BigDecimal.ZERO) > 0) {
            // Create VAT transaction
            SavingsAccountTransaction vatTxn = createVatTransaction(account, vatAmount, transactionDate,
                    charge.getCharge().getTaxGroup().getTaxGroupMappings().stream().collect(
                            Collectors.toMap(TaxGroupMappings::getTaxComponent, mapping -> mapping.getTaxComponent().getPercentage()))

            );

            return VatApplicationResult
                    .success(vatTxn, vatAmount, charge.getCharge().getTaxGroup().getTaxGroupMappings().stream()
                            .map(t -> t.getTaxComponent().getPercentage()).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .setBackdatedTransaction(isBackdatedTransaction);

        }

        return new VatApplicationResult();
    }

    private boolean shouldApplyVat(SavingsAccountCharge charge, SavingsAccount account) {
        Charge chargeDefinition = charge.getCharge();
        return chargeDefinition != null && chargeDefinition.getTaxGroup() != null;

    }

    private BigDecimal calculateVatAmount(BigDecimal feeAmount, SavingsAccountCharge charge) {
        Charge chargeDefinition = charge.getCharge();

        // First try to get VAT rate from charge's tax group
        if (chargeDefinition != null && chargeDefinition.getTaxGroup() != null) {
            return calculateVatFromTaxGroup(feeAmount, chargeDefinition.getTaxGroup());
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal calculateVatFromTaxGroup(BigDecimal feeAmount, TaxGroup taxGroup) {
        BigDecimal totalVat = BigDecimal.ZERO;

        for (TaxGroupMappings mapping : taxGroup.getTaxGroupMappings()) {
            TaxComponent taxComponent = mapping.getTaxComponent();
            if (taxComponent != null && taxComponent.getPercentage() != null) {
                BigDecimal vatAmount = feeAmount.multiply(taxComponent.getPercentage()).divide(BigDecimal.valueOf(100), 2,
                        RoundingMode.HALF_UP);
                totalVat = totalVat.add(vatAmount);
            }
        }

        return totalVat;
    }

    private SavingsAccountTransaction createVatTransaction(SavingsAccount account, BigDecimal vatAmount, LocalDate transactionDate,
            Map<TaxComponent, BigDecimal> taxDetails) {

        final boolean isReversed = false;
        final boolean isManualTransaction = false;
        final Boolean lienTransaction = false;
        final String refNo = null;
        Money vatMoney = Money.of(account.getCurrency(), vatAmount);

        // Use reflection or a factory method to create the transaction
        // without modifying SavingsAccountTransaction class
        SavingsAccountTransaction vatTxn = new SavingsAccountTransaction(account, account.office(),
                SavingsAccountTransactionType.VAT_ON_FEES.getValue(), transactionDate, vatMoney, isReversed, isManualTransaction,
                lienTransaction, refNo);
        updateTaxDetails(taxDetails, vatTxn);

        return vatTxn;

    }
}
