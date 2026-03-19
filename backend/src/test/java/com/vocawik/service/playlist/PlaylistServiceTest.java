package com.vocawik.service.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.dto.playlist.PlaylistSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.service.history.ResourceHistoryService;
import jakarta.persistence.EntityManager;
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

class PlaylistServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private PlaylistRepository playlistRepository;
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        playlistRepository = mock(PlaylistRepository.class);
        playlistService =
                new PlaylistService(
                        playlistRepository,
                        mock(PlaylistSongRepository.class),
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(SongRepository.class),
                        mock(ResourceHistoryService.class),
                        mock(EntityManager.class),
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Search should include localized name matching request locale")
    void search_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        Playlist playlist = playlist(1L, UUID.randomUUID(), "Miku Favorites");
        ResourceName japaneseName = localizedName(1L, "ミクお気に入り", Language.JA);
        when(playlistRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(playlist), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        PlaylistListResponse result = playlistService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Miku Favorites");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("ミクお気に入り");
    }

    @Test
    @DisplayName("Search should return null localized name when request locale name is missing")
    void search_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Playlist playlist = playlist(1L, UUID.randomUUID(), "Miku Favorites");
        ResourceName japaneseName = localizedName(1L, "ミクお気に入り", Language.JA);
        when(playlistRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(playlist), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        PlaylistListResponse result = playlistService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Search should not query resource names when result is empty")
    void search_withEmptyResult_shouldSkipLocalizedNameLookup() {
        when(playlistRepository.search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PlaylistListResponse result = playlistService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(playlistRepository)
                .search(
                        argThat(criteria -> criteria.status() == null && criteria.query() == null),
                        eq(PageRequest.of(0, 20)));
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Suggest should return up to 10 distinct playlists")
    void suggest_shouldReturnUpToTenDistinctPlaylists() {
        List<ResourceName> candidates = new java.util.ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        candidates.add(candidate(duplicatedUuid, "Miku Favorites"));
        candidates.add(candidate(duplicatedUuid, "Miku Best"));
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate(UUID.randomUUID(), "Candidate " + i));
        }
        when(resourceNameRepository.findPlaylistSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mik"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(candidates);

        PlaylistSuggestionListResponse result = playlistService.suggest(" mik ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().resourceUuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Miku Favorites");
        assertThat(result.items()).extracting(item -> item.resourceUuid()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Suggest should return empty list when query is blank")
    void suggest_withBlankQuery_shouldReturnEmptyList() {
        PlaylistSuggestionListResponse result = playlistService.suggest("   ");

        assertThat(result.items()).isEmpty();
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

    private Playlist playlist(Long resourceId, UUID resourceUuid, String canonicalName) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(resourceUuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getStatus()).thenReturn(ResourceStatus.ACTIVE);
        when(resource.getViewCount()).thenReturn(0L);

        Playlist playlist = mock(Playlist.class);
        when(playlist.getResource()).thenReturn(resource);
        return playlist;
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
