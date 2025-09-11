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

import com.paystack.fineract.discount.domain.DiscountContext;
import com.paystack.fineract.discount.domain.ProductDiscountRule;
import com.paystack.fineract.portfolio.savings.domain.SavingsAccountCharge;
import com.paystack.fineract.portfolio.savings.service.SavingsAccountChargeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Paystack Savings Account Charge Service
 * Extends the base service to include discount functionality
 */
@Service
@Primary
@RequiredArgsConstructor
@Slf4j
public class PaystackSavingsAccountChargeService extends SavingsAccountChargeService {
    
    private final ProductDiscountService productDiscountService;
    
    @Override
    @Transactional
    public BigDecimal calculateChargeAmount(SavingsAccountCharge charge) {
        BigDecimal originalAmount = super.calculateChargeAmount(charge);
        
        log.debug("Calculating charge amount for charge: {}, original amount: {}", 
            charge.getId(), originalAmount);
        
        // Create discount context
        DiscountContext context = DiscountContext.builder()
            .entityType("SAVINGS_ACCOUNT")
            .entityId(charge.getSavingsAccount().getId())
            .chargeId(charge.getCharge().getId())
            .originalAmount(originalAmount)
            .transactionDate(LocalDate.now())
            .clientId(charge.getSavingsAccount().getClient().getId())
            .officeId(charge.getSavingsAccount().getOffice().getId())
            .productId(charge.getSavingsAccount().getSavingsProduct().getId())
            .currencyCode(charge.getSavingsAccount().getCurrency().getCode())
            .accountId(charge.getSavingsAccount().getId())
            .build();
        
        // Apply discount
        BigDecimal finalAmount = productDiscountService.applyDiscount(originalAmount, context);
        
        log.info("Charge calculation completed. Original: {}, Final: {}, Discount: {}", 
            originalAmount, finalAmount, originalAmount.subtract(finalAmount));
        
        return finalAmount;
    }
    
    @Override
    @Transactional
    public void processChargePayment(SavingsAccountCharge charge, BigDecimal paymentAmount) {
        log.debug("Processing charge payment for charge: {}, payment amount: {}", 
            charge.getId(), paymentAmount);
        
        // Calculate discounted amount
        BigDecimal discountedAmount = calculateChargeAmount(charge);
        
        if (discountedAmount.compareTo(charge.getAmount()) != 0) {
            log.info("Discount applied to charge. Original: {}, Discounted: {}", 
                charge.getAmount(), discountedAmount);
            
            // Update charge amount with discount
            charge.updateAmount(discountedAmount);
        }
        
        // Process payment with the final amount
        super.processChargePayment(charge, paymentAmount);
        
        log.info("Charge payment processed successfully for charge: {}", charge.getId());
    }
}
