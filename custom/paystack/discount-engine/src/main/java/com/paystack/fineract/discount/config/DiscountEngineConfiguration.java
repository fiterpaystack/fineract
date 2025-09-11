package com.paystack.fineract.discount.config;

import com.paystack.fineract.discount.repository.DiscountApplicationRepository;
import com.paystack.fineract.discount.repository.ProductDiscountRuleRepository;
import com.paystack.fineract.discount.service.ProductDiscountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Discount Engine Configuration
 * Configures discount engine services and beans
 */
@Configuration
public class DiscountEngineConfiguration {
    
    /**
     * Primary Product Discount Service bean
     */
    @Bean
    @Primary
    public ProductDiscountService productDiscountService(
            ProductDiscountRuleRepository ruleRepository,
            DiscountApplicationRepository applicationRepository) {
        return new ProductDiscountService(ruleRepository, applicationRepository);
    }
}
