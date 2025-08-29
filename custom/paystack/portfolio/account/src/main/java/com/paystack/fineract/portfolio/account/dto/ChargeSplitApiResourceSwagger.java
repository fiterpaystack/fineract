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
package com.paystack.fineract.portfolio.account.dto;

import java.util.Map;

/**
 * Swagger documentation classes for ChargeSplitApiResource
 */
public class ChargeSplitApiResourceSwagger {

    public static class PostChargeSplitResponse {

        private Long resourceId;
        private Long chargeId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getChargeId() { return chargeId; }
        public void setChargeId(Long chargeId) { this.chargeId = chargeId; }
    }

    public static class PutChargeSplitResponse {

        private Long resourceId;
        private Long chargeId;
        private Map<String, Object> changes;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getChargeId() { return chargeId; }
        public void setChargeId(Long chargeId) { this.chargeId = chargeId; }
        public Map<String, Object> getChanges() { return changes; }
        public void setChanges(Map<String, Object> changes) { this.changes = changes; }
    }

    public static class DeleteChargeSplitResponse {

        private Long resourceId;
        private Long chargeId;

        public Long getResourceId() { return resourceId; }
        public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
        public Long getChargeId() { return chargeId; }
        public void setChargeId(Long chargeId) { this.chargeId = chargeId; }
    }
}
