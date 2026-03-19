package com.vocawik.service.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.dto.resource.ResourceListResponse;
import com.vocawik.dto.resource.ResourceSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistLinkRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongLinkRepository;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalLinkRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ResourceServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private ResourceRepository resourceRepository;
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        resourceRepository = mock(ResourceRepository.class);
        resourceService =
                new ResourceService(
                        resourceRepository,
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(SongRepository.class),
                        mock(SongLinkRepository.class),
                        mock(SongLyricRepository.class),
                        mock(SongPvRepository.class),
                        mock(SongPvViewRepository.class),
                        mock(SongArtistRepository.class),
                        mock(SongVocalRepository.class),
                        mock(SongRelationRepository.class),
                        mock(PlaylistSongRepository.class),
                        mock(PlaylistRepository.class),
                        mock(ArtistRepository.class),
                        mock(ArtistGroupRepository.class),
                        mock(ArtistLinkRepository.class),
                        mock(VocalRepository.class),
                        mock(VocalLinkRepository.class),
                        mock(ResourceHistoryService.class),
                        mock(ResourcePopularityService.class),
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Suggest should return up to 10 distinct resources")
    void suggest_shouldReturnUpToTenDistinctResources() {
        List<ResourceName> candidates = new ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        candidates.add(candidate(duplicatedUuid, "Miku"));
        candidates.add(candidate(duplicatedUuid, "Hatsune Miku"));
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate(UUID.randomUUID(), "Candidate " + i));
        }
        when(resourceNameRepository.findSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mik"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(candidates);

        ResourceSuggestionListResponse result = resourceService.suggest(" mik ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().uuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Miku");
        assertThat(result.items()).extracting(item -> item.uuid()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Suggest should return empty list when query is blank")
    void suggest_withBlankQuery_shouldReturnEmptyList() {
        ResourceSuggestionListResponse result = resourceService.suggest("   ");

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Search should include localized name matching request locale")
    void search_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        Resource resource = resource(1L, UUID.randomUUID(), "Hatsune Miku", ResourceType.VOCAL);
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(resourceRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(resource), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        ResourceListResponse result = resourceService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Hatsune Miku");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("初音ミク");
    }

    @Test
    @DisplayName("Search should return null localized name when request locale name is missing")
    void search_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Resource resource = resource(1L, UUID.randomUUID(), "Hatsune Miku", ResourceType.VOCAL);
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(resourceRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(resource), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        ResourceListResponse result = resourceService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Search should not query resource names when result is empty")
    void search_withEmptyResult_shouldSkipLocalizedNameLookup() {
        when(resourceRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ResourceListResponse result = resourceService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(resourceRepository)
                .search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20)));
        verifyNoInteractions(resourceNameRepository);
    }

    private ResourceName candidate(UUID uuid, String name) {
        Resource resource = mock(Resource.class);
        when(resource.getUuid()).thenReturn(uuid);

        ResourceName resourceName = mock(ResourceName.class);
        when(resourceName.getResource()).thenReturn(resource);
        when(resourceName.getName()).thenReturn(name);
        return resourceName;
    }

    private Resource resource(Long resourceId, UUID uuid, String canonicalName, ResourceType type) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(uuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getResourceType()).thenReturn(type);
        when(resource.getStatus()).thenReturn(ResourceStatus.ACTIVE);
        when(resource.getViewCount()).thenReturn(0L);
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
}
