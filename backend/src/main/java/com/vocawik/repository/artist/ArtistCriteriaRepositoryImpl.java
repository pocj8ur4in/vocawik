package com.vocawik.repository.artist;

import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.song.SongArtist;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/** Criteria API implementation for artist search. */
@Repository
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "EntityManager is a container-managed dependency provided by Spring")
public class ArtistCriteriaRepositoryImpl implements ArtistCriteriaRepository {

    private final EntityManager entityManager;

    @Override
    public Page<Artist> search(ArtistCriteria criteria, Pageable pageable) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Artist> criteriaQuery = criteriaBuilder.createQuery(Artist.class);
        Root<Artist> root = criteriaQuery.from(Artist.class);
        root.fetch("resource", JoinType.INNER);
        List<Predicate> predicates =
                buildPredicates(criteria, criteriaQuery, root, criteriaBuilder);

        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(toOrders(pageable.getSort(), criteriaBuilder, root));

        TypedQuery<Artist> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Artist> rows = typedQuery.getResultList();
        long totalCount = count(criteria, criteriaBuilder);
        return new PageImpl<>(rows, pageable, totalCount);
    }

    private long count(ArtistCriteria criteria, CriteriaBuilder criteriaBuilder) {
        CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
        Root<Artist> countRoot = countQuery.from(Artist.class);
        List<Predicate> predicates =
                buildPredicates(criteria, countQuery, countRoot, criteriaBuilder);
        countQuery.select(criteriaBuilder.count(countRoot));
        countQuery.where(predicates.toArray(Predicate[]::new));
        return entityManager.createQuery(countQuery).getSingleResult();
    }

    private List<Predicate> buildPredicates(
            ArtistCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            Root<Artist> root,
            CriteriaBuilder criteriaBuilder) {
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
        if (!criteria.groupArtistUuids().isEmpty()) {
            predicates.add(
                    hasAnyGroupArtistUuid(
                            criteria.groupArtistUuids(), criteriaQuery, root, criteriaBuilder));
        }
        if (!criteria.memberArtistUuids().isEmpty()) {
            predicates.add(
                    hasAnyMemberArtistUuid(
                            criteria.memberArtistUuids(), criteriaQuery, root, criteriaBuilder));
        }
        return predicates;
    }

    private Predicate hasAnyResourceNameLike(
            String keywordPattern,
            CriteriaQuery<?> criteriaQuery,
            Root<Artist> root,
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
            CriteriaQuery<?> criteriaQuery,
            Root<Artist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<SongArtist> songArtist = subquery.from(SongArtist.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(songArtist.get("artist"), root),
                songArtist.get("song").get("resource").get("uuid").in(songUuids));
        return criteriaBuilder.exists(subquery);
    }

    private Predicate hasAnyGroupArtistUuid(
            List<UUID> groupArtistUuids,
            CriteriaQuery<?> criteriaQuery,
            Root<Artist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<ArtistGroup> artistGroup = subquery.from(ArtistGroup.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(artistGroup.get("memberArtist"), root),
                artistGroup.get("groupArtist").get("resource").get("uuid").in(groupArtistUuids));
        return criteriaBuilder.exists(subquery);
    }

    private Predicate hasAnyMemberArtistUuid(
            List<UUID> memberArtistUuids,
            CriteriaQuery<?> criteriaQuery,
            Root<Artist> root,
            CriteriaBuilder criteriaBuilder) {
        Subquery<Long> subquery = criteriaQuery.subquery(Long.class);
        Root<ArtistGroup> artistGroup = subquery.from(ArtistGroup.class);
        subquery.select(criteriaBuilder.literal(1L));
        subquery.where(
                criteriaBuilder.equal(artistGroup.get("groupArtist"), root),
                artistGroup.get("memberArtist").get("resource").get("uuid").in(memberArtistUuids));
        return criteriaBuilder.exists(subquery);
    }

    private List<Order> toOrders(Sort sort, CriteriaBuilder criteriaBuilder, Root<Artist> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
        }
        return orders;
    }

    private Path<?> resolvePath(Root<Artist> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
