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

package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountPolicyEntityType;
import com.paystack.fineract.portfolio.discount.repository.policy.DiscountAssignmentPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscountAssignmentPolicyServiceTest {

    @Mock
    private DiscountAssignmentPolicyRepository policyRepository;

    private DiscountAssignmentPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new DiscountAssignmentPolicyService(policyRepository);
    }

    @Test
    void resolvePolicyOrDefault_WhenPolicyExists_ReturnsExistingPolicy() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.CHARGE;
        Long entityId = 1L;
        DiscountAssignmentPolicy existingPolicy = createPolicy(1L, true);
        
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.of(existingPolicy));

        // When
        DiscountAssignmentPolicy result = policyService.resolvePolicyOrDefault(entityType, entityId);

        // Then
        assertThat(result).isSameAs(existingPolicy);
        verify(policyRepository).findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Test
    void resolvePolicyOrDefault_WhenPolicyDoesNotExist_ReturnsDefaultPolicy() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.SAVINGS_PRODUCT;
        Long entityId = 2L;
        
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.empty());

        // When
        DiscountAssignmentPolicy result = policyService.resolvePolicyOrDefault(entityType, entityId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.isAndRequired()).isFalse(); // Default: AND not required
        assertThat(result.getCombinationStrategy()).isEqualTo(DiscountCombinationStrategy.SUM_CAP);
        verify(policyRepository).findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Test
    void upsertPolicy_WhenPolicyExists_UpdatesExistingPolicy() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.CHARGE;
        Long entityId = 1L;
        boolean allRulesRequired = true;
        DiscountCombinationStrategy combinationStrategy = DiscountCombinationStrategy.SUM_CAP;
        
        DiscountAssignmentPolicy existingPolicy = createPolicy(1L, false);
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.of(existingPolicy));

        // When
        policyService.upsertPolicy(entityType, entityId, allRulesRequired, combinationStrategy);

        // Then
        assertThat(existingPolicy.isAndRequired()).isTrue();
        assertThat(existingPolicy.getCombinationStrategy()).isEqualTo(combinationStrategy);
        verify(policyRepository).save(existingPolicy);
    }

    @Test
    void upsertPolicy_WhenPolicyDoesNotExist_CreatesNewPolicy() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.SAVINGS_PRODUCT;
        Long entityId = 2L;
        boolean allRulesRequired = true;
        DiscountCombinationStrategy combinationStrategy = DiscountCombinationStrategy.SUM_CAP;
        
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.empty());
        when(policyRepository.save(any(DiscountAssignmentPolicy.class)))
            .thenAnswer(invocation -> {
                DiscountAssignmentPolicy policy = invocation.getArgument(0);
                policy.setId(1L);
                return policy;
            });

        // When
        policyService.upsertPolicy(entityType, entityId, allRulesRequired, combinationStrategy);

        // Then
        verify(policyRepository).save(any(DiscountAssignmentPolicy.class));
    }

    @Test
    void deletePolicyIfExists_WhenPolicyExists_DeletesPolicy() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.CHARGE;
        Long entityId = 1L;
        DiscountAssignmentPolicy existingPolicy = createPolicy(1L, true);
        
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.of(existingPolicy));

        // When
        policyService.deletePolicyIfExists(entityType, entityId);

        // Then
        verify(policyRepository).delete(existingPolicy);
    }

    @Test
    void deletePolicyIfExists_WhenPolicyDoesNotExist_DoesNothing() {
        // Given
        DiscountPolicyEntityType entityType = DiscountPolicyEntityType.SAVINGS_PRODUCT;
        Long entityId = 2L;
        
        when(policyRepository.findByEntityTypeAndEntityId(entityType, entityId))
            .thenReturn(Optional.empty());

        // When
        policyService.deletePolicyIfExists(entityType, entityId);

        // Then
        verify(policyRepository).findByEntityTypeAndEntityId(entityType, entityId);
        // No delete call should be made
    }

    private DiscountAssignmentPolicy createPolicy(Long id, boolean andRequired) {
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setId(id);
        policy.setEntityType(DiscountPolicyEntityType.CHARGE);
        policy.setEntityId(1L);
        policy.setAllRulesRequired(andRequired);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        return policy;
    }
}
