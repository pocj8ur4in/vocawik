package com.vocawik.service.resource;

import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.dto.resource.PopularResourceElementResponse;
import com.vocawik.dto.resource.PopularResourceListResponse;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.security.ip.ClientIpResolver;
import com.vocawik.security.ip.IpHashService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Tracks recent resource views and exposes a rolling popularity ranking. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "StringRedisTemplate is a Spring-managed infrastructure bean and is not exposed externally.")
public class ResourcePopularityService {

    private static final int POPULAR_WINDOW_MINUTES = 10;
    private static final int MAX_POPULAR_SIZE = 10;
    private static final int POPULAR_CANDIDATE_SIZE = 100;
    private static final Duration VIEW_DEDUP_TTL = Duration.ofMinutes(POPULAR_WINDOW_MINUTES);
    private static final Duration BUCKET_TTL = Duration.ofMinutes(POPULAR_WINDOW_MINUTES + 5L);
    private static final Duration AGGREGATE_TTL = Duration.ofSeconds(5);
    private static final String BUCKET_KEY_PREFIX = "popular:resources:bucket:";
    private static final String DEDUP_KEY_PREFIX = "popular:resources:dedup:ip:";
    private static final String AGGREGATE_KEY_PREFIX = "popular:resources:aggregate:";
    private static final DateTimeFormatter BUCKET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final ResourceRepository resourceRepository;
    private final PlaylistRepository playlistRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ClientIpResolver clientIpResolver;
    private final IpHashService ipHashService;

    /**
     * Records a view when the current request is eligible for popularity tracking.
     *
     * @param resourceUuid viewed resource UUID
     */
    @Transactional
    public void trackView(UUID resourceUuid) {
        if (resourceUuid == null) {
            return;
        }

        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }

        Resource resource = resourceRepository.findByUuid(resourceUuid).orElse(null);
        if (resource == null || !isTrackable(resource)) {
            return;
        }

        String clientIp = clientIpResolver.resolve(request);
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }

        String dedupKey = dedupKey(ipHashService.hash(clientIp), resource.getUuid());
        boolean firstRecentView =
                Boolean.TRUE.equals(
                        stringRedisTemplate
                                .opsForValue()
                                .setIfAbsent(dedupKey, "1", VIEW_DEDUP_TTL));
        if (!firstRecentView) {
            return;
        }

        String bucketKey = bucketKey(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
        stringRedisTemplate
                .opsForZSet()
                .incrementScore(bucketKey, resource.getUuid().toString(), 1D);
        stringRedisTemplate.expire(bucketKey, BUCKET_TTL);
        resourceRepository.incrementViewCountById(resource.getId());
    }

    /**
     * Returns the recent popularity ranking for the last 10 minutes.
     *
     * @param size requested item count
     * @return ranked resource summaries
     */
    @Transactional(readOnly = true)
    public PopularResourceListResponse listPopularResources(int size) {
        int effectiveSize = Math.min(Math.max(size, 1), MAX_POPULAR_SIZE);
        List<String> bucketKeys = recentBucketKeys();
        String aggregateKey = AGGREGATE_KEY_PREFIX + UUID.randomUUID();

        try {
            stringRedisTemplate
                    .opsForZSet()
                    .unionAndStore(
                            bucketKeys.get(0),
                            bucketKeys.subList(1, bucketKeys.size()),
                            aggregateKey);
            stringRedisTemplate.expire(aggregateKey, AGGREGATE_TTL);

            Set<ZSetOperations.TypedTuple<String>> tuples =
                    stringRedisTemplate
                            .opsForZSet()
                            .reverseRangeWithScores(aggregateKey, 0, POPULAR_CANDIDATE_SIZE - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return new PopularResourceListResponse(List.of(), effectiveSize);
            }

            Map<UUID, Long> scoreByUuid = new LinkedHashMap<>();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple.getValue() == null || tuple.getScore() == null) {
                    continue;
                }
                scoreByUuid.put(UUID.fromString(tuple.getValue()), tuple.getScore().longValue());
            }

            if (scoreByUuid.isEmpty()) {
                return new PopularResourceListResponse(List.of(), effectiveSize);
            }

            List<Resource> resources =
                    resourceRepository.findAllByUuidInAndIsDeletedFalseAndStatus(
                            scoreByUuid.keySet(), ResourceStatus.ACTIVE);
            Set<Long> publicPlaylistIds = resolvePublicPlaylistIds(resources);

            List<PopularResourceElementResponse> items =
                    resources.stream()
                            .filter(resource -> isVisible(resource, publicPlaylistIds))
                            .sorted(
                                    Comparator.comparingLong(
                                                    (Resource resource) ->
                                                            scoreByUuid.getOrDefault(
                                                                    resource.getUuid(), 0L))
                                            .reversed()
                                            .thenComparing(
                                                    Resource::getUpdatedAt,
                                                    Comparator.reverseOrder()))
                            .limit(effectiveSize)
                            .map(
                                    resource ->
                                            new PopularResourceElementResponse(
                                                    resource.getUuid(),
                                                    resource.getResourceType().name(),
                                                    resource.getCanonicalName(),
                                                    scoreByUuid.getOrDefault(
                                                            resource.getUuid(), 0L)))
                            .toList();

            return new PopularResourceListResponse(items, effectiveSize);
        } finally {
            stringRedisTemplate.delete(aggregateKey);
        }
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        return attrs.getRequest();
    }

    private boolean isTrackable(Resource resource) {
        if (resource.isDeleted() || !ResourceStatus.ACTIVE.equals(resource.getStatus())) {
            return false;
        }
        if (!ResourceType.PLAYLIST.equals(resource.getResourceType())) {
            return true;
        }
        return playlistRepository.findById(resource.getId()).map(Playlist::isPublic).orElse(false);
    }

    private boolean isVisible(Resource resource, Set<Long> publicPlaylistIds) {
        if (!ResourceType.PLAYLIST.equals(resource.getResourceType())) {
            return true;
        }
        return publicPlaylistIds.contains(resource.getId());
    }

    private Set<Long> resolvePublicPlaylistIds(List<Resource> resources) {
        List<Long> playlistIds =
                resources.stream()
                        .filter(
                                resource ->
                                        ResourceType.PLAYLIST.equals(resource.getResourceType()))
                        .map(Resource::getId)
                        .toList();
        if (playlistIds.isEmpty()) {
            return Set.of();
        }
        return playlistRepository.findAllByIdInAndIsPublicTrue(playlistIds).stream()
                .map(Playlist::getId)
                .collect(Collectors.toSet());
    }

    private List<String> recentBucketKeys() {
        LocalDateTime currentMinute = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<String> keys = new ArrayList<>(POPULAR_WINDOW_MINUTES);
        for (int minuteOffset = 0; minuteOffset < POPULAR_WINDOW_MINUTES; minuteOffset++) {
            keys.add(bucketKey(currentMinute.minusMinutes(minuteOffset)));
        }
        return keys;
    }

    private String bucketKey(LocalDateTime bucketTime) {
        return BUCKET_KEY_PREFIX + bucketTime.format(BUCKET_FORMATTER);
    }

    private String dedupKey(String ipHash, UUID resourceUuid) {
        return DEDUP_KEY_PREFIX + ipHash + ":resource:" + resourceUuid;
    }
}
