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

package com.paystack.fineract.tier.service.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public class SavingsAccountTransactionLimitsApiResourceSwagger {

    @Schema(description = "GetSavingsAccountTransactionLimitsSetting")
    public static final class GetSavingsAccountTransactionLimitsSettingResponse {

        private GetSavingsAccountTransactionLimitsSettingResponse() {}

        static final class GetTaxesComponentsCreditAccountType {

            private GetTaxesComponentsCreditAccountType() {}

            @Schema(example = "2")
            public Integer id;
            @Schema(example = "accountType.liability")
            public String code;
            @Schema(example = "LIABILITY")
            public String description;
        }

        static final class GetTaxesComponentsCreditAccount {

            private GetTaxesComponentsCreditAccount() {}

            @Schema(example = "4")
            public Integer id;
            @Schema(example = "ACCOUNT_NAME_7BR9C")
            public String name;
            @Schema(example = "LIABILITY_PA1460364665046")
            public String glCode;
        }

        static final class GetTaxesComponentsHistories {

            private GetTaxesComponentsHistories() {}
        }

        @Schema(example = "1")
        public Long id;
        @Schema(example = "tax component 1")
        public String name;
        @Schema(example = "10.000000")
        public Float percentage;
        @Schema(example = "[2016, 4, 11]")
        public LocalDate startDate;
    }

    @Schema(description = "PostSavingsAccountTransactionLimitsSettingRequest")
    public static final class PostSavingsAccountTransactionLimitsSettingRequest {

        private PostSavingsAccountTransactionLimitsSettingRequest() {}

        @Schema(example = "tax component 1")
        public String name;
    }

    @Schema(description = "PostTaxesComponentsResponse")
    public static final class PostSavingsAccountTransactionLimitsSettingResponse {

        private PostSavingsAccountTransactionLimitsSettingResponse() {}

        @Schema(example = "1")
        public Integer resourceId;
    }

    @Schema(description = "PostSavingsAccountTransactionLimitsSettingRequest")
    public static final class UpdateSavingsAccountTransactionLimitsSettingRequest {

        private UpdateSavingsAccountTransactionLimitsSettingRequest() {}

        @Schema(example = "tax component 1")
        public String name;
    }

    @Schema(description = "PostTaxesComponentsResponse")
    public static final class UpdateSavingsAccountTransactionLimitsSettingResponse {

        private UpdateSavingsAccountTransactionLimitsSettingResponse() {}

        @Schema(example = "1")
        public Integer resourceId;
    }

}
