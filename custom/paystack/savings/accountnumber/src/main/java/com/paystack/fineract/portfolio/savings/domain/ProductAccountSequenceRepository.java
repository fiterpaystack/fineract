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
package com.paystack.fineract.portfolio.savings.domain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductAccountSequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Atomically increments and returns the next sequence number for a given savings product. Relies on PostgreSQL's ON
     * CONFLICT to be concurrency-safe.
     */
    @Transactional
    public long nextForSavingsProduct(long productId) {
        // product_type is fixed to 'SAVINGS' because this module only scopes savings
        String sql = """
                    INSERT INTO product_account_sequence (product_type, product_id, last_number)
                    VALUES ('SAVINGS', ?, 1)
                    ON CONFLICT (product_type, product_id)
                    DO UPDATE SET last_number = product_account_sequence.last_number + 1
                    RETURNING last_number
                """;
        Long next = jdbcTemplate.queryForObject(sql, Long.class, productId);
        if (next == null) {
            throw new IllegalStateException("Failed to allocate next account number sequence for savings product " + productId);
        }
        return next;
    }
}
