package com.paystack.fineract.portfolio.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DiscountRuleServiceAndLogicTest {

    @Test
    void testSumCapStrategy_WhenTotalExceedsOriginalAmount_CapsAtOriginalAmount() {
        // Given
        BigDecimal totalDiscount = new BigDecimal("150.00");
        BigDecimal originalAmount = new BigDecimal("100.00");
        DiscountCombinationStrategy strategy = DiscountCombinationStrategy.SUM_CAP;

        // When
        BigDecimal result = applyCombinationStrategy(totalDiscount, originalAmount, strategy);

        // Then
        assertThat(result).isEqualByComparingTo("0.00"); // 100 - 150 = 0 (capped)
    }

    @Test
    void testSumCapStrategy_WhenTotalLessThanOriginalAmount_ReturnsDifference() {
        // Given
        BigDecimal totalDiscount = new BigDecimal("30.00");
        BigDecimal originalAmount = new BigDecimal("100.00");
        DiscountCombinationStrategy strategy = DiscountCombinationStrategy.SUM_CAP;

        // When
        BigDecimal result = applyCombinationStrategy(totalDiscount, originalAmount, strategy);

        // Then
        assertThat(result).isEqualByComparingTo("70.00"); // 100 - 30 = 70
    }

    @Test
    void testPolicyAndRequired_WhenTrue_IndicatesAllRulesRequired() {
        // Given
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);

        // When & Then
        assertThat(policy.isAndRequired()).isTrue();
    }

    @Test
    void testPolicyAndRequired_WhenFalse_IndicatesNotAllRulesRequired() {
        // Given
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(false);

        // When & Then
        assertThat(policy.isAndRequired()).isFalse();
    }

    // Helper method to test combination strategy logic
    private BigDecimal applyCombinationStrategy(BigDecimal totalDiscount, BigDecimal originalAmount, DiscountCombinationStrategy strategy) {
        if (strategy == DiscountCombinationStrategy.SUM_CAP) {
            BigDecimal result = originalAmount.subtract(totalDiscount);
            return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
        }
        return originalAmount;
    }
}
