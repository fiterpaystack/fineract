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
package com.paystack.fineract.portfolio.account.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeSplitAuditRepository extends JpaRepository<FeeSplitAudit, Long>, JpaSpecificationExecutor<FeeSplitAudit> {

    Optional<FeeSplitAudit> findByTransactionId(String transactionId);

    List<FeeSplitAudit> findByChargeId(Long chargeId);

    List<FeeSplitAudit> findBySplitDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT fsa FROM FeeSplitAudit fsa WHERE fsa.splitDate >= :startDate AND fsa.splitDate <= :endDate")
    List<FeeSplitAudit> findAuditsByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT fsa FROM FeeSplitAudit fsa WHERE fsa.charge.id = :chargeId AND fsa.splitDate >= :startDate AND fsa.splitDate <= :endDate")
    List<FeeSplitAudit> findAuditsByChargeAndDateRange(@Param("chargeId") Long chargeId, 
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT fsa FROM FeeSplitAudit fsa JOIN fsa.splitDetails fsd WHERE fsd.fund.id = :fundId AND fsa.splitDate >= :startDate AND fsa.splitDate <= :endDate")
    List<FeeSplitAudit> findAuditsByFundAndDateRange(@Param("fundId") Long fundId, 
            @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find fee split audits by office ID through transaction relationships
     */
    @Query("SELECT DISTINCT fsa FROM FeeSplitAudit fsa " +
           "JOIN SavingsAccountTransaction sat ON sat.refNo = fsa.transactionId " +
           "JOIN SavingsAccount sa ON sa.id = sat.savingsAccount.id " +
           "JOIN Client c ON c.id = sa.client.id " +
           "WHERE c.office.id = :officeId")
    List<FeeSplitAudit> findByOfficeId(@Param("officeId") Long officeId);
}
