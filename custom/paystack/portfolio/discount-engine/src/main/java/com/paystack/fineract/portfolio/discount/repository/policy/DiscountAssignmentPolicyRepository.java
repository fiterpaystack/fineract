package com.paystack.fineract.portfolio.discount.repository.policy;

import com.paystack.fineract.portfolio.discount.domain.policy.DiscountAssignmentPolicy;
import com.paystack.fineract.portfolio.discount.domain.policy.DiscountPolicyEntityType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscountAssignmentPolicyRepository extends JpaRepository<DiscountAssignmentPolicy, Long> {

    Optional<DiscountAssignmentPolicy> findByEntityTypeAndEntityId(DiscountPolicyEntityType entityType, Long entityId);

    void deleteByEntityTypeAndEntityId(DiscountPolicyEntityType entityType, Long entityId);
}
