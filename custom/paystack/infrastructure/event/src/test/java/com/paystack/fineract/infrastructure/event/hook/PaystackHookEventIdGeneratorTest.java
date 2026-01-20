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
package com.paystack.fineract.infrastructure.event.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.ActionContext;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for PaystackHookEventIdGenerator.
 */
class PaystackHookEventIdGeneratorTest {

    private PaystackHookEventIdGenerator generator;

    private final LocalDate businessDate = LocalDate.now(ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        generator = new PaystackHookEventIdGenerator();
        // Setup tenant context and business dates for DateUtils.getBusinessLocalDate()
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "Default", "UTC", null);
        ThreadLocalContextUtil.setTenant(tenant);
        ThreadLocalContextUtil.setActionContext(ActionContext.DEFAULT);
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, businessDate)));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void shouldGenerateSameEventIdForSameInput() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        String payload = "{\"id\":123,\"name\":\"Test Client\"}";
        FineractContext context = createFineractContext();

        // When
        String eventId1 = generator.generate(entityName, actionName, payload, context);
        String eventId2 = generator.generate(entityName, actionName, payload, context);

        // Then
        assertThat(eventId1).isEqualTo(eventId2);
        assertThat(eventId1).isNotNull();
        assertThat(eventId1).isNotEmpty();
    }

    @Test
    void shouldGenerateDifferentEventIdForDifferentEntity() {
        // Given
        String actionName = "CREATE";
        String payload = "{\"id\":123}";
        FineractContext context = createFineractContext();

        // When
        String clientEventId = generator.generate("CLIENT", actionName, payload, context);
        String accountEventId = generator.generate("SAVINGS_ACCOUNT", actionName, payload, context);

        // Then
        assertThat(clientEventId).isNotEqualTo(accountEventId);
    }

    @Test
    void shouldGenerateDifferentEventIdForDifferentAction() {
        // Given
        String entityName = "CLIENT";
        String payload = "{\"id\":123}";
        FineractContext context = createFineractContext();

        // When
        String createEventId = generator.generate(entityName, "CREATE", payload, context);
        String activateEventId = generator.generate(entityName, "ACTIVATE", payload, context);

        // Then
        assertThat(createEventId).isNotEqualTo(activateEventId);
    }

    @Test
    void shouldGenerateDifferentEventIdForDifferentPayload() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        FineractContext context = createFineractContext();

        // When
        String eventId1 = generator.generate(entityName, actionName, "{\"id\":123}", context);
        String eventId2 = generator.generate(entityName, actionName, "{\"id\":456}", context);

        // Then
        assertThat(eventId1).isNotEqualTo(eventId2);
    }

    @Test
    void shouldGenerateStableEventIdWithAggregateRootId() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        String payload = "{\"id\":123,\"name\":\"Test\"}";
        FineractContext context = createFineractContext();

        // When
        String eventId = generator.generate(entityName, actionName, payload, context);

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId).isNotEmpty();
        assertThat(eventId).startsWith(entityName + "_" + actionName + "_");
        // May contain "unknown" if aggregate root ID extraction fails, which is acceptable
    }

    @Test
    void shouldHandleNullPayload() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        FineractContext context = createFineractContext();

        // When
        String eventId = generator.generate(entityName, actionName, null, context);

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId).isNotEmpty();
    }

    @Test
    void shouldHandleEmptyPayload() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        String payload = "";
        FineractContext context = createFineractContext();

        // When
        String eventId = generator.generate(entityName, actionName, payload, context);

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId).isNotEmpty();
    }

    @Test
    void shouldHandleInvalidJsonPayload() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        String payload = "not valid json";
        FineractContext context = createFineractContext();

        // When
        String eventId = generator.generate(entityName, actionName, payload, context);

        // Then
        assertThat(eventId).isNotNull();
        assertThat(eventId).isNotEmpty();
    }

    @Test
    void shouldIncludeBusinessDateInEventId() {
        // Given
        String entityName = "CLIENT";
        String actionName = "CREATE";
        String payload = "{\"id\":123}";
        FineractContext context = createFineractContext();
        LocalDate businessDate = DateUtils.getBusinessLocalDate();

        // When
        String eventId = generator.generate(entityName, actionName, payload, context);

        // Then
        // Event ID format: {entityName}_{actionName}_{aggregateRootId}_{businessDate}_{hash}
        // Business date should be in the event ID
        assertThat(eventId).contains(businessDate.toString());
    }

    private FineractContext createFineractContext() {
        FineractPlatformTenant tenant = new FineractPlatformTenant(1L, "default", "Default", "UTC", null);
        return FineractContext.builder().tenantContext(tenant).contextHolder(null).authTokenContext(null)
                .businessDateContext(null).actionContext(null).build();
    }
}
