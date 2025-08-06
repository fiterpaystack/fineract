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

import java.util.Set;
import org.apache.fineract.client.models.PostTaxesGroupRequest;
import org.apache.fineract.client.models.PutTaxesGroupTaxComponents;
import org.apache.fineract.client.models.PutTaxesGroupTaxGroupIdRequest;
import org.apache.fineract.test.helper.Utils;
import org.springframework.stereotype.Component;

@Component
public class TaxGroupRequestFactory {

    public static final String DATE_FORMAT = "dd MMMM yyyy";
    public static final String DEFAULT_LOCALE = "en";

    public PostTaxesGroupRequest defaultTaxGroupRequest() {
        PostTaxesGroupRequest request = new PostTaxesGroupRequest();
        request.setName(Utils.randomNameGenerator("Tax_Group_", 5));
        request.setDateFormat(DATE_FORMAT);
        request.setLocale(DEFAULT_LOCALE);
        request.setTaxComponents(Set.of());
        return request;
    }

    public PutTaxesGroupTaxGroupIdRequest defaultUpdateRequest(Set<PutTaxesGroupTaxComponents> components) {
        PutTaxesGroupTaxGroupIdRequest request = new PutTaxesGroupTaxGroupIdRequest();
        request.setTaxComponents(components);
        request.setDateFormat(DATE_FORMAT);
        request.setLocale(DEFAULT_LOCALE);
        return request;
    }

}
