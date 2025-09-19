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

import com.paystack.fineract.portfolio.discount.domain.DiscountApplication;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Discount Application Repository Handles database operations for discount applications
 */
@Repository
public interface DiscountApplicationRepository extends JpaRepository<DiscountApplication, Long> {

    /**
     * Find applications by discount rule ID
     */
    List<DiscountApplication> findByDiscountRuleId(Long ruleId);

    /**
     * Find applications by entity type and ID
     */
    List<DiscountApplication> findByEntityTypeAndEntityId(String entityType, Long entityId);

    /**
     * Find applications by charge ID
     */
    List<DiscountApplication> findByChargeId(Long chargeId);

    /**
     * Count applications by rule
     */
    @Query("SELECT COUNT(da) FROM DiscountApplication da WHERE da.discountRuleId = :ruleId")
    long countByDiscountRuleId(@Param("ruleId") Long ruleId);

    /**
     * Find applications within date range
     */
    @Query("SELECT da FROM DiscountApplication da WHERE da.applicationDate BETWEEN :startDate AND :endDate ORDER BY da.applicationDate DESC")
    List<DiscountApplication> findByApplicationDateBetween(@Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Get total discount amount by rule
     */
    @Query("SELECT SUM(da.discountAmount) FROM DiscountApplication da WHERE da.discountRuleId = :ruleId")
    java.math.BigDecimal getTotalDiscountAmountByRule(@Param("ruleId") Long ruleId);
}
