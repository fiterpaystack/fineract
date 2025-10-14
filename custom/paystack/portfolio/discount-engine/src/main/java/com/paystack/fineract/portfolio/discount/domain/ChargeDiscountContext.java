package com.paystack.fineract.portfolio.discount.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;

/**
 * Immutable context for applying discounts to savings charges.
 */
public record ChargeDiscountContext(
        SavingsAccount account,
        SavingsAccountCharge charge,
        BigDecimal originalAmount,
        LocalDate transactionDate
) {
    public static ChargeDiscountContext of(SavingsAccount account, SavingsAccountCharge charge, BigDecimal originalAmount, LocalDate transactionDate) {
        return new ChargeDiscountContext(account, charge, originalAmount, transactionDate);
    }
}


