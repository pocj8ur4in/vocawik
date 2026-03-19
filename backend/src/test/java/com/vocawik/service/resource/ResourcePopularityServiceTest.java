package com.vocawik.service.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.security.ip.ClientIpResolver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class ResourcePopularityServiceTest {

    private ResourceRepository resourceRepository;
    private ResourceNameRepository resourceNameRepository;
    private PlaylistRepository playlistRepository;
    private StringRedisTemplate stringRedisTemplate;
    private ZSetOperations<String, String> zSetOperations;
    private ResourcePopularityService resourcePopularityService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceRepository = mock(ResourceRepository.class);
        resourceNameRepository = mock(ResourceNameRepository.class);
        playlistRepository = mock(PlaylistRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = mock(ZSetOperations.class);
        when(stringRedisTemplate.opsForZSet()).thenReturn(zSetOperations);

        resourcePopularityService =
                new ResourcePopularityService(
                        resourceRepository,
                        resourceNameRepository,
                        playlistRepository,
                        stringRedisTemplate,
                        mock(ClientIpResolver.class));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Popular resources should include localized name matching request locale")
    void listPopularResources_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        UUID resourceUuid = UUID.randomUUID();
        Resource resource = resource(1L, resourceUuid, "Mesmerizer");
        ResourceName koreanName = localizedName(1L, "메스머라이저", Language.KO);
        ZSetOperations.TypedTuple<String> tuple = tuple(resourceUuid.toString(), 12D);
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                .thenReturn(Set.of(tuple));
        when(resourceRepository.findAllByUuidInAndIsDeletedFalseAndStatus(
                        anyCollection(), eq(ResourceStatus.ACTIVE)))
                .thenReturn(List.of(resource));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(koreanName));

        var result = resourcePopularityService.listPopularResources(10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Mesmerizer");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("메스머라이저");
        assertThat(result.items().getFirst().recentViewCount()).isEqualTo(12L);
    }

    @Test
    @DisplayName("Popular resources should skip localized lookup for unsupported locale")
    void listPopularResources_withUnsupportedLocale_shouldSkipLocalizedLookup() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        UUID resourceUuid = UUID.randomUUID();
        Resource resource = resource(1L, resourceUuid, "Mesmerizer");
        ZSetOperations.TypedTuple<String> tuple = tuple(resourceUuid.toString(), 7D);
        when(zSetOperations.reverseRangeWithScores(anyString(), eq(0L), eq(99L)))
                .thenReturn(Set.of(tuple));
        when(resourceRepository.findAllByUuidInAndIsDeletedFalseAndStatus(
                        anyCollection(), eq(ResourceStatus.ACTIVE)))
                .thenReturn(List.of(resource));

        var result = resourcePopularityService.listPopularResources(10);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
        verifyNoInteractions(resourceNameRepository);
    }

    private Resource resource(Long id, UUID uuid, String canonicalName) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(id);
        when(resource.getUuid()).thenReturn(uuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getResourceType()).thenReturn(ResourceType.SONG);
        when(resource.getUpdatedAt()).thenReturn(LocalDateTime.parse("2026-03-19T12:45:43"));
        return resource;
    }

    private ResourceName localizedName(Long resourceId, String name, Language language) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);

        ResourceName resourceName = mock(ResourceName.class);
        when(resourceName.getResource()).thenReturn(resource);
        when(resourceName.getName()).thenReturn(name);
        when(resourceName.getLangCode()).thenReturn(language);
        return resourceName;
    }

    @SuppressWarnings("unchecked")
    private ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
