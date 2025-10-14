/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.paystack.fineract.portfolio.discount.repository;

import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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

    /**
     * Find active rules assigned to a charge ordered by assignment priority then rule priority.
     */
    @Query(value = "SELECT dr.* FROM m_discount_rule dr " + "JOIN m_discount_rule_charge rc ON rc.discount_rule_id = dr.id "
            + "WHERE rc.charge_id = ?1 AND dr.is_active = true "
            + "ORDER BY rc.assignment_priority DESC, dr.priority DESC, dr.id ASC", nativeQuery = true)
    List<DiscountRule> findActiveByChargeOrdered(Long chargeId);

    /**
     * Find active rules assigned to a product ordered by assignment priority then rule priority.
     */
    @Query(value = "SELECT dr.* FROM m_discount_rule dr " + "JOIN m_discount_rule_product rp ON rp.discount_rule_id = dr.id "
            + "WHERE rp.product_id = ?1 AND dr.is_active = true "
            + "ORDER BY rp.assignment_priority DESC, dr.priority DESC, dr.id ASC", nativeQuery = true)
    List<DiscountRule> findActiveByProductOrdered(Long productId);

    /**
     * Bulk delete assignments by charge ID.
     */
    @Modifying
    @Query(value = "DELETE FROM m_discount_rule_charge WHERE charge_id = ?1", nativeQuery = true)
    int deleteAssignmentsByCharge(Long chargeId);

    /**
     * Bulk delete assignments by product ID.
     */
    @Modifying
    @Query(value = "DELETE FROM m_discount_rule_product WHERE product_id = ?1", nativeQuery = true)
    int deleteAssignmentsByProduct(Long productId);

    /**
     * Update assignment priority for a specific charge-rule association.
     */
    @Modifying
    @Query(value = "UPDATE m_discount_rule_charge SET assignment_priority = ?3 WHERE charge_id = ?1 AND discount_rule_id = ?2", nativeQuery = true)
    int updateChargeAssignmentPriority(Long chargeId, Long ruleId, int priority);

    /**
     * Update assignment priority for a specific product-rule association.
     */
    @Modifying
    @Query(value = "UPDATE m_discount_rule_product SET assignment_priority = ?3 WHERE product_id = ?1 AND discount_rule_id = ?2", nativeQuery = true)
    int updateProductAssignmentPriority(Long productId, Long ruleId, int priority);

    /**
     * Get assignment data for charge with priority information. Note: This query works both with and without
     * assignment_priority column.
     */
    @Query(value = """
            SELECT dr.id as ruleId, dr.name as ruleName, dr.description as ruleDescription,
                   dr.is_active as active, dr.priority as rulePriority, dr.rule_type as ruleType,
                   dr.rule_parameters as ruleParametersJson, dr.created_on_utc as createdOnUtc,
                   dr.last_modified_on_utc as lastModifiedOnUtc, dr.created_by as createdBy,
                   dr.last_modified_by as lastModifiedBy,
                   COALESCE(rp.assignment_priority, 0) as assignmentPriority
            FROM m_discount_rule dr
            JOIN m_discount_rule_charge rp ON rp.discount_rule_id = dr.id
            WHERE rp.charge_id = ?1 AND dr.is_active = true
            ORDER BY COALESCE(rp.assignment_priority, 0) DESC, dr.priority DESC, dr.id ASC
            """, nativeQuery = true)
    List<Object[]> findAssignmentDataByCharge(Long chargeId);

    /**
     * Get assignment data for product with priority information. Note: This query works both with and without
     * assignment_priority column.
     */
    @Query(value = """
            SELECT dr.id as ruleId, dr.name as ruleName, dr.description as ruleDescription,
                   dr.is_active as active, dr.priority as rulePriority, dr.rule_type as ruleType,
                   dr.rule_parameters as ruleParametersJson, dr.created_on_utc as createdOnUtc,
                   dr.last_modified_on_utc as lastModifiedOnUtc, dr.created_by as createdBy,
                   dr.last_modified_by as lastModifiedBy,
                   COALESCE(rp.assignment_priority, 0) as assignmentPriority
            FROM m_discount_rule dr
            JOIN m_discount_rule_product rp ON rp.discount_rule_id = dr.id
            WHERE rp.product_id = ?1 AND dr.is_active = true
            ORDER BY COALESCE(rp.assignment_priority, 0) DESC, dr.priority DESC, dr.id ASC
            """, nativeQuery = true)
    List<Object[]> findAssignmentDataByProduct(Long productId);
}
