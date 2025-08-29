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
package com.paystack.fineract.portfolio.account.service;

import com.paystack.fineract.portfolio.account.domain.FeeSplitAudit;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetail;
import com.paystack.fineract.portfolio.account.dto.FeeSplitAuditResponse;
import com.paystack.fineract.portfolio.account.dto.FeeSplitDetailResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service for mapping domain entities to DTOs for clean API responses
 */
@Service
public class FeeSplitResponseMapper {

    /**
     * Convert FeeSplitDetail entity to DTO
     */
    public FeeSplitDetailResponse toFeeSplitDetailResponse(FeeSplitDetail detail) {
        if (detail == null) {
            return null;
        }

        return FeeSplitDetailResponse.builder().id(detail.getId()).fundName(detail.getFund() != null ? detail.getFund().getName() : null)
                .splitAmount(detail.getSplitAmount()).splitPercentage(detail.getSplitPercentage())
                .glAccountName(detail.getGlAccount() != null ? detail.getGlAccount().getName() : null)
                .glAccountId(detail.getGlAccount() != null ? detail.getGlAccount().getId() : null)
                .journalEntryId(detail.getJournalEntry() != null ? detail.getJournalEntry().getId() : null).build();
    }

    /**
     * Convert list of FeeSplitDetail entities to DTOs
     */
    public List<FeeSplitDetailResponse> toFeeSplitDetailResponses(List<FeeSplitDetail> details) {
        if (details == null) {
            return List.of();
        }

        return details.stream().map(this::toFeeSplitDetailResponse).collect(Collectors.toList());
    }

    /**
     * Convert FeeSplitAudit entity to DTO
     */
    public FeeSplitAuditResponse toFeeSplitAuditResponse(FeeSplitAudit audit) {
        if (audit == null) {
            return null;
        }

        return FeeSplitAuditResponse.builder().id(audit.getId()).transactionId(audit.getTransactionId())
                .chargeId(audit.getCharge() != null ? audit.getCharge().getId() : null)
                .chargeName(audit.getCharge() != null ? audit.getCharge().getName() : null).totalFeeAmount(audit.getTotalFeeAmount())
                .splitDate(audit.getSplitDate()).splitDetails(toFeeSplitDetailResponses(audit.getSplitDetails()))
                .createdBy(audit.getCreatedBy().orElse(null)).createdDate(audit.getCreatedDate().orElse(null))
                .lastModifiedBy(audit.getLastModifiedBy().orElse(null)).lastModifiedDate(audit.getLastModifiedDate().orElse(null)).build();
    }

    /**
     * Convert list of FeeSplitAudit entities to DTOs
     */
    public List<FeeSplitAuditResponse> toFeeSplitAuditResponses(List<FeeSplitAudit> audits) {
        if (audits == null) {
            return List.of();
        }

        return audits.stream().map(this::toFeeSplitAuditResponse).collect(Collectors.toList());
    }
}
