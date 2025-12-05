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
package org.apache.fineract.infrastructure.businessdate.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.businessdate.data.BusinessDateResponse;
import org.apache.fineract.infrastructure.businessdate.data.BusinessDateUpdateRequest;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDate;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateRepository;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.businessdate.exception.BusinessDateActionException;
import org.apache.fineract.infrastructure.businessdate.mapper.BusinessDateUpdateRequestMapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDateWritePlatformServiceImpl implements BusinessDateWritePlatformService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int MIN_RETRY_DELAY_MS = 100;
    private static final int MAX_RETRY_DELAY_MS = 1000;
    private static final Random RANDOM = new Random();

    private final BusinessDateRepository repository;
    private final ConfigurationDomainService configurationDomainService;
    private final BusinessDateUpdateRequestMapper updateRequestMapper;

    @Override
    public BusinessDateResponse updateBusinessDate(BusinessDateUpdateRequest request) {
        BusinessDateResponse businessDateDto = updateRequestMapper.map(request);

        adjustDate(businessDateDto);

        return businessDateDto;
    }

    @Override
    public void increaseDateByTypeByOneDay(BusinessDateType businessDateType) throws JobExecutionException {
        int retryAttempt = 0;
        List<Throwable> exceptions = new ArrayList<>();

        while (retryAttempt < MAX_RETRY_ATTEMPTS) {
            try {
                // Refresh entity from database on each retry to get latest version
                Optional<BusinessDate> businessDateEntity = repository.findByType(businessDateType);
                LocalDate tenantDate = DateUtils.getLocalDateOfTenant();
                LocalDate currentDate = businessDateEntity.map(BusinessDate::getDate).orElse(tenantDate);
                LocalDate expectedTargetDate = tenantDate.plusDays(1);

                // Idempotency check: if business date is already at or beyond the expected target date
                // (tenant date + 1), another process may have already updated it. Skip to avoid conflicts.
                if (businessDateEntity.isPresent() && !DateUtils.isBefore(currentDate, expectedTargetDate)) {
                    if (retryAttempt > 0) {
                        log.info("{} is already at or beyond target date {} (current: {}). Another process may have updated it. Skipping update.",
                                businessDateType.getDescription(), expectedTargetDate, currentDate);
                    } else {
                        log.debug("{} is already at or beyond target date {}. Skipping update.",
                                businessDateType.getDescription(), expectedTargetDate);
                    }
                    return; // Already at or beyond target date, no update needed
                }

                // Calculate target date: current date + 1 day
                LocalDate targetDate = currentDate.plusDays(1);

                BusinessDateResponse response = BusinessDateResponse.builder().type(businessDateType)
                        .description(businessDateType.getDescription()).date(targetDate).build();
                adjustDate(response);

                // Success - exit retry loop
                if (retryAttempt > 0) {
                    log.info("Successfully increased {} by 1 day after {} retry attempt(s)", businessDateType.getDescription(),
                            retryAttempt);
                }
                return;

            } catch (ObjectOptimisticLockingFailureException | jakarta.persistence.OptimisticLockException
                    | org.eclipse.persistence.exceptions.OptimisticLockException e) {
                retryAttempt++;
                if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
                    log.error("Failed to increase {} by 1 day after {} retry attempts due to optimistic lock conflict. "
                            + "Another process may be updating the business date concurrently.", businessDateType.getDescription(),
                            MAX_RETRY_ATTEMPTS);
                    exceptions.add(e);
                } else {
                    // Calculate exponential backoff with jitter: delay = (2^retryAttempt * baseDelay) + random jitter
                    long baseDelay = MIN_RETRY_DELAY_MS;
                    long exponentialDelay = baseDelay * (1L << (retryAttempt - 1)); // 2^(retryAttempt-1)
                    long jitter = RANDOM.nextLong(MIN_RETRY_DELAY_MS, MAX_RETRY_DELAY_MS + 1);
                    long delayMs = Math.min(exponentialDelay + jitter, MAX_RETRY_DELAY_MS * 10); // Cap at 10 seconds

                    log.warn("Optimistic lock conflict when updating {} (attempt {}/{}). Retrying after {}ms...",
                            businessDateType.getDescription(), retryAttempt, MAX_RETRY_ATTEMPTS, delayMs);

                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Thread interrupted during retry delay for {}", businessDateType.getDescription());
                        exceptions.add(ie);
                        break;
                    }
                }
            } catch (final PlatformApiDataValidationException e) {
                final List<ApiParameterError> errors = e.getErrors();
                for (final ApiParameterError error : errors) {
                    log.error("Increasing {} by 1 day failed due to: {}", businessDateType.getDescription(),
                            error.getDeveloperMessage());
                }
                exceptions.add(e);
                break; // Don't retry validation errors
            } catch (final AbstractPlatformDomainRuleException e) {
                log.error("Increasing {} by 1 day failed due to: {}", businessDateType.getDescription(),
                        e.getDefaultUserMessage());
                exceptions.add(e);
                break; // Don't retry business rule exceptions
            } catch (Exception e) {
                // Check if it's an optimistic lock exception wrapped in another exception
                Throwable cause = e.getCause();
                if (cause instanceof ObjectOptimisticLockingFailureException
                        || cause instanceof jakarta.persistence.OptimisticLockException
                        || cause instanceof org.eclipse.persistence.exceptions.OptimisticLockException) {
                    retryAttempt++;
                    if (retryAttempt >= MAX_RETRY_ATTEMPTS) {
                        log.error("Failed to increase {} by 1 day after {} retry attempts due to optimistic lock conflict",
                                businessDateType.getDescription(), MAX_RETRY_ATTEMPTS);
                        exceptions.add(e);
                    } else {
                        long delayMs = MIN_RETRY_DELAY_MS
                                + RANDOM.nextLong(MAX_RETRY_DELAY_MS - MIN_RETRY_DELAY_MS + 1);
                        log.warn("Optimistic lock conflict when updating {} (attempt {}/{}). Retrying after {}ms...",
                                businessDateType.getDescription(), retryAttempt, MAX_RETRY_ATTEMPTS, delayMs);
                        try {
                            Thread.sleep(delayMs);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            exceptions.add(ie);
                            break;
                        }
                        continue; // Retry
                    }
                } else {
                    log.error("Increasing {} by 1 day failed due to: {}", businessDateType.getDescription(), e.getMessage());
                    exceptions.add(e);
                    break; // Don't retry other exceptions
                }
            }
        }

        if (!exceptions.isEmpty()) {
            throw new JobExecutionException(exceptions);
        }
    }

    private void adjustDate(BusinessDateResponse response) {
        boolean isCOBDateAdjustmentEnabled = configurationDomainService.isCOBDateAdjustmentEnabled();
        boolean isBusinessDateEnabled = configurationDomainService.isBusinessDateEnabled();

        if (!isBusinessDateEnabled) {
            log.error("Business date functionality is not enabled!");
            throw new BusinessDateActionException("business.date.is.not.enabled", "Business date functionality is not enabled");
        }
        updateOrCreateBusinessDate(response);
        if (isCOBDateAdjustmentEnabled && BusinessDateType.BUSINESS_DATE.equals(response.getType())) {
            BusinessDateResponse res = BusinessDateResponse.builder().type(BusinessDateType.COB_DATE)
                    .description(BusinessDateType.COB_DATE.getDescription()).date(response.getDate().minusDays(1)).build();
            updateOrCreateBusinessDate(res);
            response.addAllChanges(res.getChanges());
        }
    }

    private void updateOrCreateBusinessDate(BusinessDateResponse businessDateDto) {
        BusinessDateType businessDateType = businessDateDto.getType();
        Optional<BusinessDate> businessDate = repository.findByType(businessDateType);

        if (businessDate.isEmpty()) {
            BusinessDate newBusinessDate = BusinessDate.instance(businessDateType, businessDateDto.getDate());
            repository.save(newBusinessDate);
            businessDateDto.addChange(businessDateType, newBusinessDate.getDate());
        } else {
            updateBusinessDate(businessDate.get(), businessDateDto);
        }
    }

    private void updateBusinessDate(BusinessDate businessDate, BusinessDateResponse businessDateDto) {
        if (DateUtils.isEqual(businessDate.getDate(), businessDateDto.getDate())) {
            return;
        }

        businessDate.setDate(businessDateDto.getDate());
        repository.save(businessDate);

        businessDateDto.addChange(businessDate.getType(), businessDateDto.getDate());
    }
}
