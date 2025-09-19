package com.paystack.fineract.portfolio.discount.factory;

import com.paystack.fineract.portfolio.discount.calculator.DiscountRuleCalculator;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Factory for creating discount rule calculators Follows the same pattern as CommandHandlerProvider and other factories
 * in Fineract
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountRuleCalculatorFactory {

    private final ApplicationContext applicationContext;
    private final Map<String, DiscountRuleCalculator> calculators = new HashMap<>();

    @PostConstruct
    public void initializeCalculators() {

        // Get all DiscountRuleCalculator implementations from Spring context
        Map<String, DiscountRuleCalculator> calculatorBeans = applicationContext.getBeansOfType(DiscountRuleCalculator.class);

        for (DiscountRuleCalculator calculator : calculatorBeans.values()) {
            String ruleType = calculator.getRuleType();
            calculators.put(ruleType, calculator);
        }

    }

    /**
     * Create a new calculator instance for the given rule type
     */
    public DiscountRuleCalculator createCalculator(String ruleType, Map<String, Object> parameters) {
        DiscountRuleCalculator template = calculators.get(ruleType);
        if (template == null) {
            throw new IllegalArgumentException("Unknown rule type: " + ruleType);
        }

        try {
            // Create new instance using reflection
            DiscountRuleCalculator instance = template.getClass().getDeclaredConstructor().newInstance();
            instance.configure(parameters);
            return instance;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create calculator instance for rule type: " + ruleType, e);
        }
    }

    /**
     * Get all available rule types
     */
    public List<String> getAvailableRuleTypes() {
        return calculators.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * Get calculator template for a rule type (for metadata purposes)
     */
    public DiscountRuleCalculator getCalculatorTemplate(String ruleType) {
        return calculators.get(ruleType);
    }

    /**
     * Check if a rule type is available
     */
    public boolean isRuleTypeAvailable(String ruleType) {
        return calculators.containsKey(ruleType);
    }
}
