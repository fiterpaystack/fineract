package com.paystack.fineract.portfolio.discount.service;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountCombinationStrategy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountPolicyEntityType;
import com.paystack.fineract.portfolio.discount.repository.policy.DiscountAssignmentPolicyRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DiscountAssignmentPolicyService {

    private final DiscountAssignmentPolicyRepository policyRepository;

    @Transactional(readOnly = true)
    public Optional<DiscountAssignmentPolicy> findPolicy(DiscountPolicyEntityType entityType, Long entityId) {
        return policyRepository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Transactional(readOnly = true)
    public DiscountAssignmentPolicy resolvePolicyOrDefault(DiscountPolicyEntityType entityType, Long entityId) {
        return findPolicy(entityType, entityId).orElseGet(this::defaultPolicy);
    }

    private DiscountAssignmentPolicy defaultPolicy() {
        DiscountAssignmentPolicy policy = new DiscountAssignmentPolicy();
        policy.setAllRulesRequired(false);
        policy.setCombinationStrategy(DiscountCombinationStrategy.SUM_CAP);
        return policy;
    }

    @Transactional
    public void deletePolicyIfExists(DiscountPolicyEntityType entityType, Long entityId) {
        policyRepository.findByEntityTypeAndEntityId(entityType, entityId)
            .ifPresent(policyRepository::delete);
    }

    @Transactional
    public DiscountAssignmentPolicy upsertPolicy(DiscountPolicyEntityType entityType, Long entityId, boolean allRulesRequired,
            DiscountCombinationStrategy combinationStrategy) {
        DiscountAssignmentPolicy policy = policyRepository.findByEntityTypeAndEntityId(entityType, entityId)
                .orElseGet(DiscountAssignmentPolicy::new);
        policy.setEntityType(entityType);
        policy.setEntityId(entityId);
        policy.setAllRulesRequired(allRulesRequired);
        policy.setCombinationStrategy(combinationStrategy != null ? combinationStrategy : DiscountCombinationStrategy.SUM_CAP);
        return policyRepository.save(policy);
    }
}


