package com.paystack.fineract.portfolio.discount.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import org.junit.jupiter.api.Test;

class ProductDiscountServiceAndLogicTest {

    @Test
    void testPolicyConfiguration_WhenCreatedWithDefaults_HasCorrectValues() {
        // Given
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();

        // When & Then
        assertThat(policy.isAndRequired()).isFalse(); // Default: AND not required
        assertThat(policy.getCombinationStrategy()).isEqualTo(DiscountCombinationStrategy.SUM_CAP);
    }

    @Test
    void testPolicyConfiguration_WhenSetToAndRequired_ReflectsCorrectly() {
        // Given
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);

        // When & Then
        assertThat(policy.isAndRequired()).isTrue();
    }

    @Test
    void testPolicyConfiguration_WhenSetToDifferentStrategy_ReflectsCorrectly() {
        // Given
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);

        // When & Then
        assertThat(policy.getCombinationStrategy()).isEqualTo(DiscountCombinationStrategy.SUM_CAP);
    }
}
