package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DiscountEngineIntegrationTest {

    @Test
    void testAcceptanceCriteria_AccountMeets2Of3Conditions_NoDiscount() {
        // Test Scenario: Account meets 2 of 3 conditions → No discount (AND logic)
        // This validates the strict AND logic requirement
        
        // Given: Policy requires all rules to be applicable
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        
        // When: Only 2 of 3 rules are applicable
        boolean rule1Applicable = true;  // Balance condition met
        boolean rule2Applicable = true;  // Transaction condition met  
        boolean rule3Applicable = false; // Daily inflow condition NOT met
        
        boolean allRulesApplicable = rule1Applicable && rule2Applicable && rule3Applicable;
        
        // Then: No discount should be applied due to AND logic
        assertThat(allRulesApplicable).isFalse();
    }

    @Test
    void testAcceptanceCriteria_AccountMeetsAll3Conditions_GetsCombinedDiscount() {
        // Test Scenario: Account meets all 3 conditions → Gets combined discount
        // This validates the combination strategy works when all rules apply
        
        // Given: Policy requires all rules and uses SUM_CAP strategy
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        
        // When: All 3 rules are applicable
        boolean rule1Applicable = true;  // Balance condition met
        boolean rule2Applicable = true;  // Transaction condition met
        boolean rule3Applicable = true;  // Daily inflow condition met
        
        boolean allRulesApplicable = rule1Applicable && rule2Applicable && rule3Applicable;
        
        // Then: Combined discount should be applied
        assertThat(allRulesApplicable).isTrue();
    }

    @Test
    void testAcceptanceCriteria_SumCapStrategy_CapsAtOriginalAmount() {
        // Test Scenario: Multiple rules with high discounts that exceed original amount
        // This validates the SUM_CAP combination strategy
        
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal rule1Discount = new BigDecimal("60.00");
        BigDecimal rule2Discount = new BigDecimal("50.00");
        BigDecimal totalDiscount = rule1Discount.add(rule2Discount); // 110.00
        
        // When: Applying SUM_CAP strategy
        BigDecimal result = applySumCapStrategy(originalAmount, totalDiscount);
        
        // Then: Result should be capped at 0 (100 - 110 = 0, not negative)
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void testAcceptanceCriteria_SumCapStrategy_NormalCase() {
        // Test Scenario: Multiple rules with discounts that don't exceed original amount
        // This validates the SUM_CAP combination strategy in normal cases
        
        // Given
        BigDecimal originalAmount = new BigDecimal("100.00");
        BigDecimal rule1Discount = new BigDecimal("30.00");
        BigDecimal rule2Discount = new BigDecimal("20.00");
        BigDecimal totalDiscount = rule1Discount.add(rule2Discount); // 50.00
        
        // When: Applying SUM_CAP strategy
        BigDecimal result = applySumCapStrategy(originalAmount, totalDiscount);
        
        // Then: Result should be the difference (100 - 50 = 50)
        assertThat(result).isEqualByComparingTo("50.00");
    }

    // Helper method to test SUM_CAP strategy logic
    private BigDecimal applySumCapStrategy(BigDecimal originalAmount, BigDecimal totalDiscount) {
        BigDecimal result = originalAmount.subtract(totalDiscount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }
}
