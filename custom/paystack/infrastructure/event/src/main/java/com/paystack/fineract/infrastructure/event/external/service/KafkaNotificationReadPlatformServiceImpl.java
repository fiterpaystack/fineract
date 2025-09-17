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
package com.paystack.fineract.infrastructure.event.external.service;

import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotification;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationDTO;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationRepository;
import com.paystack.fineract.infrastructure.event.external.domain.KafkaNotificationStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * Implementation of PaystackNotificationReadPlatformService for reading Kafka notifications with search and pagination
 * capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaNotificationReadPlatformServiceImpl implements KafkaNotificationReadPlatformService {

    private final KafkaNotificationRepository kafkaNotificationRepository;

    private static final int DEFAULT_LIMIT = 50;
    private static final int DEFAULT_OFFSET = 0;
    private static final int MAX_LIMIT = 1000;

    @Override
    public Page<KafkaNotificationDTO> searchNotifications(Long customerId, String eventType, Long accountId, KafkaNotificationStatus status,
            LocalDateTime fromDate, LocalDateTime toDate, Integer limit, Integer offset) {

        // Validate and set default values
        int lim = validateAndSetLimit(limit);
        int off = validateAndSetOffset(offset);

        // Create specification for filtering
        Specification<KafkaNotification> spec = createSearchSpecification(customerId, eventType, accountId, status, fromDate, toDate);

        // Create pageable for pagination with ordering by createdDate descending (most recent first)
        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate", "id");
        Pageable pageable = PageRequest.of(off / lim, lim, sort);

        // Execute query with specification (if spec is null, returns all records)
        org.springframework.data.domain.Page<KafkaNotification> notificationPage = kafkaNotificationRepository.findAll(spec, pageable);

        // Convert to DTOs with additional fields populated
        List<KafkaNotificationDTO> notificationDTOs = notificationPage.getContent().stream()
                .map(notification -> new KafkaNotificationDTO(notification, true)).collect(Collectors.toList());

        return new Page<>(notificationDTOs, (int) notificationPage.getTotalElements());
    }

    @Override
    public KafkaNotificationDTO getNotificationById(Long id) {
        return kafkaNotificationRepository.findById(id).map(notification -> new KafkaNotificationDTO(notification, true)).orElse(null);
    }

    @Override
    public Page<KafkaNotificationDTO> getNotificationsByAccount(Long accountId, Integer limit, Integer offset) {
        int lim = validateAndSetLimit(limit);
        int off = validateAndSetOffset(offset);

        // Create pageable for pagination with ordering by createdDate descending (most recent first)
        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate", "id");
        Pageable pageable = PageRequest.of(off / lim, lim, sort);

        // Create specification for account filter
        Specification<KafkaNotification> spec = Specification
                .where((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("account").get("id"), accountId));

        org.springframework.data.domain.Page<KafkaNotification> notificationPage = kafkaNotificationRepository.findAll(spec, pageable);

        List<KafkaNotificationDTO> notificationDTOs = notificationPage.getContent().stream()
                .map(notification -> new KafkaNotificationDTO(notification, true)).collect(Collectors.toList());

        return new Page<>(notificationDTOs, (int) notificationPage.getTotalElements());
    }

    @Override
    public Page<KafkaNotificationDTO> getNotificationsByStatus(KafkaNotificationStatus status, Integer limit, Integer offset) {
        int lim = validateAndSetLimit(limit);
        int off = validateAndSetOffset(offset);

        // Create pageable for pagination with ordering by createdDate descending (most recent first)
        Sort sort = Sort.by(Sort.Direction.DESC, "createdDate", "id");
        Pageable pageable = PageRequest.of(off / lim, lim, sort);

        // Create specification for status filter
        Specification<KafkaNotification> spec = Specification
                .where((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));

        org.springframework.data.domain.Page<KafkaNotification> notificationPage = kafkaNotificationRepository.findAll(spec, pageable);

        List<KafkaNotificationDTO> notificationDTOs = notificationPage.getContent().stream()
                .map(notification -> new KafkaNotificationDTO(notification, true)).collect(Collectors.toList());

        return new Page<>(notificationDTOs, (int) notificationPage.getTotalElements());
    }

    /**
     * Creates a JPA Specification for filtering notifications based on search criteria.
     */
    private Specification<KafkaNotification> createSearchSpecification(Long customerId, String eventType, Long accountId,
            KafkaNotificationStatus status, LocalDateTime fromDate, LocalDateTime toDate) {

        return Specification.where(createCustomerIdSpecification(customerId)).and(createEventTypeSpecification(eventType))
                .and(createAccountIdSpecification(accountId)).and(createStatusSpecification(status))
                .and(createDateRangeSpecification(fromDate, toDate));
    }

    /**
     * Creates specification for customer ID filter.
     */
    private Specification<KafkaNotification> createCustomerIdSpecification(Long customerId) {
        return customerId == null ? null
                : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("account").get("client").get("id"), customerId);
    }

    /**
     * Creates specification for event type filter (case-insensitive partial match).
     */
    private Specification<KafkaNotification> createEventTypeSpecification(String eventType) {
        return eventType == null || eventType.trim().isEmpty() ? null
                : (root, query, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.lower(root.get("eventType")),
                        "%" + eventType.toLowerCase() + "%");
    }

    /**
     * Creates specification for account ID filter.
     */
    private Specification<KafkaNotification> createAccountIdSpecification(Long accountId) {
        return accountId == null ? null : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("account").get("id"), accountId);
    }

    /**
     * Creates specification for status filter.
     */
    private Specification<KafkaNotification> createStatusSpecification(KafkaNotificationStatus status) {
        return status == null ? null : (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status);
    }

    /**
     * Creates specification for date range filter.
     */
    private Specification<KafkaNotification> createDateRangeSpecification(LocalDateTime fromDate, LocalDateTime toDate) {
        Specification<KafkaNotification> spec = null;

        if (fromDate != null) {
            spec = Specification
                    .where((root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), fromDate));
        }

        if (toDate != null) {
            Specification<KafkaNotification> toDateSpec = (root, query, criteriaBuilder) -> criteriaBuilder
                    .lessThanOrEqualTo(root.get("createdDate"), toDate);

            spec = spec == null ? toDateSpec : spec.and(toDateSpec);
        }

        return spec;
    }

    /**
     * Validates and sets the limit parameter with default and maximum constraints.
     */
    private int validateAndSetLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * Validates and sets the offset parameter with default constraints.
     */
    private int validateAndSetOffset(Integer offset) {
        if (offset == null || offset < 0) {
            return DEFAULT_OFFSET;
        }
        return offset;
    }

    @Override
    public void validatePaginationParameters(Integer limit, Integer offset) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("kafkaNotification");

        if (limit != null && (limit <= 0 || limit > 1000)) {
            baseDataValidator.reset().parameter("limit").value(limit)
                    .failWithCodeNoParameterAddedToErrorCode("pagination.limit.must.be.between.one.and.thousand");
        }

        if (offset != null && offset < 0) {
            baseDataValidator.reset().parameter("offset").value(offset)
                    .failWithCodeNoParameterAddedToErrorCode("pagination.offset.must.be.positive");
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    @Override
    public KafkaNotificationStatus validateAndParseStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }

        try {
            return KafkaNotificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new PlatformApiDataValidationException(
                    List.of(ApiParameterError.parameterError("invalid.status.value", "Invalid status value: " + status, "status", status)),
                    e);
        }
    }

    @Override
    public void validateNotificationExists(Long notificationId) {
        if (notificationId == null) {
            throw new PlatformApiDataValidationException(List.of(
                    ApiParameterError.parameterError("notificationId.required", "Notification ID is required", "notificationId", null)));
        }

        boolean exists = kafkaNotificationRepository.existsById(notificationId);
        if (!exists) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError("notificationId.not.found",
                    "Notification not found", "notificationId", notificationId)));
        }
    }
}
