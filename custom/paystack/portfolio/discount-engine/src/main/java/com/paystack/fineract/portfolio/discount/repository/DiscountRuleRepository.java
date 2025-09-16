package com.paystack.fineract.portfolio.discount.repository;

import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DiscountRule entity
 */
@Repository
public interface DiscountRuleRepository extends JpaRepository<DiscountRule, Long>, JpaSpecificationExecutor<DiscountRule> {
    
    /**
     * Find all active discount rules
     */
    List<DiscountRule> findByActiveTrueOrderByRulePriorityAsc();
    
    /**
     * Find discount rule by ID and active status
     */
    Optional<DiscountRule> findByIdAndActiveTrue(Long id);
    
    /**
     * Check if discount rule exists by ID and is active
     */
    boolean existsByIdAndActiveTrue(Long id);
}
