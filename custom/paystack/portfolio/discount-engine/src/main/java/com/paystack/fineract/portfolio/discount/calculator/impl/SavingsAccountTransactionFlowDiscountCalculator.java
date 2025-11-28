package com.paystack.fineract.portfolio.discount.calculator.impl;

import com.paystack.fineract.portfolio.discount.annotation.DiscountRuleType;
import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.repository.PaystackSavingsAccountTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.springframework.stereotype.Service;

/**
 * Value-Based Discount Calculator Applies discounts based on transaction volume (inflows/outflows) within periods
 */
@DiscountRuleType(value = "TRANSACTION_FLOW", category = "SAVINGS_ACCOUNT")
@Service
@RequiredArgsConstructor
@Slf4j
public class SavingsAccountTransactionFlowDiscountCalculator implements DiscountRuleCalculator {

    private final PaystackSavingsAccountTransactionRepository transactionRepository;

    private BigDecimal thresholdAmount;
    private String directionType; // INFLOW, OUTFLOW, COMBINED
    private String periodType; // DAILY, MONTHLY
    private BigDecimal discountPercentage;

    @Override
    public String getRuleType() {
        return "TRANSACTION_FLOW";
    }

    @Override
    public String getRuleCategory() {
        return "SAVINGS_ACCOUNT";
    }

    @Override
    public String getRuleDescription() {
        return "Apply discount based on transaction volume (inflows/outflows) within periods";
    }

    @Override
    public List<String> getRequiredParameters() {
        return Arrays.asList("thresholdAmount", "directionType", "periodType", "discountPercentage");
    }

    @Override
    public List<String> getOptionalParameters() {
        return Arrays.asList();
    }

    @Override
    public Map<String, String> getParameterDescriptions() {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("thresholdAmount", "Minimum transaction volume required for discount (e.g., 200000)");
        descriptions.put("directionType", "Transaction direction: [INFLOW, OUTFLOW, COMBINED]");
        descriptions.put("periodType", "Period for calculation: [DAILY, MONTHLY]");
        descriptions.put("discountPercentage", "Discount percentage to apply (e.g., 10 for 10%)");
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
            // Get transactions for the period
            List<SavingsAccountTransaction> transactions = getTransactionsForPeriod(context.getAccountId());

            if (transactions.isEmpty()) {
                return BigDecimal.ZERO;
            }

            // Calculate total value based on direction
            BigDecimal totalValue = calculateTotalValue(transactions);

            // Apply discount if threshold met
            if (totalValue.compareTo(thresholdAmount) >= 0) {
                BigDecimal discount = originalAmount.multiply(discountPercentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                // Ensure discount doesn't exceed original amount
                if (discount.compareTo(originalAmount) > 0) {
                    discount = originalAmount;
                }

                return discount;
            } else {
                return BigDecimal.ZERO;
            }

        } catch (Exception e) {
            log.error("VALUE CALCULATOR: Error calculating value-based discount for account {}", context.getAccountId(), e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public boolean isApplicable(DiscountContext context) {
        if (context == null || context.getAccountId() == null || context.getTransactionAmount() == null
                || context.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // Check if transaction flow threshold is met - this is critical for AND logic validation
        try {
            List<SavingsAccountTransaction> transactions = getTransactionsForPeriod(context.getAccountId());
            if (transactions.isEmpty()) {
                return false;
            }

            BigDecimal totalValue = calculateTotalValue(transactions);
            return totalValue.compareTo(thresholdAmount) >= 0;
        } catch (Exception e) {
            log.warn("Error checking transaction flow threshold for account {}: {}", context.getAccountId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isValid(DiscountContext context) {
        return thresholdAmount != null && thresholdAmount.compareTo(BigDecimal.ZERO) > 0 && directionType != null
                && !directionType.trim().isEmpty() && periodType != null && !periodType.trim().isEmpty() && discountPercentage != null
                && discountPercentage.compareTo(BigDecimal.ZERO) > 0 && discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    @Override
    public void configure(Map<String, Object> parameters) {
        if (parameters.containsKey("thresholdAmount")) {
            this.thresholdAmount = new BigDecimal(parameters.get("thresholdAmount").toString());
        }

        if (parameters.containsKey("directionType")) {
            this.directionType = parameters.get("directionType").toString();
        }

        if (parameters.containsKey("periodType")) {
            this.periodType = parameters.get("periodType").toString();
        }

        if (parameters.containsKey("discountPercentage")) {
            this.discountPercentage = new BigDecimal(parameters.get("discountPercentage").toString());
        }

    }

    /**
     * Get transactions for the specified period
     */
    private List<SavingsAccountTransaction> getTransactionsForPeriod(Long accountId) {
        LocalDate startDate;
        LocalDate endDate = LocalDate.now();

        switch (periodType.toUpperCase()) {
            case "DAILY":
                startDate = endDate;
            break;
            case "MONTHLY":
                startDate = endDate.withDayOfMonth(1);
            break;
            default:
                log.warn("VALUE CALCULATOR: Unknown period type: {}, defaulting to DAILY", periodType);
                startDate = endDate;
            break;
        }

        return transactionRepository.findTransactionsForPeriod(accountId, startDate, endDate);
    }

    /**
     * Calculate total transaction value based on direction
     */
    private BigDecimal calculateTotalValue(List<SavingsAccountTransaction> transactions) {
        return transactions.stream().filter(t -> !t.isReversed()).filter(this::matchesDirection).map(SavingsAccountTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Check if transaction matches the specified direction
     */
    private boolean matchesDirection(SavingsAccountTransaction transaction) {
        switch (directionType.toUpperCase()) {
            case "INFLOW":
                return transaction.isCredit();
            case "OUTFLOW":
                return transaction.isDebit();
            case "COMBINED":
                return true; // Include all transactions
            default:
                log.warn("VALUE CALCULATOR: Unknown direction type: {}, defaulting to COMBINED", directionType);
                return true;
        }
    }
}
