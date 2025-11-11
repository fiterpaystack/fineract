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
package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.domain.DiscountApplication;
import com.paystack.fineract.portfolio.discount.domain.DiscountContext;
import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.repository.DiscountApplicationRepository;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing discount application records for audit trail and reporting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountApplicationService {

    private final DiscountApplicationRepository discountApplicationRepository;
    private final DiscountConditionFormatter conditionFormatter;
    private final SavingsAccountRepository savingsAccountRepository;
    private final ChargeRepository chargeRepository;

    /**
     * Save discount application record for audit trail This is called in a separate transaction to avoid affecting the
     * read-only discount calculation
     */
    @Transactional
    public void saveDiscountApplication(DiscountRule rule, String entityType, Long entityId, Long chargeId, BigDecimal originalAmount,
            BigDecimal discountAmount, DiscountContext context, Charge charge) {
        try {
            // Fetch Charge entity if not provided
            if (charge == null && chargeId != null) {
                charge = chargeRepository.findById(chargeId).orElse(null);
                if (charge == null) {
                    log.warn("Charge not found for chargeId: {}, skipping discount application record", chargeId);
                    return;
                }
            }

            // Create discount application record
            DiscountApplication application = DiscountApplication.createNew(rule.getId(), entityType, entityId, chargeId, originalAmount,
                    discountAmount);

            // Populate reporting fields
            populateReportingFields(application, context, rule, charge);

            // Save the record
            discountApplicationRepository.save(application);
            log.debug("Saved discount application record: ruleId={}, discountAmount={}", rule.getId(), discountAmount);

        } catch (Exception e) {
            // Log error but don't fail the discount application
            log.error("Failed to save discount application record for rule {}: {}", rule.getId(), e.getMessage(), e);
        }
    }

    /**
     * Update transaction ID for discount application records Called after transaction is created
     */
    @Transactional
    public void updateTransactionId(Long chargeId, Long accountId, Long transactionId) {
        try {
            // Find the most recent discount application for this charge and account
            // This assumes we're updating the latest one (which should be the case)
            // Only update records without transaction_id
            discountApplicationRepository.findByChargeId(chargeId).stream().filter(da -> da.getTransactionId() == null).filter(da -> {
                // Match by account if we have account info
                if (accountId != null && da.getEntityType().equals("SAVINGS_PRODUCT")) {
                    return da.getEntityId().equals(accountId)
                            || (da.getAccountNumber() != null && getAccountIdFromNumber(da.getAccountNumber()).equals(accountId));
                }
                return true; // Update all if we can't match
            }).findFirst().ifPresent(da -> {
                da.updateTransactionId(transactionId);
                discountApplicationRepository.save(da);
                log.debug("Updated transaction ID {} for discount application {}", transactionId, da.getId());
            });
        } catch (Exception e) {
            log.error("Failed to update transaction ID for discount application: {}", e.getMessage(), e);
        }
    }

    /**
     * Populate reporting fields in discount application
     */
    private void populateReportingFields(DiscountApplication application, DiscountContext context, DiscountRule rule, Charge charge) {
        // Get account information
        String accountNumber = null;
        Long customerId = null;

        if (context.getAccountId() != null) {
            try {
                SavingsAccount account = savingsAccountRepository.findById(context.getAccountId()).orElse(null);
                if (account != null) {
                    accountNumber = account.getAccountNumber();
                    if (account.getClient() != null) {
                        customerId = account.getClient().getId();
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch account information for accountId {}: {}", context.getAccountId(), e.getMessage());
            }
        }

        application.setAccountNumber(accountNumber);
        application.setCustomerId(customerId);

        // Set discount type from rule
        application.setDiscountType(rule.getRuleType() != null ? rule.getRuleType() : "UNKNOWN");

        // Categorize fee type
        application.setFeeTypeCategory(categorizeFeeType(charge));

        // Format triggered conditions
        if (rule.getRuleParametersJson() != null && !rule.getRuleParametersJson().trim().isEmpty()) {
            try {
                Map<String, Object> parameters = rule.getRuleParameters();
                String conditions = conditionFormatter.formatConditions(rule.getRuleType(), parameters);
                application.setTriggeredConditions(conditions);
            } catch (Exception e) {
                log.warn("Failed to format triggered conditions for rule {}: {}", rule.getId(), e.getMessage());
                application.setTriggeredConditions("Conditions parsing failed");
            }
        } else {
            application.setTriggeredConditions("No conditions specified");
        }
    }

    /**
     * Categorize fee type based on charge properties
     */
    private String categorizeFeeType(Charge charge) {
        if (charge == null) {
            return "OTHER";
        }

        // Check if it's a penalty
        if (charge.isPenalty()) {
            return "PENALTY";
        }

        // Categorize by charge time type
        Integer chargeTimeType = charge.getChargeTimeType();
        if (chargeTimeType == null) {
            return "OTHER";
        }

        return switch (chargeTimeType) {
            case 5 -> "TRANSFER"; // WITHDRAWAL_FEE
            case 7, 11, 6, 16 -> "MAINTENANCE"; // MONTHLY_FEE, WEEKLY_FEE, ANNUAL_FEE, SAVINGS_NOACTIVITY_FEE
            default -> "OTHER";
        };
    }

    /**
     * Helper to get account ID from account number (for transaction ID update)
     */
    private Long getAccountIdFromNumber(String accountNumber) {
        try {
            SavingsAccount account = savingsAccountRepository.findSavingsAccountByAccountNumber(accountNumber);
            return account != null ? account.getId() : null;
        } catch (Exception e) {
            log.warn("Failed to get account ID from account number {}: {}", accountNumber, e.getMessage());
            return null;
        }
    }
}
