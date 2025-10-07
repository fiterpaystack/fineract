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

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransaction;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountTransactionRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Extended repository for SavingsAccountTransaction with custom query methods for discount engine
 */
@Repository
public interface PaystackSavingsAccountTransactionRepository extends SavingsAccountTransactionRepository {

    /**
     * Find transactions for a specific period with proper ordering This method extends the core functionality with
     * additional query capabilities
     */
    @Query("SELECT t FROM SavingsAccountTransaction t WHERE t.savingsAccount.id = :accountId AND t.dateOf BETWEEN :startDate AND :endDate AND t.reversed = false ORDER BY t.dateOf, t.createdDate, t.id")
    List<SavingsAccountTransaction> findTransactionsForPeriod(@Param("accountId") Long accountId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Count transactions for a specific period. Optionally include reversed transactions and filter by transaction
     * types.
     *
     * @param accountId
     *            The account ID
     * @param startDate
     *            Start date of the period
     * @param endDate
     *            End date of the period
     * @param includeReversed
     *            Whether to include reversed transactions
     * @param transactionTypes
     *            List of transaction type IDs to filter by. If null or empty, counts all transaction types.
     */
    @Query("SELECT COUNT(t) FROM SavingsAccountTransaction t WHERE t.savingsAccount.id = :accountId "
            + "AND t.dateOf BETWEEN :startDate AND :endDate " + "AND (:includeReversed = true OR t.reversed = false) "
            + "AND (:transactionTypes IS NULL OR :transactionTypes IS EMPTY OR t.typeOf IN :transactionTypes)")
    long countTransactionsForPeriod(@Param("accountId") Long accountId, @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate, @Param("includeReversed") boolean includeReversed,
            @Param("transactionTypes") List<Integer> transactionTypes);
}
