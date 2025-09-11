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

package com.paystack.fineract.discount.service;

import com.paystack.fineract.discount.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Discount Condition Evaluator
 * Evaluates discount conditions against context
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountConditionEvaluator {
    
    public DiscountEvaluationResult evaluate(ProductDiscountRule rule, DiscountContext context) {
        DiscountEvaluationResult result = new DiscountEvaluationResult();
        result.setRuleId(rule.getId());
        result.setContext(context);
        
        ProductDiscountConditions conditions = rule.getConditions();
        Map<String, Boolean> evaluationResults = new HashMap<>();
        
        try {
            // Account Balance Evaluation
            if (conditions.getAccountBalance() != null) {
                boolean accountBalanceResult = evaluateAccountBalance(conditions.getAccountBalance(), context);
                evaluationResults.put("accountBalance", accountBalanceResult);
                result.addDetail("accountBalance", accountBalanceResult, 
                    "Account balance condition evaluated");
            }
            
            // Transaction Evaluation
            if (conditions.getTransaction() != null) {
                boolean transactionResult = evaluateTransaction(conditions.getTransaction(), context);
                evaluationResults.put("transaction", transactionResult);
                result.addDetail("transaction", transactionResult, 
                    "Transaction condition evaluated");
            }
            
            // Time-Based Evaluation
            if (conditions.getTimeBased() != null) {
                boolean timeBasedResult = evaluateTimeBased(conditions.getTimeBased(), context);
                evaluationResults.put("timeBased", timeBasedResult);
                result.addDetail("timeBased", timeBasedResult, 
                    "Time-based condition evaluated");
            }
            
            // Client Evaluation
            if (conditions.getClient() != null) {
                boolean clientResult = evaluateClient(conditions.getClient(), context);
                evaluationResults.put("client", clientResult);
                result.addDetail("client", clientResult, 
                    "Client condition evaluated");
            }
            
            // Overall eligibility - all enabled conditions must pass
            boolean overallEligible = evaluationResults.values().stream().allMatch(result -> result);
            result.setEligible(overallEligible);
            result.setResults(evaluationResults);
            
        } catch (Exception e) {
            log.error("Error evaluating discount conditions for rule: {}", rule.getId(), e);
            result.setEligible(false);
            result.addDetail("error", false, "Error during evaluation: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Evaluate account balance condition
     */
    private boolean evaluateAccountBalance(ProductDiscountConditions.AccountBalanceCondition condition, 
                                         DiscountContext context) {
        if (!condition.isEnabled()) return true;
        
        try {
            // This would typically fetch from savings account service
            // For now, we'll use a mock implementation
            BigDecimal currentBalance = getCurrentBalance(context, condition.getBalanceType());
            
            if (currentBalance == null) {
                log.debug("Could not determine current balance for account: {}", context.getAccountId());
                return false;
            }
            
            // Minimum balance check
            if (condition.getMinimumBalance() != null && 
                currentBalance.compareTo(condition.getMinimumBalance()) < 0) {
                log.debug("Account balance {} is below minimum required {}", 
                    currentBalance, condition.getMinimumBalance());
                return false;
            }
            
            // Maximum balance check
            if (condition.getMaximumBalance() != null && 
                currentBalance.compareTo(condition.getMaximumBalance()) > 0) {
                log.debug("Account balance {} exceeds maximum allowed {}", 
                    currentBalance, condition.getMaximumBalance());
                return false;
            }
            
            log.debug("Account balance condition passed: {}", currentBalance);
            return true;
            
        } catch (Exception e) {
            log.error("Error evaluating account balance condition", e);
            return false;
        }
    }
    
    /**
     * Evaluate transaction condition
     */
    private boolean evaluateTransaction(ProductDiscountConditions.TransactionCondition condition, 
                                      DiscountContext context) {
        if (!condition.isEnabled()) return true;
        
        try {
            BigDecimal transactionAmount = context.getTransactionAmount();
            
            if (transactionAmount == null) {
                log.debug("Transaction amount is null for context: {}", context);
                return false;
            }
            
            // Minimum amount check
            if (condition.getMinimumAmount() != null && 
                transactionAmount.compareTo(condition.getMinimumAmount()) < 0) {
                log.debug("Transaction amount {} is below minimum required {}", 
                    transactionAmount, condition.getMinimumAmount());
                return false;
            }
            
            // Maximum amount check
            if (condition.getMaximumAmount() != null && 
                transactionAmount.compareTo(condition.getMaximumAmount()) > 0) {
                log.debug("Transaction amount {} exceeds maximum allowed {}", 
                    transactionAmount, condition.getMaximumAmount());
                return false;
            }
            
            log.debug("Transaction condition passed: {}", transactionAmount);
            return true;
            
        } catch (Exception e) {
            log.error("Error evaluating transaction condition", e);
            return false;
        }
    }
    
    /**
     * Evaluate time-based condition
     */
    private boolean evaluateTimeBased(ProductDiscountConditions.TimeBasedCondition condition, 
                                    DiscountContext context) {
        if (!condition.isEnabled()) return true;
        
        try {
            LocalDate now = LocalDate.now();
            
            // Validity period check
            if (condition.getValidFrom() != null) {
                LocalDate validFrom = LocalDate.parse(condition.getValidFrom());
                if (now.isBefore(validFrom)) {
                    log.debug("Current date {} is before valid from date {}", now, validFrom);
                    return false;
                }
            }
            
            if (condition.getValidTo() != null) {
                LocalDate validTo = LocalDate.parse(condition.getValidTo());
                if (now.isAfter(validTo)) {
                    log.debug("Current date {} is after valid to date {}", now, validTo);
                    return false;
                }
            }
            
            // Day of week check
            if (condition.getDaysOfWeek() != null && !condition.getDaysOfWeek().isEmpty()) {
                int dayOfWeek = now.getDayOfWeek().getValue();
                if (!condition.getDaysOfWeek().contains(dayOfWeek)) {
                    log.debug("Current day of week {} is not in allowed days {}", 
                        dayOfWeek, condition.getDaysOfWeek());
                    return false;
                }
            }
            
            log.debug("Time-based condition passed for date: {}", now);
            return true;
            
        } catch (Exception e) {
            log.error("Error evaluating time-based condition", e);
            return false;
        }
    }
    
    /**
     * Evaluate client condition
     */
    private boolean evaluateClient(ProductDiscountConditions.ClientCondition condition, 
                                 DiscountContext context) {
        if (!condition.isEnabled()) return true;
        
        try {
            // This would typically fetch from client service
            // For now, we'll use a mock implementation
            String clientType = determineClientType(context);
            int accountAge = calculateAccountAge(context);
            
            // Client type check
            if (condition.getClientType() != null && 
                !condition.getClientType().equals(clientType)) {
                log.debug("Client type {} does not match required {}", 
                    clientType, condition.getClientType());
                return false;
            }
            
            // Minimum account age check
            if (condition.getMinimumAccountAge() != null && 
                accountAge < condition.getMinimumAccountAge()) {
                log.debug("Account age {} is below minimum required {}", 
                    accountAge, condition.getMinimumAccountAge());
                return false;
            }
            
            // Maximum account age check
            if (condition.getMaximumAccountAge() != null && 
                accountAge > condition.getMaximumAccountAge()) {
                log.debug("Account age {} exceeds maximum allowed {}", 
                    accountAge, condition.getMaximumAccountAge());
                return false;
            }
            
            log.debug("Client condition passed - Type: {}, Age: {}", clientType, accountAge);
            return true;
            
        } catch (Exception e) {
            log.error("Error evaluating client condition", e);
            return false;
        }
    }
    
    /**
     * Get current balance for account (mock implementation)
     */
    private BigDecimal getCurrentBalance(DiscountContext context, String balanceType) {
        // This would typically fetch from savings account service
        // For now, return a mock balance
        return BigDecimal.valueOf(10000); // Mock balance
    }
    
    /**
     * Determine client type (mock implementation)
     */
    private String determineClientType(DiscountContext context) {
        // This would typically fetch from client service
        // For now, return a mock type
        return "EXISTING"; // Mock client type
    }
    
    /**
     * Calculate account age in days (mock implementation)
     */
    private int calculateAccountAge(DiscountContext context) {
        // This would typically fetch from client service
        // For now, return a mock age
        return 365; // Mock account age in days
    }
}
