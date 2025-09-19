package com.paystack.fineract.portfolio.discount.calculator.impl;

import com.paystack.fineract.portfolio.discount.annotation.DiscountRuleType;
import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.springframework.stereotype.Service;

/**
 * Account Balance Based Discount Calculator Applies discounts based on account's average daily balance for current
 * month
 */
@DiscountRuleType(value = "ACCOUNT_BALANCE", category = "SAVINGS_ACCOUNT")
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountBalanceBasedDiscountCalculator implements DiscountRuleCalculator {

    private final SavingsAccountTransactionRepository transactionRepository;

    private BigDecimal minimumAverageBalance;
    private BigDecimal discountPercentage;
    private BigDecimal maxDiscountAmount;

    @Override
    public String getRuleType() {
        return "ACCOUNT_BALANCE";
    }

    @Override
    public String getRuleCategory() {
        return "SAVINGS_ACCOUNT";
    }

    @Override
    public String getRuleDescription() {
        return "Apply discount based on savings account average daily balance for current month";
    }

    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList("minimumAverageBalance", "discountPercentage");
    }

    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList("maxDiscountAmount");
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("minimumAverageBalance", "Minimum average daily balance required for discount (e.g., 500000)");
        descriptions.put("discountPercentage", "Discount percentage to apply (e.g., 5 for 5%)");
        descriptions.put("maxDiscountAmount", "Maximum discount amount allowed (optional)");
        return descriptions;
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal originalAmount, DiscountContext context) {
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (context == null || context.getAccountId() == null) {
            return BigDecimal.ZERO;
        }

        try {
            // Calculate average daily balance for current month
            BigDecimal avgBalance = calculateAverageDailyBalance(context.getAccountId());

            // Check if balance meets threshold
            if (avgBalance.compareTo(minimumAverageBalance) >= 0) {
                // Apply discount
                BigDecimal discount = originalAmount.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                // Apply maximum discount limit if set
                if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                    discount = maxDiscountAmount;
                }

                // Ensure discount doesn't exceed original amount
                if (discount.compareTo(originalAmount) > 0) {
                    discount = originalAmount;
                }

                return discount;
            } else {
                return BigDecimal.ZERO;
            }

        } catch (Exception e) {
            log.error("Error calculating balance-based discount for account {}", context.getAccountId(), e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean isApplicable(DiscountContext context) {
        return context != null && context.getAccountId() != null && context.getTransactionAmount() != null
                && context.getTransactionAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    @Override
    public boolean isValid(DiscountContext context) {
        return minimumAverageBalance != null && minimumAverageBalance.compareTo(BigDecimal.ZERO) > 0 && discountPercentage != null
                && discountPercentage.compareTo(BigDecimal.ZERO) > 0 && discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        if (parameters.containsKey("minimumAverageBalance")) {
            this.minimumAverageBalance = new BigDecimal(parameters.get("minimumAverageBalance").toString());
        }

        if (parameters.containsKey("discountPercentage")) {
            this.discountPercentage = new BigDecimal(parameters.get("discountPercentage").toString());
        }

        if (parameters.containsKey("maxDiscountAmount")) {
            this.maxDiscountAmount = new BigDecimal(parameters.get("maxDiscountAmount").toString());
        }
    }

    /**
     * Calculate average daily balance for current month CORRECTED: Now calculates true average daily balance by
     * considering each day of the month
     */
    private BigDecimal calculateAverageDailyBalance(Long accountId) {
        // Get current month start and end dates
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());

        // Get transactions for the current month (including some from previous month for balance continuity)
        LocalDate extendedStart = monthStart.minusDays(1); // Get one day before to establish starting balance
        List<SavingsAccountTransaction> transactions = transactionRepository.findTransactionsForPeriod(accountId, extendedStart, monthEnd);

        if (transactions.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Sort transactions by date, created date, and ID to ensure proper chronological order
        // This handles multiple transactions on the same day correctly
        // Note: Database query now includes ORDER BY, but we keep this for safety
        transactions.sort((t1, t2) -> {
            int result = t1.getDateOf().compareTo(t2.getDateOf());
            if (result != 0) {
                return result;
            }

            // Compare created dates using Optional handling
            Optional<OffsetDateTime> createdDate1 = t1.getCreatedDate();
            Optional<OffsetDateTime> createdDate2 = t2.getCreatedDate();

            if (createdDate1.isPresent() && createdDate2.isPresent()) {
                result = createdDate1.get().compareTo(createdDate2.get());
                if (result != 0) {
                    return result;
                }
            } else if (createdDate1.isPresent()) {
                return -1; // t1 has created date, t2 doesn't
            } else if (createdDate2.isPresent()) {
                return 1; // t2 has created date, t1 doesn't
            }
            // Both are empty, continue to ID comparison

            return t1.getId().compareTo(t2.getId());
        });

        // Calculate daily balances for each day of the month
        BigDecimal totalBalanceDays = BigDecimal.ZERO;
        int totalDays = 0;

        // Get starting balance (from day before month start or first transaction)
        BigDecimal currentBalance = getStartingBalance(accountId, monthStart, transactions);

        // Preprocess: Map each date to the last (chronologically latest) transaction for that date
        Map<LocalDate, SavingsAccountTransaction> lastTransactionPerDay = new HashMap<>();
        for (SavingsAccountTransaction transaction : transactions) {
            if (!transaction.isReversed() && transaction.getRunningBalance() != null) {
                LocalDate date = transaction.getDateOf();
                SavingsAccountTransaction existing = lastTransactionPerDay.get(date);
                if (existing == null || transaction.getCreatedDate().isAfter(existing.getCreatedDate())) {
                    lastTransactionPerDay.put(date, transaction);
                }
            }
        }

        // Process each day of the month
        LocalDate currentDate = monthStart;
        while (!currentDate.isAfter(monthEnd)) {
            // Update balance if there is a transaction on this date
            SavingsAccountTransaction lastTx = lastTransactionPerDay.get(currentDate);
            if (lastTx != null) {
                currentBalance = lastTx.getRunningBalance();
            }

            // Add this day's balance to the total
            totalBalanceDays = totalBalanceDays.add(currentBalance);
            totalDays++;

            currentDate = currentDate.plusDays(1);
        }

        if (totalDays == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal averageBalance = totalBalanceDays.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);

        return averageBalance;
    }

    /**
     * Get the starting balance for the month
     */
    private BigDecimal getStartingBalance(Long accountId, LocalDate monthStart, List<SavingsAccountTransaction> transactions) {

        // Look for the last transaction before the month start
        SavingsAccountTransaction lastTransaction = null;

        for (SavingsAccountTransaction transaction : transactions) {
            if (transaction.getDateOf().isBefore(monthStart) && !transaction.isReversed() && transaction.getRunningBalance() != null) {
                lastTransaction = transaction;
            }
        }

        if (lastTransaction != null) {
            return lastTransaction.getRunningBalance();
        }

        // If no previous transaction, start with zero
        return BigDecimal.ZERO;
    }
}
