package com.paystack.fineract.portfolio.discount.repository;

import com.paystack.fineract.portfolio.discount.domain.DiscountRule;
import com.paystack.fineract.portfolio.discount.exception.DiscountRuleNotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository wrapper for DiscountRule following core Fineract patterns. Provides validation and error handling
 * capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DiscountRuleRepositoryWrapper {

    private final DiscountRuleRepository repository;

    /**
     * Find discount rule by ID with not found detection
     */
    public DiscountRule findOneWithNotFoundDetection(final Long id) {
        return repository.findById(id).orElseThrow(() -> new DiscountRuleNotFoundException(id));
    }

    /**
     * Check if discount rule exists and is active
     */
    public boolean existsAndActive(final Long id) {
        return repository.existsByIdAndActiveTrue(id);
    }

    /**
     * Find active discount rule by ID
     */
    public Optional<DiscountRule> findActiveById(final Long id) {
        return repository.findByIdAndActiveTrue(id);
    }

    /**
     * Find all active discount rules
     */
    @Transactional(readOnly = true)
    public List<DiscountRule> findAllActive() {
        return repository.findByActiveTrueOrderByRulePriorityAsc();
    }

    /**
     * Save discount rule
     */
    public DiscountRule save(final DiscountRule entity) {
        return repository.save(entity);
    }

    /**
     * Save and flush discount rule
     */
    public DiscountRule saveAndFlush(final DiscountRule entity) {
        return repository.saveAndFlush(entity);
    }

    /**
     * Delete discount rule
     */
    public void delete(final DiscountRule entity) {
        repository.delete(entity);
    }

    /**
     * Validate discount rule exists and is active
     */
    public void validateExistsAndActive(final Long id) {
        if (!existsAndActive(id)) {
            log.error("Discount rule validation failed for ID: {} - rule does not exist or is inactive", id);
            throw new DiscountRuleNotFoundException(id);
        }
    }

    /**
     * Validate multiple discount rules exist and are active
     */
    public void validateAllExistAndActive(final List<Long> ids) {
        for (Long id : ids) {
            validateExistsAndActive(id);
        }
    }
}
