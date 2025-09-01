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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeSplitDetailRepository extends JpaRepository<FeeSplitDetail, Long>, JpaSpecificationExecutor<FeeSplitDetail> {

    List<FeeSplitDetail> findByAuditId(Long auditId);

    List<FeeSplitDetail> findByFundId(Long fundId);

    @Query("SELECT fsd FROM FeeSplitDetail fsd WHERE fsd.audit.id = :auditId")
    List<FeeSplitDetail> findDetailsByAuditId(@Param("auditId") Long auditId);

    @Query("SELECT fsd FROM FeeSplitDetail fsd WHERE fsd.fund.id = :fundId")
    List<FeeSplitDetail> findDetailsByFundId(@Param("fundId") Long fundId);

    @Query("SELECT fsd FROM FeeSplitDetail fsd WHERE fsd.journalEntry.id = :journalEntryId")
    Optional<FeeSplitDetail> findByJournalEntryId(@Param("journalEntryId") Long journalEntryId);

    @Query("SELECT SUM(fsd.splitAmount) FROM FeeSplitDetail fsd WHERE fsd.fund.id = :fundId")
    Optional<BigDecimal> getTotalSplitAmountByFundId(@Param("fundId") Long fundId);
}
