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
package com.paystack.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

/**
 * Swagger schema definitions for PaystackClientsApiResource
 */
final class PaystackClientsApiResourceSwagger {

    private PaystackClientsApiResourceSwagger() {}

    @Schema(description = "PostClientsClientIdExtendedUpgradeToEntityRequest")
    public static final class PostClientsClientIdExtendedUpgradeToEntityRequest {

        private PostClientsClientIdExtendedUpgradeToEntityRequest() {}

        @Schema(example = "15 November 2023", description = "Date when the client upgrade to entity is performed (required)")
        public String upgradeDate;

        @Schema(example = "TechCorp Solutions Limited", description = "Legal name of the business entity (required if not migrating individual names)")
        public String entityName;

        @Schema(example = "true", description = "Whether to migrate the client's first name to the entity name")
        public Boolean migrateFirstName;

        @Schema(example = "true", description = "Whether to migrate the client's last name to the entity name")
        public Boolean migrateLastName;

        @Schema(example = "15 March 2019", description = "Date when the business was incorporated")
        public String incorporationDate;

        @Schema(example = "dd MMMM yyyy", description = "Date format for date fields")
        public String dateFormat;

        @Schema(example = "en", description = "Locale setting")
        public String locale;

        @Schema(description = "Client non-person details required for entity upgrade (required)")
        public ClientNonPersonDetails clientNonPersonDetails;

        static final class ClientNonPersonDetails {

            @Schema(example = "1", description = "Constitution ID (required) - Code value ID for business constitution type")
            public Long constitutionId;

            @Schema(example = "RC1234567890", description = "Incorporation number")
            public String incorpNumber;

            @Schema(example = "15 March 2029", description = "Incorporation validity till date")
            public String incorpValidityTillDate;

            @Schema(example = "2", description = "Main business line ID - Code value ID for primary business activity")
            public Long mainBusinessLineId;

            @Schema(example = "Technology company providing software solutions", description = "Additional remarks about the business")
            public String remarks;
        }
    }

    @Schema(description = "PostClientsClientIdExtendedUpgradeToEntityResponse")
    public static final class PostClientsClientIdExtendedUpgradeToEntityResponse {

        private PostClientsClientIdExtendedUpgradeToEntityResponse() {}

        @Schema(example = "1")
        public Long officeId;

        @Schema(example = "2")
        public Long clientId;

        @Schema(example = "2")
        public Long resourceId;

        @Schema(example = "CLIENT-ENTITY-001")
        public String resourceExternalId;

        @Schema(example = "Client successfully upgraded to entity")
        public String message;

        @Schema(example = "2023-11-01T10:30:00Z")
        public String timestamp;

        @Schema(description = "Updated client information")
        public UpdatedClientInfo clientInfo;

        static final class UpdatedClientInfo {

            @Schema(example = "Business Entity Name")
            public String entityName;

            @Schema(example = "2")
            public Long legalFormId;

            @Schema(example = "entity")
            public String legalFormType;

            @Schema(example = "active")
            public String status;

            @Schema(example = "[2023, 11, 1]")
            public LocalDate upgradeDate;
        }
    }

    @Schema(description = "ErrorResponse")
    public static final class ErrorResponse {

        private ErrorResponse() {}

        @Schema(example = "400")
        public Integer status;

        @Schema(example = "Bad Request")
        public String error;

        @Schema(example = "Client upgrade failed due to business rules violation")
        public String message;

        @Schema(example = "CLIENT_UPGRADE_VALIDATION_ERROR")
        public String errorCode;

        @Schema(example = "2023-11-01T10:30:00Z")
        public String timestamp;

        @Schema(description = "List of validation errors")
        public List<ValidationError> validationErrors;

        static final class ValidationError {

            @Schema(example = "entityName")
            public String field;

            @Schema(example = "Entity name is required")
            public String message;

            @Schema(example = "REQUIRED_FIELD")
            public String code;
        }
    }
}
