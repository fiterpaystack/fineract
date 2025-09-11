package com.paystack.fineract.discount.repository;

import com.paystack.fineract.discount.domain.DiscountApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Discount Application Repository
 * Handles database operations for discount applications
 */
@Repository
public interface DiscountApplicationRepository extends JpaRepository<DiscountApplication, Long> {
    
    /**
     * Find applications by discount rule ID
     */
    List<DiscountApplication> findByDiscountRuleId(Long ruleId);
    
    /**
     * Find applications by charge ID
     */
    List<DiscountApplication> findByChargeId(Long chargeId);
    
    /**
     * Count applications by rule
     */
    @Query("SELECT COUNT(da) FROM DiscountApplication da WHERE da.discountRuleId = :ruleId")
    long countByDiscountRuleId(@Param("ruleId") Long ruleId);
}