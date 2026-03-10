package com.vocawik.repository.vocal;

import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.song.SongVocal;
import com.vocawik.domain.vocal.Vocal;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Criteria API implementation for vocal search. */
@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EntityManager is a container-managed dependency provided by Spring")
public class VocalCriteriaRepositoryImpl implements VocalCriteriaRepository {

    private final EntityManager entityManager;

    @Override
    public Slice<Vocal> search(VocalCriteria criteria, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Vocal> criteriaQuery = criteriaBuilder.createQuery(Vocal.class);
        Root<Vocal> root = criteriaQuery.from(Vocal.class);
        root.fetch("resource", JoinType.INNER);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.isFalse(root.get("resource").get("isDeleted")));
        if (criteria.status() != null) {
            predicates.add(
                    criteriaBuilder.equal(root.get("resource").get("status"), criteria.status()));
        }
        if (criteria.query() != null) {
            String keywordPattern = "%" + criteria.query().toLowerCase() + "%";
            predicates.add(
                    hasAnyResourceNameLike(keywordPattern, criteriaQuery, root, criteriaBuilder));
        }
        if (!criteria.songUuids().isEmpty()) {
            predicates.add(
                    hasAnySongUuid(criteria.songUuids(), criteriaQuery, root, criteriaBuilder));
        }
        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(toOrders(pageable.getSort(), criteriaBuilder, root));

        TypedQuery<Vocal> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize() + 1);

        List<Vocal> rows = typedQuery.getResultList();
        boolean hasNext = rows.size() > pageable.getPageSize();
        if (hasNext) {
            rows = rows.subList(0, pageable.getPageSize());
        }
        return new SliceImpl<>(rows, pageable, hasNext);
    }

    private Predicate hasAnyResourceNameLike(
            String keywordPattern,
            CriteriaQuery<Vocal> criteriaQuery,
            Root<Vocal> root,
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

    private Predicate hasAnySongUuid(
            List<UUID> songUuids,
            CriteriaQuery<Vocal> criteriaQuery,
            Root<Vocal> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<SongVocal> songVocal = subquery.from(SongVocal.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(songVocal.get("vocal"), root),
                songVocal.get("song").get("resource").get("uuid").in(songUuids));
        return criteriaBuilder.exists(subquery);
    }

    private List<Order> toOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<Vocal> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
        }
        return orders;
    }

    private Path<?> resolvePath(Root<Vocal> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
