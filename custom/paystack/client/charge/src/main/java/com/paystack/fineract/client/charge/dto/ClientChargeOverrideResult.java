/*
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
package com.paystack.fineract.client.charge.dto;

import com.paystack.fineract.client.charge.domain.ClientChargeOverride;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class ClientChargeOverrideResult {

    private Long id;
    private Long clientId;
    private Long chargeId;
    private BigDecimal amount;
    private BigDecimal minCap;
    private BigDecimal maxCap;

    public static ClientChargeOverrideResult fromEntity(ClientChargeOverride e) {
        ClientChargeOverrideResult r = new ClientChargeOverrideResult();
        r.id = e.getId();
        r.clientId = e.getClient() != null ? e.getClient().getId() : null;
        r.chargeId = e.getCharge() != null ? e.getCharge().getId() : null;
        r.amount = e.getAmount();
        r.minCap = e.getMinCap();
        r.maxCap = e.getMaxCap();
        return r;
    }
}
