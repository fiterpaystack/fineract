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
import com.paystack.fineract.portfolio.account.domain.FeeSplitAuditRepository;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetail;
import com.paystack.fineract.portfolio.account.domain.FeeSplitDetailRepository;
import com.paystack.fineract.portfolio.account.dto.FeeSplitAuditResponse;
import com.paystack.fineract.portfolio.account.dto.FeeSplitDetailResponse;
import com.paystack.fineract.portfolio.account.dto.FeeSplitSummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing fee split audit operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeSplitAuditService {

    private final FeeSplitAuditRepository feeSplitAuditRepository;
    private final FeeSplitDetailRepository feeSplitDetailRepository;
    private final FeeSplitResponseMapper feeSplitResponseMapper;

    /**
     * Retrieve fee split audits with filters and pagination
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<FeeSplitAudit> retrieveFeeSplitAudits(final Long officeId, final LocalDate fromDate,
            final LocalDate toDate, final String transactionId, final Pageable pageable) {

        Specification<FeeSplitAudit> spec = Specification.where(null);

        // Note: Office filtering is not available since FeeSplitAudit doesn't have direct office information
        // The officeId parameter is kept for API compatibility but not used in filtering
        // Office information can be derived through transaction relationships if needed

        if (fromDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("splitDate"), fromDate));
        }

        if (toDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("splitDate"), toDate));
        }

        if (transactionId != null && !transactionId.trim().isEmpty()) {
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("transactionId")), "%" + transactionId.toLowerCase() + "%"));
        }

        return feeSplitAuditRepository.findAll(spec, pageable);
    }

    /**
     * Retrieve fee split audits for a specific office
     */
    @Transactional(readOnly = true)
    public List<FeeSplitAuditResponse> retrieveFeeSplitAuditsByOffice(final Long officeId) {
        if (officeId == null) {
            throw new IllegalArgumentException("Office ID cannot be null");
        }
        // Use the existing repository method that handles office filtering through transaction relationships
        List<FeeSplitAudit> audits = feeSplitAuditRepository.findByOfficeId(officeId);
        return feeSplitResponseMapper.toFeeSplitAuditResponses(audits);
    }

    /**
     * Retrieve fee split audits for a specific charge
     */
    @Transactional(readOnly = true)
    public List<FeeSplitAuditResponse> retrieveFeeSplitAuditsByCharge(final Long chargeId) {
        if (chargeId == null) {
            throw new IllegalArgumentException("Charge ID cannot be null");
        }
        List<FeeSplitAudit> audits = feeSplitAuditRepository.findByChargeId(chargeId);
        return feeSplitResponseMapper.toFeeSplitAuditResponses(audits);
    }

    /**
     * Retrieve fee split summary statistics
     */
    @Transactional(readOnly = true)
    public FeeSplitSummaryResponse retrieveFeeSplitSummary(final Long officeId, final LocalDate fromDate, final LocalDate toDate) {

        Specification<FeeSplitAudit> spec = Specification.where(null);

        // Note: Office filtering is not available since FeeSplitAudit doesn't have office information
        // The officeId parameter is kept for API compatibility but not used in filtering

        if (fromDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("splitDate"), fromDate));
        }

        if (toDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("splitDate"), toDate));
        }

        List<FeeSplitAudit> audits = feeSplitAuditRepository.findAll(spec);

        BigDecimal totalFeeAmount = audits.stream().map(FeeSplitAudit::getTotalFeeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSplitAmount = audits.stream().flatMap(audit -> audit.getSplitDetails().stream()).map(FeeSplitDetail::getSplitAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Note: Office grouping is not available since FeeSplitAudit doesn't have office information
        Map<String, Object> auditsByOffice = new HashMap<>();

        return FeeSplitSummaryResponse.builder().totalAudits((long) audits.size()).totalFeeAmount(totalFeeAmount)
                .totalSplitAmount(totalSplitAmount).auditsByOffice(auditsByOffice).build();
    }

    /**
     * Create a new fee split audit record
     */
    @Transactional
    public FeeSplitAudit createFeeSplitAudit(final FeeSplitAudit audit) {
        log.info("Creating fee split audit for transaction: {}", audit.getTransactionId());
        return feeSplitAuditRepository.save(audit);
    }

    /**
     * Update an existing fee split audit record
     */
    @Transactional
    public FeeSplitAudit updateFeeSplitAudit(final FeeSplitAudit audit) {
        log.info("Updating fee split audit: {}", audit.getId());
        return feeSplitAuditRepository.save(audit);
    }

    /**
     * Delete a fee split audit record
     */
    @Transactional
    public void deleteFeeSplitAudit(final Long auditId) {
        log.info("Deleting fee split audit: {}", auditId);
        feeSplitAuditRepository.deleteById(auditId);
    }

    /**
     * Get fee split audit by ID
     */
    @Transactional(readOnly = true)
    public FeeSplitAuditResponse getFeeSplitAuditById(final Long auditId) {
        FeeSplitAudit audit = feeSplitAuditRepository.findById(auditId)
                .orElseThrow(() -> new RuntimeException("Fee split audit not found with id: " + auditId));
        return feeSplitResponseMapper.toFeeSplitAuditResponse(audit);
    }

    /**
     * Get fee split details for an audit
     */
    @Transactional(readOnly = true)
    public List<FeeSplitDetailResponse> getFeeSplitDetailsByAuditId(final Long auditId) {
        List<FeeSplitDetail> details = feeSplitDetailRepository.findByAuditId(auditId);
        return feeSplitResponseMapper.toFeeSplitDetailResponses(details);
    }
}
