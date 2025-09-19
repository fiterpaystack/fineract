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

package com.paystack.fineract.portfolio.discount.data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Discount Application Data Transfer Object
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountApplicationData {

    private Long id;
    private Long discountRuleId;
    private String entityType;
    private Long entityId;
    private Long chargeId;
    private BigDecimal originalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private OffsetDateTime applicationDate;
    private Long transactionId;
    private OffsetDateTime createdDate;
    private OffsetDateTime lastModifiedDate;
    private Long createdBy;
    private Long lastModifiedBy;

    // Additional fields for display
    private String ruleName;
    private String entityName;
    private String chargeName;
}
