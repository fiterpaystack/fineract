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

package com.paystack.fineract.discount.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Discount Context
 * Contains all information needed for discount evaluation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscountContext {
    
    private String entityType;
    private Long entityId;
    private Long chargeId;
    private BigDecimal originalAmount;
    private LocalDate transactionDate;
    private Long clientId;
    private Long officeId;
    private Long productId;
    private String currencyCode;
    private BigDecimal transactionAmount;
    private Long transactionId;
    private Long userId;
    private Long accountId;
}
