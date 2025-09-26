package com.paystack.fineract.portfolio.savings.domain;

import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Primary
public interface ExtendedSavingsAccountRepository extends SavingsAccountRepository {

    @Query("SELECT sa FROM SavingsAccount sa WHERE sa.product.id = :productId")
    Page<SavingsAccount> findSavingsAccountsByProductId(@Param("productId") Long productId, Pageable pageable);

    @Query("SELECT COUNT(sa) FROM SavingsAccount sa WHERE sa.product.id = :productId")
    long countSavingsAccountsByProductId(@Param("productId") Long productId);
}
