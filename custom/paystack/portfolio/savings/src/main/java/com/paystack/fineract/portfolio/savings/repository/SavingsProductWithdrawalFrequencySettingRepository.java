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

package com.paystack.fineract.portfolio.savings.repository;

import com.paystack.fineract.portfolio.savings.domain.SavingsProductWithdrawalFrequencySetting;
import com.paystack.fineract.portfolio.savings.domain.TimePeriod;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for SavingsProductWithdrawalFrequencySetting
 * Handles database operations for product-level withdrawal frequency settings
 */
@Repository
public interface SavingsProductWithdrawalFrequencySettingRepository extends JpaRepository<SavingsProductWithdrawalFrequencySetting, Long> {
    
    /**
     * Find all active settings for a savings product
     */
    List<SavingsProductWithdrawalFrequencySetting> findBySavingsProductIdAndIsActive(Long productId, Boolean isActive);
    
    /**
     * Find a specific setting for a product and time period
     */
    Optional<SavingsProductWithdrawalFrequencySetting> findBySavingsProductIdAndTimePeriodAndIsActive(
        Long productId, TimePeriod timePeriod, Boolean isActive);
    
    /**
     * Find all settings for a product (active and inactive)
     */
    List<SavingsProductWithdrawalFrequencySetting> findBySavingsProductId(Long productId);
    
    /**
     * Check if a setting exists for a product and time period
     */
    boolean existsBySavingsProductIdAndTimePeriodAndIsActive(Long productId, TimePeriod timePeriod, Boolean isActive);
    
    /**
     * Deactivate all settings for a product
     */
    @Modifying
    @Query("UPDATE SavingsProductWithdrawalFrequencySetting s SET s.isActive = false WHERE s.savingsProductId = :productId")
    void deactivateByProductId(@Param("productId") Long productId);
    
    /**
     * Deactivate a specific setting for a product and time period
     */
    @Modifying
    @Query("UPDATE SavingsProductWithdrawalFrequencySetting s SET s.isActive = false WHERE s.savingsProductId = :productId AND s.timePeriod = :timePeriod")
    void deactivateByProductIdAndTimePeriod(@Param("productId") Long productId, @Param("timePeriod") TimePeriod timePeriod);
    
    /**
     * Count active settings for a product
     */
    long countBySavingsProductIdAndIsActive(Long productId, Boolean isActive);
    
    /**
     * Find settings by time period across all products
     */
    List<SavingsProductWithdrawalFrequencySetting> findByTimePeriodAndIsActive(TimePeriod timePeriod, Boolean isActive);
}
