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
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
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
        criteriaQuery.orderBy(
                toOrders(pageable.getSort(), criteria, criteriaQuery, criteriaBuilder, root));

        TypedQuery<Song> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());

        List<Song> rows = typedQuery.getResultList();
        long totalCount = count(criteria, criteriaBuilder);
        return new PageImpl<>(rows, pageable, totalCount);
    }

    @Override
    public List<Song> searchAll(SongCriteria criteria, Sort sort) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Song> criteriaQuery = criteriaBuilder.createQuery(Song.class);
        Root<Song> root = criteriaQuery.from(Song.class);
        List<Predicate> predicates =
                buildPredicates(criteria, criteriaQuery, root, criteriaBuilder);
        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(toOrders(sort, criteria, criteriaQuery, criteriaBuilder, root));

        return entityManager.createQuery(criteriaQuery).getResultList();
    }

    @Override
    public List<Song> searchPlaybackSlice(
            SongCriteria criteria,
            SongPlaybackCursorCriteria cursor,
            Sort.Order sortOrder,
            int limit) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Song> criteriaQuery = criteriaBuilder.createQuery(Song.class);
        Root<Song> root = criteriaQuery.from(Song.class);
        List<Predicate> predicates =
                buildPredicates(criteria, criteriaQuery, root, criteriaBuilder);
        Predicate cursorPredicate =
                buildPlaybackCursorPredicate(
                        criteria, criteriaQuery, cursor, sortOrder, criteriaBuilder, root);
        if (cursorPredicate != null) {
            predicates.add(cursorPredicate);
        }
        criteriaQuery.where(predicates.toArray(Predicate[]::new));
        criteriaQuery.orderBy(
                toPlaybackOrders(criteria, criteriaQuery, sortOrder, criteriaBuilder, root));

        TypedQuery<Song> typedQuery = entityManager.createQuery(criteriaQuery);
        typedQuery.setMaxResults(limit);
        return typedQuery.getResultList();
    }

    @Override
    public long count(SongCriteria criteria) {
        return count(criteria, entityManager.getCriteriaBuilder());
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
        if (!criteria.includeDeleted()) {
            predicates.add(criteriaBuilder.isFalse(root.get("resource").get("isDeleted")));
        }
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

    private Predicate buildPlaybackCursorPredicate(
            SongCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            SongPlaybackCursorCriteria cursor,
            Sort.Order sortOrder,
            CriteriaBuilder criteriaBuilder,
            Root<Song> root) {
        if (cursor == null || sortOrder == null) {
            return null;
        }

        Path<Long> idPath = root.get("id");
        return switch (cursor.sortProperty()) {
            case "resource.updatedAt", "resource.createdAt" -> {
                Expression<LocalDateTime> path =
                        root.get("resource")
                                .get(
                                        cursor.sortProperty().endsWith("updatedAt")
                                                ? "updatedAt"
                                                : "createdAt");
                yield buildComparableCursorPredicate(
                        path,
                        cursor.dateTimeValue(),
                        idPath,
                        sortOrder.isAscending(),
                        criteriaBuilder,
                        cursor.songId());
            }
            case "publishedAt" -> {
                Expression<LocalDateTime> path =
                        normalizedPublishedAtExpression(
                                root, criteriaBuilder, sortOrder.isAscending());
                LocalDateTime cursorValue =
                        normalizePublishedAtValue(cursor.dateTimeValue(), sortOrder.isAscending());
                yield buildComparableCursorPredicate(
                        path,
                        cursorValue,
                        idPath,
                        sortOrder.isAscending(),
                        criteriaBuilder,
                        cursor.songId());
            }
            case "resource.viewCount" -> {
                Expression<Long> path = root.get("resource").get("viewCount");
                yield buildComparableCursorPredicate(
                        path,
                        cursor.longValue(),
                        idPath,
                        sortOrder.isAscending(),
                        criteriaBuilder,
                        cursor.songId());
            }
            case "resource.canonicalName" -> {
                Expression<String> path = root.get("resource").get("canonicalName");
                yield buildComparableCursorPredicate(
                        path,
                        cursor.stringValue(),
                        idPath,
                        sortOrder.isAscending(),
                        criteriaBuilder,
                        cursor.songId());
            }
            case "match" ->
                    buildMatchPlaybackCursorPredicate(
                            criteria, criteriaQuery, cursor, criteriaBuilder, root, idPath);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported playback cursor sort property: " + cursor.sortProperty());
        };
    }

    private <T extends Comparable<? super T>> Predicate buildComparableCursorPredicate(
            Expression<T> sortPath,
            T cursorValue,
            Path<Long> idPath,
            boolean ascending,
            CriteriaBuilder criteriaBuilder,
            Long cursorSongId) {
        if (cursorValue == null || cursorSongId == null) {
            return null;
        }

        Predicate valuePredicate =
                ascending
                        ? criteriaBuilder.greaterThan(sortPath, cursorValue)
                        : criteriaBuilder.lessThan(sortPath, cursorValue);
        Predicate tieBreakPredicate =
                criteriaBuilder.and(
                        criteriaBuilder.equal(sortPath, cursorValue),
                        ascending
                                ? criteriaBuilder.greaterThan(idPath, cursorSongId)
                                : criteriaBuilder.lessThan(idPath, cursorSongId));
        return criteriaBuilder.or(valuePredicate, tieBreakPredicate);
    }

    private List<Order> toPlaybackOrders(
            SongCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            Sort.Order sortOrder,
            CriteriaBuilder criteriaBuilder,
            Root<Song> root) {
        if ("match".equals(sortOrder.getProperty())) {
            Expression<Integer> matchRank =
                    buildMatchRank(criteria.query(), criteriaQuery, root, criteriaBuilder);
            ArrayList<Order> orders = new ArrayList<>(6);
            orders.add(criteriaBuilder.asc(matchRank));
            orders.add(criteriaBuilder.asc(root.get("resource").get("canonicalName")));
            orders.add(criteriaBuilder.asc(buildOriginalFirstExpression(criteriaBuilder, root)));
            orders.add(criteriaBuilder.desc(root.get("resource").get("viewCount")));
            orders.add(criteriaBuilder.desc(root.get("resource").get("updatedAt")));
            orders.add(criteriaBuilder.asc(root.get("id")));
            return orders;
        }

        Expression<?> sortExpression =
                switch (sortOrder.getProperty()) {
                    case "resource.updatedAt" -> root.get("resource").get("updatedAt");
                    case "resource.createdAt" -> root.get("resource").get("createdAt");
                    case "publishedAt" ->
                            normalizedPublishedAtExpression(
                                    root, criteriaBuilder, sortOrder.isAscending());
                    case "resource.viewCount" -> root.get("resource").get("viewCount");
                    case "resource.canonicalName" -> root.get("resource").get("canonicalName");
                    default ->
                            throw new IllegalArgumentException(
                                    "Unsupported playback sort property: "
                                            + sortOrder.getProperty());
                };

        ArrayList<Order> orders = new ArrayList<>(2);
        orders.add(
                sortOrder.isAscending()
                        ? criteriaBuilder.asc(sortExpression)
                        : criteriaBuilder.desc(sortExpression));
        orders.add(
                sortOrder.isAscending()
                        ? criteriaBuilder.asc(root.get("id"))
                        : criteriaBuilder.desc(root.get("id")));
        return orders;
    }

    private Predicate buildMatchPlaybackCursorPredicate(
            SongCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            SongPlaybackCursorCriteria cursor,
            CriteriaBuilder criteriaBuilder,
            Root<Song> root,
            Path<Long> idPath) {
        if (cursor.intValue() == null
                || cursor.stringValue() == null
                || cursor.secondaryIntValue() == null
                || cursor.longValue() == null
                || cursor.dateTimeValue() == null
                || cursor.songId() == null) {
            return null;
        }

        Expression<Integer> matchRank =
                buildMatchRank(criteria.query(), criteriaQuery, root, criteriaBuilder);
        Expression<String> canonicalName = root.get("resource").get("canonicalName");
        Expression<Integer> originalRank = buildOriginalFirstExpression(criteriaBuilder, root);
        Expression<Long> viewCount = root.get("resource").get("viewCount");
        Expression<LocalDateTime> updatedAt = root.get("resource").get("updatedAt");

        Predicate rankAfter = criteriaBuilder.greaterThan(matchRank, cursor.intValue());
        Predicate nameAfter =
                criteriaBuilder.and(
                        criteriaBuilder.equal(matchRank, cursor.intValue()),
                        criteriaBuilder.greaterThan(canonicalName, cursor.stringValue()));
        Predicate originalAfter =
                criteriaBuilder.and(
                        criteriaBuilder.equal(matchRank, cursor.intValue()),
                        criteriaBuilder.equal(canonicalName, cursor.stringValue()),
                        criteriaBuilder.greaterThan(originalRank, cursor.secondaryIntValue()));
        Predicate viewCountAfter =
                criteriaBuilder.and(
                        criteriaBuilder.equal(matchRank, cursor.intValue()),
                        criteriaBuilder.equal(canonicalName, cursor.stringValue()),
                        criteriaBuilder.equal(originalRank, cursor.secondaryIntValue()),
                        criteriaBuilder.lessThan(viewCount, cursor.longValue()));
        Predicate updatedAtAfter =
                criteriaBuilder.and(
                        criteriaBuilder.equal(matchRank, cursor.intValue()),
                        criteriaBuilder.equal(canonicalName, cursor.stringValue()),
                        criteriaBuilder.equal(originalRank, cursor.secondaryIntValue()),
                        criteriaBuilder.equal(viewCount, cursor.longValue()),
                        criteriaBuilder.lessThan(updatedAt, cursor.dateTimeValue()));
        Predicate idAfter =
                criteriaBuilder.and(
                        criteriaBuilder.equal(matchRank, cursor.intValue()),
                        criteriaBuilder.equal(canonicalName, cursor.stringValue()),
                        criteriaBuilder.equal(originalRank, cursor.secondaryIntValue()),
                        criteriaBuilder.equal(viewCount, cursor.longValue()),
                        criteriaBuilder.equal(updatedAt, cursor.dateTimeValue()),
                        criteriaBuilder.greaterThan(idPath, cursor.songId()));
        return criteriaBuilder.or(
                rankAfter, nameAfter, originalAfter, viewCountAfter, updatedAtAfter, idAfter);
    }

    private Expression<LocalDateTime> normalizedPublishedAtExpression(
            Root<Song> root, CriteriaBuilder criteriaBuilder, boolean ascending) {
        LocalDateTime sentinel =
                ascending
                        ? LocalDateTime.of(9999, 12, 31, 23, 59, 59)
                        : LocalDateTime.MIN.plusYears(1000);
        return criteriaBuilder.coalesce(root.get("publishedAt"), criteriaBuilder.literal(sentinel));
    }

    private LocalDateTime normalizePublishedAtValue(LocalDateTime value, boolean ascending) {
        if (value != null) {
            return value;
        }
        return ascending
                ? LocalDateTime.of(9999, 12, 31, 23, 59, 59)
                : LocalDateTime.MIN.plusYears(1000);
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

    private List<Order> toOrders(
            Sort sort,
            SongCriteria criteria,
            CriteriaQuery<?> criteriaQuery,
            CriteriaBuilder criteriaBuilder,
            Root<Song> root) {
        List<Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            if (isMatchSort(order)) {
                Expression<Integer> matchRank =
                        buildMatchRank(criteria.query(), criteriaQuery, root, criteriaBuilder);
                orders.add(criteriaBuilder.asc(matchRank));
                orders.add(criteriaBuilder.asc(root.get("resource").get("canonicalName")));
                orders.add(buildOriginalFirstOrder(criteriaBuilder, root));
                orders.add(buildViewCountOrder(criteriaBuilder, root));
                orders.add(criteriaBuilder.desc(root.get("resource").get("updatedAt")));
                continue;
            }
            Path<?> path = resolvePath(root, order.getProperty());
            orders.add(
                    order.isAscending() ? criteriaBuilder.asc(path) : criteriaBuilder.desc(path));
            if (isNameSort(order)) {
                orders.add(buildOriginalFirstOrder(criteriaBuilder, root));
                orders.add(buildViewCountOrder(criteriaBuilder, root));
            }
        }
        return orders;
    }

    private Expression<Integer> buildMatchRank(
            String query,
            CriteriaQuery<?> criteriaQuery,
            Root<Song> root,
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
            Root<Song> root,
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
            Root<Song> root,
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

    private Order buildOriginalFirstOrder(CriteriaBuilder criteriaBuilder, Root<Song> root) {
        return criteriaBuilder.asc(buildOriginalFirstExpression(criteriaBuilder, root));
    }

    private Order buildViewCountOrder(CriteriaBuilder criteriaBuilder, Root<Song> root) {
        return criteriaBuilder.desc(root.get("resource").get("viewCount"));
    }

    private Expression<Integer> buildOriginalFirstExpression(
            CriteriaBuilder criteriaBuilder, Root<Song> root) {
        return criteriaBuilder
                .<Integer>selectCase()
                .when(criteriaBuilder.equal(root.get("songType"), SongType.ORIGINAL), 0)
                .otherwise(1);
    }

    private boolean isNameSort(Sort.Order order) {
        return "resource.canonicalName".equals(order.getProperty());
    }

    private boolean isMatchSort(Sort.Order order) {
        return "match".equals(order.getProperty());
    }

    private Path<?> resolvePath(Root<Song> root, String propertyPath) {
        Path<?> current = root;
        for (String segment : propertyPath.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }
}
