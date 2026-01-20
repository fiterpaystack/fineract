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
package com.paystack.fineract.infrastructure.event.external.service.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.event.business.domain.BusinessEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaystackStableExternalEventIdempotencyKeyGenerator.
 */
class PaystackStableExternalEventIdempotencyKeyGeneratorTest {

    private PaystackStableExternalEventIdempotencyKeyGenerator generator;

    private final LocalDate businessDate = LocalDate.now(ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        generator = new PaystackStableExternalEventIdempotencyKeyGenerator();
        // Setup ThreadLocalContextUtil for DateUtils.getBusinessLocalDate()
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, businessDate)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void shouldGenerateSameKeyForSameEvent() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", 123L);

        // When
        String key1 = generator.generate(event);
        String key2 = generator.generate(event);

        // Then
        assertThat(key1).isEqualTo(key2);
        assertThat(key1).isNotNull();
        assertThat(key1).isNotEmpty();
    }

    @Test
    void shouldGenerateDifferentKeyForDifferentEventType() {
        // Given
        BusinessEvent<Object> event1 = createMockEvent("CLIENT_CREATE", 123L);
        BusinessEvent<Object> event2 = createMockEvent("CLIENT_ACTIVATE", 123L);

        // When
        String key1 = generator.generate(event1);
        String key2 = generator.generate(event2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldGenerateDifferentKeyForDifferentAggregateRootId() {
        // Given
        BusinessEvent<Object> event1 = createMockEvent("CLIENT_CREATE", 123L);
        BusinessEvent<Object> event2 = createMockEvent("CLIENT_CREATE", 456L);

        // When
        String key1 = generator.generate(event1);
        String key2 = generator.generate(event2);

        // Then
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    void shouldHandleNullAggregateRootId() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", null);

        // When
        String key = generator.generate(event);

        // Then
        assertThat(key).isNotNull();
        assertThat(key).isNotEmpty();
        assertThat(key).contains("null");
    }

    @Test
    void shouldIncludeEventTypeInKey() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", 123L);

        // When
        String key = generator.generate(event);

        // Then
        assertThat(key).startsWith("CLIENT_CREATE_");
    }

    @Test
    void shouldIncludeAggregateRootIdInKey() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", 123L);

        // When
        String key = generator.generate(event);

        // Then
        assertThat(key).contains("_123_");
    }

    @Test
    void shouldIncludeHashInKey() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", 123L);

        // When
        String key = generator.generate(event);

        // Then
        // Key format: {eventType}_{aggregateRootId}_{businessDate}_{hash}
        String[] parts = key.split("_");
        assertThat(parts.length).isGreaterThanOrEqualTo(4);
        // Last part should be the hash (8 characters)
        assertThat(parts[parts.length - 1].length()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void shouldGenerateStableKeyAcrossMultipleCalls() {
        // Given
        BusinessEvent<Object> event = createMockEvent("CLIENT_CREATE", 123L);

        // When
        String key1 = generator.generate(event);
        String key2 = generator.generate(event);
        String key3 = generator.generate(event);

        // Then
        assertThat(key1).isEqualTo(key2);
        assertThat(key2).isEqualTo(key3);
    }

    private BusinessEvent<Object> createMockEvent(String eventType, Long aggregateRootId) {
        @SuppressWarnings("unchecked")
        BusinessEvent<Object> event = mock(BusinessEvent.class);
        when(event.getType()).thenReturn(eventType);
        when(event.getAggregateRootId()).thenReturn(aggregateRootId);
        when(event.getCategory()).thenReturn("CLIENT");
        when(event.get()).thenReturn(new Object());
        return event;
    }
}
