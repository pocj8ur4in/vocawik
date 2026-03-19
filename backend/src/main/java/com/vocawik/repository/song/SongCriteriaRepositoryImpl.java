package com.vocawik.repository.song;

import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongArtist;
import com.vocawik.domain.song.SongType;
import com.vocawik.domain.song.SongVocal;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Criteria API implementation for song search. */
@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EntityManager is a container-managed dependency provided by Spring")
public class SongCriteriaRepositoryImpl implements SongCriteriaRepository {

    private final EntityManager entityManager;

    @Override
    public Page<Song> search(SongCriteria criteria, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Song> criteriaQuery = criteriaBuilder.createQuery(Song.class);
        Root<Song> root = criteriaQuery.from(Song.class);
        List<Predicate> predicates =
                buildPredicates(criteria, criteriaQuery, root, criteriaBuilder);
        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(toOrders(pageable.getSort(), criteriaBuilder, root));

        TypedQuery<Song> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Song> rows = typedQuery.getResultList();
        long totalCount = count(criteria, criteriaBuilder);
        return new PageImpl<>(rows, pageable, totalCount);
    }

    private long count(SongCriteria criteria, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Song> countRoot = countQuery.from(Song.class);
        List<Predicate> predicates =
                buildPredicates(criteria, countQuery, countRoot, criteriaBuilder);
        countQuery.select(criteriaBuilder.count(countRoot));
        countQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            SongCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            Root<Song> root,
            CriteriaBuilder criteriaBuilder) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(criteriaBuilder.isFalse(root.get("resource").get("isDeleted")));
        if (criteria.status() != null) {
            predicates.add(
                    criteriaBuilder.equal(root.get("resource").get("status"), criteria.status()));
        }
        if (criteria.songTypes() != null && !criteria.songTypes().isEmpty()) {
            predicates.add(root.get("songType").in(criteria.songTypes()));
        }
        if (criteria.query() != null) {
            String keywordPattern = "%" + criteria.query().toLowerCase() + "%";
            predicates.add(
                    hasAnyResourceNameLike(keywordPattern, criteriaQuery, root, criteriaBuilder));
        }
        if (criteria.publishedFrom() != null) {
            predicates.add(
                    criteriaBuilder.greaterThanOrEqualTo(
                            root.get("publishedAt"), criteria.publishedFrom()));
        }
        if (criteria.publishedTo() != null) {
            predicates.add(
                    criteriaBuilder.lessThanOrEqualTo(
                            root.get("publishedAt"), criteria.publishedTo()));
        }
        if (criteria.artistUuids() != null && !criteria.artistUuids().isEmpty()) {
            predicates.add(
                    hasAnyArtistUuid(criteria.artistUuids(), criteriaQuery, root, criteriaBuilder));
        }
        if (criteria.vocalUuids() != null && !criteria.vocalUuids().isEmpty()) {
            predicates.add(
                    hasAnyVocalUuid(criteria.vocalUuids(), criteriaQuery, root, criteriaBuilder));
        }
        return predicates;
    }

    private Predicate hasAnyResourceNameLike(
            String keywordPattern,
            CriteriaQuery<?> criteriaQuery,
            Root<Song> root,
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

    private Predicate hasAnyArtistUuid(
            List<UUID> artistUuids,
            CriteriaQuery<?> criteriaQuery,
            Root<Song> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<SongArtist> songArtist = subquery.from(SongArtist.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(songArtist.get("song"), root),
                songArtist.get("artist").get("resource").get("uuid").in(artistUuids));
        return criteriaBuilder.exists(subquery);
    }

    private Predicate hasAnyVocalUuid(
            List<UUID> vocalUuids,
            CriteriaQuery<?> criteriaQuery,
            Root<Song> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<SongVocal> songVocal = subquery.from(SongVocal.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(songVocal.get("song"), root),
                songVocal.get("vocal").get("resource").get("uuid").in(vocalUuids));
        return criteriaBuilder.exists(subquery);
    }

    private List<Order> toOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<Song> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
            if (isNameSort(order)) {
                orders.add(
                        criteriaBuilder.asc(
                                criteriaBuilder
                                        .selectCase()
                                        .when(
                                                criteriaBuilder.equal(
                                                        root.get("songType"), SongType.ORIGINAL),
                                                0)
                                        .otherwise(1)));
            }
        }
        return orders;
    }

    private boolean isNameSort(Sort.Order order) {
        return "resource.canonicalName".equals(order.getProperty());
    }

    private Path<?> resolvePath(Root<Song> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
