package com.paystack.fineract.discount.repository;

import com.paystack.fineract.discount.domain.ProductDiscountRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Product Discount Rule Repository
 * Handles database operations for discount rules
 */
@Repository
public interface ProductDiscountRuleRepository extends JpaRepository<ProductDiscountRule, Long> {
    
    /**
     * Find active discount rules by product ID
     */
    @Query("SELECT r FROM ProductDiscountRule r WHERE r.productId = :productId AND r.active = true ORDER BY r.priority DESC")
    List<ProductDiscountRule> findByProductIdAndActive(@Param("productId") Long productId);
    
    /**
     * Count active rules for a product
     */
    @Query("SELECT COUNT(r) FROM ProductDiscountRule r WHERE r.productId = :productId AND r.active = true")
    long countActiveRulesByProductId(@Param("productId") Long productId);
}