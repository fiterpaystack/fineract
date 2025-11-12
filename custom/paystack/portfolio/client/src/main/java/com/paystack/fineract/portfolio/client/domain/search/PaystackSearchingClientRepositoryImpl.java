package com.paystack.fineract.portfolio.client.domain.search;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.jpa.CriteriaQueryFactory;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientIdentifier;
import org.apache.fineract.portfolio.client.domain.search.SearchedClient;
import org.apache.fineract.portfolio.client.domain.search.SearchingClientRepositoryImpl;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/**
 * Custom implementation of SearchingClientRepository with case-insensitive search.
 * Extends the core implementation to provide case-insensitive search functionality
 * for client search operations.
 */
@Slf4j
@Repository
@Primary
public class PaystackSearchingClientRepositoryImpl extends SearchingClientRepositoryImpl {

    @PersistenceContext
    private EntityManager entityManager;

    private final CriteriaQueryFactory criteriaQueryFactory;

    public PaystackSearchingClientRepositoryImpl(CriteriaQueryFactory criteriaQueryFactory) {
        super(criteriaQueryFactory);
        this.criteriaQueryFactory = criteriaQueryFactory;
    }

    @Override
    public Page<SearchedClient> searchByText(String searchText, Pageable pageable, String officeHierarchy) {
        String hierarchyLikeValue = officeHierarchy + "%";

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SearchedClient> query = cb.createQuery(SearchedClient.class);

        Root<Client> root = query.from(Client.class);
        Path<Office> office = root.get("office");

        Specification<Client> spec = (r, q, builder) -> {
            Path<Office> o = r.get("office");
            Join<Client, ClientIdentifier> identity = r.join("identifiers", JoinType.LEFT);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.like(o.get("hierarchy"), hierarchyLikeValue));

            // Case-insensitive search: convert both search text and database fields to uppercase
            String normalizedSearchText = searchText != null ? searchText.toUpperCase() : "";
            String searchLikeValue = "%" + normalizedSearchText + "%";
            predicates.add(cb.or(
                    cb.like(cb.upper(r.get("accountNumber")), searchLikeValue),
                    cb.like(cb.upper(r.get("displayName")), searchLikeValue),
                    cb.like(cb.upper(r.get("externalId")), searchLikeValue),
                    cb.like(cb.upper(r.get("mobileNo")), searchLikeValue),
                    cb.like(cb.upper(identity.get("documentKey")), searchLikeValue)));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        criteriaQueryFactory.applySpecificationToCriteria(root, spec, query);

        List<Order> orders = criteriaQueryFactory.ordersFromPageable(pageable, cb, root, () -> cb.desc(root.get("id")));
        query.orderBy(orders);

        query.select(cb.construct(SearchedClient.class, root.get("id"), root.get("displayName"), root.get("externalId"),
                root.get("accountNumber"), office.get("id"), office.get("name"), root.get("mobileNo"), root.get("status"),
                root.get("activationDate"), root.get("createdDate")));

        TypedQuery<SearchedClient> queryToExecute = entityManager.createQuery(query);

        return criteriaQueryFactory.readPage(queryToExecute, Client.class, pageable, spec);
    }
}

