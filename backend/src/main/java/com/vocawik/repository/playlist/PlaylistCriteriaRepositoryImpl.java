package com.vocawik.repository.playlist;

import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.resource.ResourceName;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Criteria API implementation for playlist search. */
@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EntityManager is a container-managed dependency provided by Spring")
public class PlaylistCriteriaRepositoryImpl implements PlaylistCriteriaRepository {

    private final EntityManager entityManager;

    @Override
    public Page<Playlist> search(PlaylistCriteria criteria, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Playlist> criteriaQuery = criteriaBuilder.createQuery(Playlist.class);
        Root<Playlist> root = criteriaQuery.from(Playlist.class);
        List<Predicate> predicates =
                buildPredicates(criteria, criteriaQuery, root, criteriaBuilder);

        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(
                toOrders(pageable.getSort(), criteria, criteriaQuery, criteriaBuilder, root));

        TypedQuery<Playlist> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Playlist> rows = typedQuery.getResultList();
        long totalCount = count(criteria, criteriaBuilder);
        return new PageImpl<>(rows, pageable, totalCount);
    }

    private long count(PlaylistCriteria criteria, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Playlist> countRoot = countQuery.from(Playlist.class);
        List<Predicate> predicates =
                buildPredicates(criteria, countQuery, countRoot, criteriaBuilder);
        countQuery.select(criteriaBuilder.count(countRoot));
        countQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            PlaylistCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            Root<Playlist> root,
            CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.isFalse(root.get("resource").get("isDeleted")));
        if (criteria.status() != null) {
            predicates.add(
                    criteriaBuilder.equal(root.get("resource").get("status"), criteria.status()));
        }
        if (criteria.query() != null) {
            predicates.add(
                    hasAnyResourceNameLike(
                            "%" + criteria.query().toLowerCase() + "%",
                            criteriaQuery,
                            root,
                            criteriaBuilder));
        }
        return predicates;
    }

    private Predicate hasAnyResourceNameLike(
            String keywordPattern,
            CriteriaQuery<?> criteriaQuery,
            Root<Playlist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<ResourceName> resourceName = subquery.from(ResourceName.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(resourceName.get("resource"), root.get("resource")),
                criteriaBuilder.like(
                        criteriaBuilder.lower(resourceName.get("name")), keywordPattern));
        return criteriaBuilder.exists(subquery);
    }

    private List<Order> toOrders(
            Sort sort,
            PlaylistCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder criteriaBuilder,
            Root<Playlist> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (isMatchSort(order)) {
                Expression<Integer> matchRank =
                        buildMatchRank(criteria.query(), criteriaQuery, root, criteriaBuilder);
                orders.add(criteriaBuilder.asc(matchRank));
                orders.add(criteriaBuilder.asc(root.get("resource").get("canonicalName")));
                orders.add(criteriaBuilder.desc(root.get("resource").get("viewCount")));
                orders.add(criteriaBuilder.desc(root.get("resource").get("updatedAt")));
                continue;
            }
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
            if (isNameSort(order)) {
                orders.add(criteriaBuilder.desc(root.get("resource").get("viewCount")));
            }
        }
        return orders;
    }

    private Expression<Integer> buildMatchRank(
            String query,
            CriteriaQuery<?> criteriaQuery,
            Root<Playlist> root,
            CriteriaBuilder criteriaBuilder) {
        if (query == null) {
            return criteriaBuilder.literal(0);
        }

        String normalizedQuery = query.toLowerCase();
        return criteriaBuilder
                .<Integer>selectCase()
                .when(
                        hasAnyResourceNameEqual(
                                normalizedQuery, criteriaQuery, root, criteriaBuilder),
                        0)
                .when(
                        hasAnyResourceNamePrefix(
                                normalizedQuery, criteriaQuery, root, criteriaBuilder),
                        1)
                .otherwise(2);
    }

    private Predicate hasAnyResourceNameEqual(
            String normalizedQuery,
            CriteriaQuery<?> criteriaQuery,
            Root<Playlist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<ResourceName> resourceName = subquery.from(ResourceName.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(resourceName.get("resource"), root.get("resource")),
                criteriaBuilder.equal(
                        criteriaBuilder.lower(resourceName.get("name")), normalizedQuery));
        return criteriaBuilder.exists(subquery);
    }

    private Predicate hasAnyResourceNamePrefix(
            String normalizedQuery,
            CriteriaQuery<?> criteriaQuery,
            Root<Playlist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<ResourceName> resourceName = subquery.from(ResourceName.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(resourceName.get("resource"), root.get("resource")),
                criteriaBuilder.like(
                        criteriaBuilder.lower(resourceName.get("name")), normalizedQuery + "%"));
        return criteriaBuilder.exists(subquery);
    }

    private boolean isMatchSort(Sort.Order order) {
        return "match".equals(order.getProperty());
    }

    private boolean isNameSort(Sort.Order order) {
        return "resource.canonicalName".equals(order.getProperty());
    }

    private Path<?> resolvePath(Root<Playlist> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
