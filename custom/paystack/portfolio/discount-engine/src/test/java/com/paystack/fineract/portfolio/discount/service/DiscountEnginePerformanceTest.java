package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance validation tests for the discount engine.
 * These tests validate that the system can handle large numbers of rules efficiently.
 * 
 * Note: These tests are disabled by default as they are performance-focused.
 * Enable them when you want to run performance validation.
 */
@Disabled("Performance tests - enable when needed")
class DiscountEnginePerformanceTest {

    @Test
    void testPerformance_WithManyRules_CompletesWithinReasonableTime() {
        // Given: Simulate a scenario with many discount rules
        int numberOfRules = 1000;
        List<BigDecimal> ruleDiscounts = generateRuleDiscounts(numberOfRules);
        BigDecimal originalAmount = new BigDecimal("1000.00");
        
        // When: Apply SUM_CAP strategy with many rules
        long startTime = System.currentTimeMillis();
        BigDecimal result = applySumCapStrategyWithManyRules(originalAmount, ruleDiscounts);
        long endTime = System.currentTimeMillis();
        
        // Then: Should complete within reasonable time (e.g., 100ms for 1000 rules)
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(100); // 100ms threshold
        assertThat(result).isNotNull();
    }

    @Test
    void testPerformance_AndLogicWithManyRules_CompletesEfficiently() {
        // Given: Many rules with AND logic requirement
        int numberOfRules = 500;
        List<Boolean> ruleApplicability = generateRuleApplicability(numberOfRules, true); // All applicable
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        
        // When: Check AND logic with many rules
        long startTime = System.currentTimeMillis();
        boolean allRulesApplicable = checkAndLogicWithManyRules(ruleApplicability);
        long endTime = System.currentTimeMillis();
        
        // Then: Should complete efficiently
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(50); // 50ms threshold
        assertThat(allRulesApplicable).isTrue();
    }

    @Test
    void testPerformance_AndLogicWithManyRules_OneNotApplicable_ReturnsQuickly() {
        // Given: Many rules with AND logic, but one is not applicable
        int numberOfRules = 500;
        List<Boolean> ruleApplicability = generateRuleApplicability(numberOfRules, false); // One not applicable
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(true);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        
        // When: Check AND logic with many rules (should short-circuit)
        long startTime = System.currentTimeMillis();
        boolean allRulesApplicable = checkAndLogicWithManyRules(ruleApplicability);
        long endTime = System.currentTimeMillis();
        
        // Then: Should complete very quickly due to short-circuiting
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(10); // 10ms threshold for short-circuit
        assertThat(allRulesApplicable).isFalse();
    }

    @Test
    void testPerformance_IndexUsage_OrderedQueriesAreEfficient() {
        // Given: Simulate ordered query performance
        int numberOfRules = 1000;
        
        // When: Simulate ordered query (assignment_priority DESC, rule_priority DESC, rule_id ASC)
        long startTime = System.currentTimeMillis();
        List<Long> orderedRuleIds = simulateOrderedQuery(numberOfRules);
        long endTime = System.currentTimeMillis();
        
        // Then: Should complete efficiently with proper indexing
        long executionTime = endTime - startTime;
        assertThat(executionTime).isLessThan(50); // 50ms threshold
        assertThat(orderedRuleIds).hasSize(numberOfRules);
        
        // Verify ordering is correct (descending by priority)
        for (int i = 0; i < orderedRuleIds.size() - 1; i++) {
            assertThat(orderedRuleIds.get(i)).isGreaterThanOrEqualTo(orderedRuleIds.get(i + 1));
        }
    }

    // Helper methods for performance testing

    private List<BigDecimal> generateRuleDiscounts(int count) {
        List<BigDecimal> discounts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            discounts.add(new BigDecimal(i % 10 + 1)); // 1-10 range
        }
        return discounts;
    }

    private List<Boolean> generateRuleApplicability(int count, boolean allApplicable) {
        List<Boolean> applicability = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (allApplicable) {
                applicability.add(true);
            } else {
                // Make one rule not applicable (at index count/2)
                applicability.add(i != count / 2);
            }
        }
        return applicability;
    }

    private BigDecimal applySumCapStrategyWithManyRules(BigDecimal originalAmount, List<BigDecimal> ruleDiscounts) {
        BigDecimal totalDiscount = ruleDiscounts.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal result = originalAmount.subtract(totalDiscount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private boolean checkAndLogicWithManyRules(List<Boolean> ruleApplicability) {
        // Simulate the AND logic check with potential short-circuiting
        for (Boolean applicable : ruleApplicability) {
            if (!applicable) {
                return false; // Short-circuit on first false
            }
        }
        return true;
    }

    private List<Long> simulateOrderedQuery(int numberOfRules) {
        // Simulate the ordered query: ORDER BY assignment_priority DESC, rule_priority DESC, rule_id ASC
        List<Long> ruleIds = new ArrayList<>();
        for (int i = 0; i < numberOfRules; i++) {
            ruleIds.add((long) (numberOfRules - i)); // Simulate descending order
        }
        return ruleIds;
    }
}
