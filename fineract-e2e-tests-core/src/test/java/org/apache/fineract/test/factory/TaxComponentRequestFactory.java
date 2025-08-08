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
package org.apache.fineract.test.factory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.apache.fineract.client.models.PostTaxesComponentsRequest;
import org.apache.fineract.test.helper.Utils;
import org.springframework.stereotype.Component;

@Component
public class TaxComponentRequestFactory {

    public static final String DATE_FORMAT = "dd MMMM yyyy";
    public static final String DEFAULT_LOCALE = "en";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);

    public PostTaxesComponentsRequest defaultTaxComponentRequest() {
        PostTaxesComponentsRequest request = new PostTaxesComponentsRequest();
        request.setName(Utils.randomNameGenerator("Tax_Component_", 5));
        request.setPercentage(10f);
        request.setDateFormat(DATE_FORMAT);
        request.setLocale(DEFAULT_LOCALE);
        request.setStartDate(LocalDate.now(ZoneId.systemDefault()).format(DATE_FORMATTER));
        return request;
    }

    public PostTaxesComponentsRequest taxComponentWithAccountsRequest(Long creditAccountId, Long debitAccountId) {
        PostTaxesComponentsRequest request = defaultTaxComponentRequest();

        if (creditAccountId != null) {
            request.setCreditAccountType(2); // 2 - Liability
            request.setCreditAccountId(creditAccountId);
        }

        if (debitAccountId != null) {
            request.setDebitAccountType(1); // 1 - Asset
            request.setDebitAccountId(debitAccountId);
        }

        return request;
    }

    public PostTaxesComponentsRequest customTaxComponent(String name, float percentage) {
        PostTaxesComponentsRequest request = defaultTaxComponentRequest();
        request.setName(name);
        request.setPercentage(percentage);
        return request;
    }
}
