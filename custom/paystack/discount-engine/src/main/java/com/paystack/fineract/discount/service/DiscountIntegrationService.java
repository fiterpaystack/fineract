package com.paystack.fineract.discount.service;

import com.paystack.fineract.discount.service.ApplicationContextProvider;
import com.paystack.fineract.discount.service.ProductDiscountService;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;

import java.math.BigDecimal;

/**
 * Discount Integration Service
 * Integrates discount functionality with SavingsAccountCharge
 */
@Slf4j
public class DiscountIntegrationService {
    
    /**
     * Apply discount to savings account charge amount
     */
    public static BigDecimal applyDiscountToCharge(SavingsAccountCharge charge) {
        try {
            // Get the original amount from the charge
            BigDecimal originalAmount = charge.getAmount();
            
            if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return originalAmount;
            }
            
            // Get product ID from savings account
            Long productId = charge.getSavingsAccount().getSavingsProduct().getId();
            
            // Apply discount using the service
            ProductDiscountService discountService = ApplicationContextProvider.getBean(ProductDiscountService.class);
            BigDecimal discountedAmount = discountService.applyDiscount(productId, originalAmount, charge.getId());
            
            log.debug("Applied discount to charge {}: {} -> {}", charge.getId(), originalAmount, discountedAmount);
            
            return discountedAmount;
            
        } catch (Exception e) {
            log.error("Error applying discount to charge: {}", charge.getId(), e);
            // Return original amount if discount application fails
            return charge.getAmount();
        }
    }
    
    /**
     * Update charge amount with discount
     */
    public static void updateChargeAmountWithDiscount(SavingsAccountCharge charge) {
        BigDecimal discountedAmount = applyDiscountToCharge(charge);
        
        if (discountedAmount.compareTo(charge.getAmount()) != 0) {
            log.info("Updating charge {} amount from {} to {}", 
                charge.getId(), charge.getAmount(), discountedAmount);
            
            // Update the amount and outstanding amount
            charge.updateAmount(discountedAmount);
        }
    }
}
