package com.paystack.fineract.portfolio.discount.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Discount Rule Entity
 * Represents discount rules that can be applied to charges and products
 */
@Entity
@Table(name = "m_discount_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscountRule extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "rule_type", length = 50)
    private String ruleType;

    @Column(name = "rule_parameters", columnDefinition = "text")
    private String ruleParametersJson;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private Integer rulePriority = 0;

    // Direct many-to-many relationships following core Fineract patterns
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "m_discount_rule_charge", 
               joinColumns = @JoinColumn(name = "discount_rule_id"), 
               inverseJoinColumns = @JoinColumn(name = "charge_id"))
    private Set<Charge> charges = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "m_discount_rule_product", 
               joinColumns = @JoinColumn(name = "discount_rule_id"), 
               inverseJoinColumns = @JoinColumn(name = "product_id"))
    private Set<SavingsProduct> products = new HashSet<>();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Check if rule is valid for given date
     * Since date validation fields were removed, this always returns true
     */
    public boolean isValidForDate(java.time.LocalDate date) {
        // Date validation removed - rules are always valid for any date
        return true;
    }

    /**
     * Assign discount rule to a charge
     */
    public void assignToCharge(Charge charge) {
        if (charges.contains(charge)) {
            throw new IllegalArgumentException("Discount rule is already assigned to charge: " + charge.getId());
        }
        charges.add(charge);
    }

    /**
     * Unassign discount rule from a charge
     */
    public void unassignFromCharge(Charge charge) {
        charges.remove(charge);
    }

    /**
     * Assign discount rule to a product
     */
    public void assignToProduct(SavingsProduct product) {
        if (products.contains(product)) {
            throw new IllegalArgumentException("Discount rule is already assigned to product: " + product.getId());
        }
        products.add(product);
    }

    /**
     * Unassign discount rule from a product
     */
    public void unassignFromProduct(SavingsProduct product) {
        products.remove(product);
    }

    /**
     * Check if rule is assigned to charge
     */
    public boolean isAssignedToCharge(Long chargeId) {
        return charges.stream().anyMatch(charge -> charge.getId().equals(chargeId));
    }

    /**
     * Check if rule is assigned to product
     */
    public boolean isAssignedToProduct(Long productId) {
        return products.stream().anyMatch(product -> product.getId().equals(productId));
    }

    /**
     * Calculate discount using the new calculator system
     * Falls back to legacy calculation if calculator is not available
     */
    public BigDecimal calculateDiscountWithContext(BigDecimal originalAmount, DiscountContext context) {
        // Try new calculator system first
        if (ruleType != null && ruleParametersJson != null) {
            try {
                return calculateDiscountWithCalculator(originalAmount, context);
            } catch (Exception e) {
                // Fall back to zero if calculator fails
                return BigDecimal.ZERO;
            }
        }
        
        // No legacy calculation available
        return BigDecimal.ZERO;
    }

    /**
     * Calculate discount using the calculator system
     */
    private BigDecimal calculateDiscountWithCalculator(BigDecimal originalAmount, DiscountContext context) {
        // This method is intentionally left empty as calculator-based calculation
        // is handled by the service layer through DiscountRuleCalculatorFactory
        // The entity should not have direct dependencies on the factory
        return BigDecimal.ZERO;
    }

    /**
     * Parse rule parameters from JSON
     */
    public Map<String, Object> getRuleParameters() {
        if (ruleParametersJson == null || ruleParametersJson.trim().isEmpty()) {
            return Map.of();
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.readValue(ruleParametersJson, Map.class);
            return parameters;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse rule parameters JSON: " + ruleParametersJson, e);
        }
    }

    /**
     * Set rule parameters as JSON
     */
    public void setRuleParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            this.ruleParametersJson = null;
            return;
        }
        
        try {
            this.ruleParametersJson = objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize rule parameters to JSON", e);
        }
    }
}