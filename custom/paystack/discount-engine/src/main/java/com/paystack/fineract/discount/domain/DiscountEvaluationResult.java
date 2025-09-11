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
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Discount Evaluation Result
 * Contains the result of evaluating discount conditions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscountEvaluationResult {
    
    private Long ruleId;
    private DiscountContext context;
    private boolean eligible;
    private Map<String, Boolean> results;
    private Map<String, String> details;

    /**
     * Add evaluation detail
     */
    public void addDetail(String condition, boolean result, String message) {
        if (details == null) {
            details = new java.util.HashMap<>();
        }
        details.put(condition, message + " - Result: " + result);
    }
}
