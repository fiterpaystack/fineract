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

import static org.apache.fineract.portfolio.savings.SavingsApiConstants.SAVINGS_ACCOUNT_RESOURCE_NAME;
import static org.apache.fineract.portfolio.savings.SavingsApiConstants.dueAsOfDateParamName;

import com.paystack.fineract.portfolio.account.data.ChargePaymentResult;
import com.paystack.fineract.portfolio.account.data.VatApplicationResult;
import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccount;
import com.paystack.fineract.portfolio.account.domain.PaystackSavingsAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.organisation.monetary.domain.Money;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionSummaryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wrapper service that handles charge payment with VAT This replaces direct calls to account.payCharge()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsAccountChargePaymentWrapperService {

    private final SavingsVatPostProcessorService vatService;
    private final SavingsAccountTransactionSummaryWrapper savingsAccountTransactionSummaryWrapper;
    private final PaystackSavingsAccountRepository paystackSavingsAccountRepository;

    /**
     * Pay a charge and automatically apply VAT if configured
     */
    @Transactional
    public ChargePaymentResult payChargeWithVat(SavingsAccount account, SavingsAccountCharge charge, Money amount,
            LocalDate transactionDate, String refNo, boolean isBackdatedTransaction) {

        // Create fee transaction first (added to account)
        SavingsAccountTransaction feeTransaction = account.payCharge(charge, amount, transactionDate, isBackdatedTransaction, refNo);

        // Compute VAT but DO NOT attach yet
        VatApplicationResult vatResult = vatService.processVatForFeeTransaction(amount.getAmount(), transactionDate, charge, account,
                isBackdatedTransaction);

        return new ChargePaymentResult(feeTransaction, vatResult); // caller will attach VAT after persisting fee
    }

    // Make VAT attachment method public so caller can invoke after fee persistence
    public void attachVatAfterFeePersist(SavingsAccount account, VatApplicationResult vatResult) {
        if (vatResult == null || !vatResult.isVatApplied()) {
            return;
        }
        if (vatResult.isBackdatedTransaction()) {
            account.getSavingsAccountTransactionsWithPivotConfig().add(vatResult.getVatTransaction());
            account.getSummary().updateSummaryWithPivotConfig(account.getCurrency(), savingsAccountTransactionSummaryWrapper,
                    vatResult.getVatTransaction(), account.getSavingsAccountTransactionsWithPivotConfig());
            Optional<PaystackSavingsAccount> paystackAccountOpt = paystackSavingsAccountRepository.findBySavingsAccount(account);
            if (paystackAccountOpt.isPresent()) {
                PaystackSavingsAccount paystackAccount = paystackAccountOpt.get();
                BigDecimal newVatTotal = paystackAccount.getTotalVatAmountDerived().add(vatResult.getVatAmount());
                paystackAccount.setTotalVatAmountDerived(newVatTotal);
                paystackSavingsAccountRepository.save(paystackAccount);
            } else {
                PaystackSavingsAccount extendedAccount = new PaystackSavingsAccount();
                extendedAccount.setTotalVatAmountDerived(vatResult.getVatAmount());
                extendedAccount.setSavingsAccount(account);
                extendedAccount.setId(account.getId());
                paystackSavingsAccountRepository.save(extendedAccount);
            }
        } else {
            account.addTransaction(vatResult.getVatTransaction());
        }
    }

    public ChargePaymentResult payChargeWithVat(final SavingsAccount account, final SavingsAccountCharge savingsAccountCharge,
            final BigDecimal amountPaid, final LocalDate transactionDate, final DateTimeFormatter formatter,
            final boolean backdatedTxnsAllowedTill, final String refNo) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(SAVINGS_ACCOUNT_RESOURCE_NAME);

        if (account.isClosed()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.is.closed");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        if (account.isNotActive()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.is.not.active");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        if (savingsAccountCharge.isNotActive()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("charge.is.not.active");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        if (DateUtils.isBefore(transactionDate, account.getActivationDate())) {
            baseDataValidator.reset().parameter(dueAsOfDateParamName).value(account.getActivationDate().format(formatter))
                    .failWithCodeNoParameterAddedToErrorCode("transaction.before.activationDate");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (DateUtils.isDateInTheFuture(transactionDate)) {
            baseDataValidator.reset().parameter(dueAsOfDateParamName).value(transactionDate.format(formatter))
                    .failWithCodeNoParameterAddedToErrorCode("transaction.is.futureDate");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (savingsAccountCharge.isSavingsActivation()) {
            baseDataValidator.reset()
                    .failWithCodeNoParameterAddedToErrorCode("transaction.not.valid.cannot.pay.activation.time.charge.is.automated");
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }

        if (savingsAccountCharge.isAnnualFee()) {
            final LocalDate annualFeeDueDate = savingsAccountCharge.getDueDate();
            if (annualFeeDueDate == null) {
                baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("no.annualfee.settings");
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }

            if (!DateUtils.isEqual(annualFeeDueDate, transactionDate)) {
                baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("invalid.date");
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }

            LocalDate currentAnnualFeeNextDueDate = account.findLatestAnnualFeeTransactionDueDate();
            if (DateUtils.isEqual(currentAnnualFeeNextDueDate, transactionDate)) {
                baseDataValidator.reset().parameter("dueDate").value(transactionDate.format(formatter))
                        .failWithCodeNoParameterAddedToErrorCode("transaction.exists.on.date");

                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        // validate charge is not already paid or waived
        if (savingsAccountCharge.isWaived()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.charge.is.already.waived");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        } else if (savingsAccountCharge.isPaid()) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.account.charge.is.paid");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        final Money chargePaid = Money.of(account.getCurrency(), amountPaid);
        if (!savingsAccountCharge.getAmountOutstanding(account.getCurrency()).isGreaterThanOrEqualTo(chargePaid)) {
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("transaction.invalid.charge.amount.paid.in.access");
            if (!dataValidationErrors.isEmpty()) {
                throw new PlatformApiDataValidationException(dataValidationErrors);
            }
        }

        return this.payChargeWithVat(account, savingsAccountCharge, chargePaid, transactionDate, refNo, backdatedTxnsAllowedTill);
    }
}
