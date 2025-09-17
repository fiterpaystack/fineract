package com.paystack.fineract.portfolio.discount.calculator.impl;

import com.paystack.fineract.portfolio.discount.annotation.DiscountRuleType;
import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Account Balance Based Discount Calculator
 * Applies discounts based on account's average daily balance for current month
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
        log.debug("Starting calculation for account {}, amount: {}, minBalance: {}, discount: {}%", 
            context != null ? context.getAccountId() : "null", originalAmount, minimumAverageBalance, discountPercentage);
            
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("Original amount is null or zero, no discount applied");
            return BigDecimal.ZERO;
        }
        
        if (context == null || context.getAccountId() == null) {
            log.debug("No valid context or account ID, no discount applied");
            return BigDecimal.ZERO;
        }
        
        try {
            // Calculate average daily balance for current month
            BigDecimal avgBalance = calculateAverageDailyBalance(context.getAccountId());
            
            log.debug("Account {} average daily balance: {}, minimum required: {}", 
                context.getAccountId(), avgBalance, minimumAverageBalance);
            
            // Check if balance meets threshold
            if (avgBalance.compareTo(minimumAverageBalance) >= 0) {
                // Apply discount
                BigDecimal discount = originalAmount.multiply(discountPercentage)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                
                // Apply maximum discount limit if set
                if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                    discount = maxDiscountAmount;
                }
                
                // Ensure discount doesn't exceed original amount
                if (discount.compareTo(originalAmount) > 0) {
                    discount = originalAmount;
                }
                
                log.debug("Applied balance-based discount: {}% of {} = {} for account {}", 
                    discountPercentage, originalAmount, discount, context.getAccountId());
                
                return discount;
            } else {
                log.debug("Account {} balance {} below threshold {}, no discount applied", 
                    context.getAccountId(), avgBalance, minimumAverageBalance);
                return BigDecimal.ZERO;
            }
            
        } catch (Exception e) {
            log.error("Error calculating balance-based discount for account {}: {}", 
                context.getAccountId(), e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }
    
    @Override
    public boolean isApplicable(DiscountContext context) {
        return context != null && 
               context.getAccountId() != null && 
               context.getTransactionAmount() != null &&
               context.getTransactionAmount().compareTo(BigDecimal.ZERO) > 0;
    }
    
    @Override
    public boolean isValid(DiscountContext context) {
        return minimumAverageBalance != null && 
               minimumAverageBalance.compareTo(BigDecimal.ZERO) > 0 &&
               discountPercentage != null && 
               discountPercentage.compareTo(BigDecimal.ZERO) > 0 && 
               discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0;
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
        
        log.debug("Configured balance-based calculator: minBalance={}, discount={}%, maxDiscount={}",
                 minimumAverageBalance, discountPercentage, maxDiscountAmount);
    }
    
    /**
     * Calculate average daily balance for current month
     * CORRECTED: Now calculates true average daily balance by considering each day of the month
     */
    private BigDecimal calculateAverageDailyBalance(Long accountId) {
        // Get current month start and end dates
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
        
        log.debug("Calculating average daily balance for account {} from {} to {}", 
            accountId, monthStart, monthEnd);
        
        // Get transactions for the current month (including some from previous month for balance continuity)
        LocalDate extendedStart = monthStart.minusDays(1); // Get one day before to establish starting balance
        List<SavingsAccountTransaction> transactions = transactionRepository
            .findTransactionsForPeriod(accountId, extendedStart, monthEnd);
        
        if (transactions.isEmpty()) {
            log.debug("No transactions found for account {} in period {} to {}", 
                accountId, extendedStart, monthEnd);
            return BigDecimal.ZERO;
        }
        
        log.debug("Found {} transactions for account {} in period {} to {}", 
            transactions.size(), accountId, extendedStart, monthEnd);
        
        // Log all transactions found
        for (SavingsAccountTransaction txn : transactions) {
            log.debug("Transaction {} - Date: {}, Amount: {}, Running Balance: {}, Reversed: {}", 
                txn.getId(), txn.getDateOf(), txn.getAmount(), txn.getRunningBalance(), txn.isReversed());
        }
        
        // Sort transactions by date, created date, and ID to ensure proper chronological order
        // This handles multiple transactions on the same day correctly
        // Note: Database query now includes ORDER BY, but we keep this for safety
        transactions.sort((t1, t2) -> {
            int result = t1.getDateOf().compareTo(t2.getDateOf());
            if (result != 0) return result;
            
            // Compare created dates using Optional handling
            Optional<OffsetDateTime> createdDate1 = t1.getCreatedDate();
            Optional<OffsetDateTime> createdDate2 = t2.getCreatedDate();
            
            if (createdDate1.isPresent() && createdDate2.isPresent()) {
                result = createdDate1.get().compareTo(createdDate2.get());
                if (result != 0) return result;
            } else if (createdDate1.isPresent()) {
                return -1; // t1 has created date, t2 doesn't
            } else if (createdDate2.isPresent()) {
                return 1;  // t2 has created date, t1 doesn't
            }
            // Both are empty, continue to ID comparison
            
            return t1.getId().compareTo(t2.getId());
        });
        
        // Calculate daily balances for each day of the month
        BigDecimal totalBalanceDays = BigDecimal.ZERO;
        int totalDays = 0;
        
        // Get starting balance (from day before month start or first transaction)
        BigDecimal currentBalance = getStartingBalance(accountId, monthStart, transactions);
        
        log.debug("Starting balance for account {} on {}: {}", 
            accountId, monthStart, currentBalance);
        
        // Process each day of the month
        LocalDate currentDate = monthStart;
        int transactionIndex = 0;
        
        log.debug("Starting daily balance calculation for account {} from {} to {}", 
            accountId, monthStart, monthEnd);
        
        while (!currentDate.isAfter(monthEnd)) {
            BigDecimal balanceAtStartOfDay = currentBalance;
            
            // Update balance if there are transactions on this date
            while (transactionIndex < transactions.size()) {
                SavingsAccountTransaction transaction = transactions.get(transactionIndex);
                
                if (transaction.getDateOf().isAfter(currentDate)) {
                    break; // No more transactions for this date
                }
                
                if (transaction.getDateOf().equals(currentDate)) {
                    log.debug("Processing transaction {} on {} - Amount: {}, Running Balance: {}, Reversed: {}", 
                        transaction.getId(), currentDate, transaction.getAmount(), 
                        transaction.getRunningBalance(), transaction.isReversed());
                    
                    if (!transaction.isReversed() && transaction.getRunningBalance() != null) {
                        currentBalance = transaction.getRunningBalance();
                        log.debug("Updated balance for account {} on {}: {} → {} (from transaction {})", 
                            accountId, currentDate, balanceAtStartOfDay, currentBalance, transaction.getId());
                    } else {
                        log.debug("Skipping transaction {} - Reversed: {}, Running Balance: {}", 
                            transaction.getId(), transaction.isReversed(), transaction.getRunningBalance());
                    }
                }
                
                transactionIndex++;
            }
            
            // Add this day's balance to the total
            totalBalanceDays = totalBalanceDays.add(currentBalance);
            totalDays++;
            
            log.debug("Day {} ({}) - Balance: {}, Running Total: {}, Days Counted: {}", 
                currentDate, currentDate.getDayOfWeek(), currentBalance, totalBalanceDays, totalDays);
            
            currentDate = currentDate.plusDays(1);
        }
        
        if (totalDays == 0) {
            log.debug("No days processed for account {}", accountId);
            return BigDecimal.ZERO;
        }
        
        BigDecimal averageBalance = totalBalanceDays.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        
        log.debug("Final calculation summary - Account ID: {}, Period: {} to {}, Total Days: {}, Total Balance Days: {}, Average Daily Balance: {}", 
            accountId, monthStart, monthEnd, totalDays, totalBalanceDays, averageBalance);
        
        return averageBalance;
    }
    
    /**
     * Get the starting balance for the month
     */
    private BigDecimal getStartingBalance(Long accountId, LocalDate monthStart, List<SavingsAccountTransaction> transactions) {
        log.debug("Looking for starting balance for account {} before {}", 
            accountId, monthStart);
        
        // Look for the last transaction before the month start
        SavingsAccountTransaction lastTransaction = null;
        
        for (SavingsAccountTransaction transaction : transactions) {
            log.debug("Checking transaction {} - Date: {}, Before Month Start: {}, Reversed: {}, Running Balance: {}", 
                transaction.getId(), transaction.getDateOf(), 
                transaction.getDateOf().isBefore(monthStart), 
                transaction.isReversed(), 
                transaction.getRunningBalance());
            
            if (transaction.getDateOf().isBefore(monthStart) && 
                !transaction.isReversed() && 
                transaction.getRunningBalance() != null) {
                lastTransaction = transaction;
                log.debug("Found candidate starting balance transaction {} on {}: {}", 
                    transaction.getId(), transaction.getDateOf(), transaction.getRunningBalance());
            }
        }
        
        if (lastTransaction != null) {
            log.debug("Using balance from transaction {} on {}: {}", 
                lastTransaction.getId(), lastTransaction.getDateOf(), lastTransaction.getRunningBalance());
            return lastTransaction.getRunningBalance();
        }
        
        // If no previous transaction, start with zero
        log.debug("No previous transaction found before {}, starting with zero balance", monthStart);
        return BigDecimal.ZERO;
    }
}
