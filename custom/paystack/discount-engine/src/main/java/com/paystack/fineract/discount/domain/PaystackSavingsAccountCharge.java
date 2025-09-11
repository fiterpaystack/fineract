package com.paystack.fineract.discount.domain;

import com.paystack.fineract.discount.service.DiscountIntegrationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;

import java.math.BigDecimal;

/**
 * Extended Savings Account Charge
 * Adds discount functionality to the core SavingsAccountCharge
 */
@Slf4j
public class PaystackSavingsAccountCharge extends SavingsAccountCharge {
    
    /**
     * Calculate charge amount with discount applied
     */
    public BigDecimal calculateChargeAmountWithDiscount() {
        BigDecimal originalAmount = this.getAmount();
        
        if (originalAmount == null || originalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return originalAmount;
        }
        
        try {
            // Apply discount using the integration service
            BigDecimal discountedAmount = DiscountIntegrationService.applyDiscountToCharge(this);
            
            log.debug("Charge {} discount calculation: {} -> {}", 
                this.getId(), originalAmount, discountedAmount);
            
            return discountedAmount;
            
        } catch (Exception e) {
            log.error("Error calculating discounted amount for charge: {}", this.getId(), e);
            return originalAmount;
        }
    }
    
    /**
     * Update charge amount with discount
     */
    public void updateAmountWithDiscount() {
        DiscountIntegrationService.updateChargeAmountWithDiscount(this);
    }
    
    /**
     * Get the effective amount (with discount applied)
     */
    public BigDecimal getEffectiveAmount() {
        return calculateChargeAmountWithDiscount();
    }
}
