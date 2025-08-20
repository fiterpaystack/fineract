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

package com.paystack.fineract.client.charge.service;

import com.paystack.fineract.client.charge.dto.ChargeSearchResult;
import com.paystack.fineract.client.charge.dto.ClientChargeResult;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.Page;

public interface ExtendedClientChargeReadPlatformService {

    // Returns up to 10 charges from m_charge for template usage
    List<ChargeSearchResult> retrieveTemplateCharges(String nameKeyWords);

    ClientChargeResult get(Long id);

    Page<ClientChargeResult> listByClient(Long clientId, Integer limit, Integer offset);

}
