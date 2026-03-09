package com.vocawik.repository.vocal;

import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.song.SongVoicebank;
import com.vocawik.domain.vocal.VocalVoicebank;
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

/** Criteria API implementation for voicebank search. */
@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EntityManager is a container-managed dependency provided by Spring")
public class VoicebankCriteriaRepositoryImpl implements VoicebankCriteriaRepository {

    private final EntityManager entityManager;

    @Override
    public Slice<VocalVoicebank> search(VoicebankCriteria criteria, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<VocalVoicebank> criteriaQuery =
                criteriaBuilder.createQuery(VocalVoicebank.class);
        Root<VocalVoicebank> root = criteriaQuery.from(VocalVoicebank.class);
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
        if (!criteria.voicebankTypes().isEmpty()) {
            predicates.add(root.get("voicebankType").in(criteria.voicebankTypes()));
        }
        if (!criteria.songUuids().isEmpty()) {
            predicates.add(
                    hasAnySongUuid(criteria.songUuids(), criteriaQuery, root, criteriaBuilder));
        }
        if (!criteria.vocalUuids().isEmpty()) {
            predicates.add(
                    hasAnyVocalUuid(criteria.vocalUuids(), criteriaQuery, root, criteriaBuilder));
        }

        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(toOrders(pageable.getSort(), criteriaBuilder, root));

        TypedQuery<VocalVoicebank> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize() + 1);

        List<VocalVoicebank> rows = typedQuery.getResultList();
        boolean hasNext = rows.size() > pageable.getPageSize();
        if (hasNext) {
            rows = rows.subList(0, pageable.getPageSize());
        }
        return new SliceImpl<>(rows, pageable, hasNext);
    }

    private Predicate hasAnyResourceNameLike(
            String keywordPattern,
            CriteriaQuery<VocalVoicebank> criteriaQuery,
            Root<VocalVoicebank> root,
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
            CriteriaQuery<VocalVoicebank> criteriaQuery,
            Root<VocalVoicebank> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<SongVoicebank> songVoicebank = subquery.from(SongVoicebank.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(songVoicebank.get("voicebank"), root),
                songVoicebank.get("song").get("resource").get("uuid").in(songUuids));
        return criteriaBuilder.exists(subquery);
    }

    private Predicate hasAnyVocalUuid(
            List<UUID> vocalUuids,
            CriteriaQuery<VocalVoicebank> criteriaQuery,
            Root<VocalVoicebank> root,
            CriteriaBuilder criteriaBuilder) {
        return root.get("vocalCharacter").get("resource").get("uuid").in(vocalUuids);
    }

    private List<Order> toOrders(
            Sort sort, CriteriaBuilder criteriaBuilder, Root<VocalVoicebank> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
        }
        return orders;
    }

    private Path<?> resolvePath(Root<VocalVoicebank> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
