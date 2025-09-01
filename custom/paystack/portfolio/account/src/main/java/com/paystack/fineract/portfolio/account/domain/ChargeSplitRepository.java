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

public interface ChargeSplitRepository extends JpaRepository<ChargeSplit, Long>, JpaSpecificationExecutor<ChargeSplit> {

    List<ChargeSplit> findByChargeIdAndActive(Long chargeId, boolean active);

    @Query("SELECT cs FROM ChargeSplit cs WHERE cs.charge.id = :chargeId AND cs.active = true")
    List<ChargeSplit> findActiveSplitsByChargeId(@Param("chargeId") Long chargeId);

    Optional<ChargeSplit> findByChargeIdAndFundId(Long chargeId, Long fundId);

    @Query("SELECT cs FROM ChargeSplit cs WHERE cs.charge.id = :chargeId AND cs.fund.id = :fundId")
    Optional<ChargeSplit> findByChargeIdAndFundIdQuery(@Param("chargeId") Long chargeId, @Param("fundId") Long fundId);

    @Query("SELECT SUM(cs.splitValue) FROM ChargeSplit cs WHERE cs.charge.id = :chargeId AND cs.active = true AND cs.splitType = 'PERCENTAGE'")
    Optional<BigDecimal> getTotalPercentageByChargeId(@Param("chargeId") Long chargeId);

    @Query("SELECT SUM(cs.splitValue) FROM ChargeSplit cs WHERE cs.charge.id = :chargeId AND cs.active = true AND cs.splitType = 'FLAT_AMOUNT'")
    Optional<BigDecimal> getTotalFlatAmountByChargeId(@Param("chargeId") Long chargeId);

    List<ChargeSplit> findByFundIdAndActive(Long fundId, boolean active);
}
