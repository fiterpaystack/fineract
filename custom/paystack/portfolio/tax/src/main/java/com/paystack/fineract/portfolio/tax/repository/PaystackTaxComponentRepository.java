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

package com.paystack.fineract.portfolio.tax.repository;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.apache.fineract.portfolio.tax.domain.TaxComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PaystackTaxComponentRepository extends JpaRepository<TaxComponent, Long> {

    @Transactional
    @Modifying
    @Query(value = "UPDATE m_tax_component SET credit_account_type_enum = ?2, credit_account_id = ?3, lastmodified_date = ?5, lastmodifiedby_id = ?4 WHERE id = ?1", nativeQuery = true)
    int updateCreditAccounts(Long id, Integer creditAccountType, Long creditAccountId, Long userId, LocalDateTime timestamp);
}
