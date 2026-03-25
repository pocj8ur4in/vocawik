package com.vocawik.service.playlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.playlist.PlaylistSong;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.playlist.PlaylistListResponse;
import com.vocawik.dto.playlist.PlaylistPlaybackResponse;
import com.vocawik.dto.playlist.PlaylistSuggestionListResponse;
import com.vocawik.dto.playlist.PlaylistUpdateRequest;
import com.vocawik.dto.song.SongPlaybackElementResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.service.acl.AclPermissionService;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.song.SongService;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
    private PlaylistSongRepository playlistSongRepository;
    private SongService songService;
    private PlaylistService playlistService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        playlistRepository = mock(PlaylistRepository.class);
        playlistSongRepository = mock(PlaylistSongRepository.class);
        songService = mock(SongService.class);
        playlistService =
                new PlaylistService(
                        playlistRepository,
                        playlistSongRepository,
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(SongRepository.class),
                        mock(AclPermissionService.class),
                        mock(ResourceHistoryService.class),
                        songService,
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
                        argThat(
                                criteria ->
                                        criteria.status() == ResourceStatus.ACTIVE
                                                && criteria.query() == null
                                                && !criteria.includeDeleted()),
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
                        argThat(
                                criteria ->
                                        criteria.status() == ResourceStatus.ACTIVE
                                                && criteria.query() == null
                                                && !criteria.includeDeleted()),
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
                        argThat(
                                criteria ->
                                        criteria.status() == ResourceStatus.ACTIVE
                                                && criteria.query() == null
                                                && !criteria.includeDeleted()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PlaylistListResponse result = playlistService.search(null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(playlistRepository)
                .search(
                        argThat(
                                criteria ->
                                        criteria.status() == ResourceStatus.ACTIVE
                                                && criteria.query() == null
                                                && !criteria.includeDeleted()),
                        eq(PageRequest.of(0, 20)));
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Suggest should return up to 10 distinct playlists")
    void suggest_shouldReturnUpToTenDistinctPlaylists() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        List<ResourceName> candidates = new java.util.ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        ResourceName japaneseName = localizedName(1L, "初音ミクのお気に入り", Language.JA);
        candidates.add(candidate(1L, duplicatedUuid, "Miku Favorites"));
        candidates.add(candidate(1L, duplicatedUuid, "Miku Best"));
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
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 11 && resourceIds.contains(1L))))
                .thenReturn(List.of(japaneseName));

        PlaylistSuggestionListResponse result = playlistService.suggest(" mik ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().resourceUuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Miku Favorites");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("初音ミクのお気に入り");
        assertThat(result.items().getFirst().hasMultipleResources()).isFalse();
    }

    @Test
    @DisplayName("Suggest should merge duplicate names and mark them as multiple")
    void suggest_withDuplicateNames_shouldMergeAndFlag() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ResourceName firstCandidate = candidate(firstUuid, "메스머라이저");
        ResourceName secondCandidate = candidate(secondUuid, "메스머라이저");
        when(resourceNameRepository.findPlaylistSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mes"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        PlaylistSuggestionListResponse result = playlistService.suggest(" mes ");

        assertThat(result.items())
                .containsExactly(
                        new com.vocawik.dto.playlist.PlaylistSuggestionElementResponse(
                                null, "메스머라이저", null, true));
    }

    @Test
    @DisplayName("Suggest should return empty list when query is blank")
    void suggest_withBlankQuery_shouldReturnEmptyList() {
        PlaylistSuggestionListResponse result = playlistService.suggest("   ");

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Get playback should reuse song playback items and preserve playlist order")
    void getPlayback_shouldReturnOrderedPlaybackSongs() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        UUID playlistUuid = UUID.randomUUID();
        UUID songUuid = UUID.randomUUID();
        Playlist playlist = playlist(1L, playlistUuid, "Miku Favorites");
        ResourceName koreanName = localizedName(1L, "미쿠 플레이리스트", Language.KO);
        com.vocawik.domain.song.Song song = mock(com.vocawik.domain.song.Song.class);
        PlaylistSong playlistSong = mock(PlaylistSong.class);
        SongPlaybackElementResponse playbackItem =
                new SongPlaybackElementResponse(
                        songUuid,
                        "World is Mine",
                        "월드 이즈 마인",
                        "https://cdn.example.com/song.jpg",
                        "supercell feat. Hatsune Miku",
                        List.of(
                                new SongPlaybackElementResponse.SongPlaybackPv(
                                        UUID.randomUUID(),
                                        "YOUTUBE",
                                        "abc123",
                                        "https://www.youtube.com/watch?v=abc123",
                                        null,
                                        "World is Mine",
                                        "https://cdn.example.com/pv.jpg",
                                        "supercell",
                                        255,
                                        true,
                                        LocalDateTime.parse("2026-03-01T12:00:00"),
                                        null,
                                        0)));

        when(playlistRepository.findByResourceUuid(playlistUuid)).thenReturn(Optional.of(playlist));
        when(playlistSongRepository.findAllWithSongResourceByPlaylistIdOrderBySortOrderAscIdAsc(1L))
                .thenReturn(List.of(playlistSong));
        when(playlistSong.getSong()).thenReturn(song);
        when(playlistSong.getSortOrder()).thenReturn(7);
        when(songService.buildPlaybackItems(
                        argThat(items -> items.size() == 1 && items.getFirst() == song),
                        eq("YOUTUBE")))
                .thenReturn(List.of(playbackItem));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(koreanName));

        PlaylistPlaybackResponse result = playlistService.getPlayback(playlistUuid, "YOUTUBE");

        assertThat(result.resourceUuid()).isEqualTo(playlistUuid);
        assertThat(result.localizedName()).isEqualTo("미쿠 플레이리스트");
        assertThat(result.songs()).hasSize(1);
        assertThat(result.songs().getFirst().resourceUuid()).isEqualTo(songUuid);
        assertThat(result.songs().getFirst().subtitle()).isEqualTo("supercell feat. Hatsune Miku");
        assertThat(result.songs().getFirst().sortOrder()).isEqualTo(7);
        assertThat(result.songs().getFirst().pvs()).hasSize(1);
        assertThat(result.songs().getFirst().pvs().getFirst().service()).isEqualTo("YOUTUBE");
    }

    @Test
    @DisplayName("Update should reject system-managed playlists")
    void update_shouldRejectSystemManagedPlaylist() {
        UUID playlistUuid = UUID.randomUUID();
        Playlist playlist = playlist(1L, playlistUuid, "Managed Playlist");
        when(playlist.isSystemManaged()).thenReturn(true);
        when(playlistRepository.findByResourceUuidAndResourceIsDeletedFalse(playlistUuid))
                .thenReturn(Optional.of(playlist));

        PlaylistUpdateRequest request =
                new PlaylistUpdateRequest(
                        new PlaylistUpdateRequest.CanonicalNameUpdateRequest(
                                Language.EN, "Managed Playlist"),
                        null,
                        null,
                        true,
                        List.of(),
                        List.of(),
                        List.of(),
                        null);

        assertThatThrownBy(() -> playlistService.update(playlistUuid, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        throwable ->
                                assertThat(((BusinessException) throwable).getErrorCode())
                                        .isEqualTo(ErrorCode.PLAYLIST_SYSTEM_MANAGED));
    }

    @Test
    @DisplayName("Delete should reject system-managed playlists")
    void delete_shouldRejectSystemManagedPlaylist() {
        UUID playlistUuid = UUID.randomUUID();
        Playlist playlist = playlist(1L, playlistUuid, "Managed Playlist");
        when(playlist.isSystemManaged()).thenReturn(true);
        when(playlistRepository.findByResourceUuidAndResourceIsDeletedFalse(playlistUuid))
                .thenReturn(Optional.of(playlist));

        assertThatThrownBy(() -> playlistService.delete(playlistUuid))
                .isInstanceOf(BusinessException.class)
                .satisfies(
                        throwable ->
                                assertThat(((BusinessException) throwable).getErrorCode())
                                        .isEqualTo(ErrorCode.PLAYLIST_SYSTEM_MANAGED));
    }

    private ResourceName candidate(UUID uuid, String name) {
        return candidate(Math.abs(uuid.getMostSignificantBits()) % 10_000 + 1, uuid, name);
    }

    private ResourceName candidate(Long resourceId, UUID uuid, String name) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
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
        when(resource.getThumbnailUrl()).thenReturn("https://cdn.example.com/playlist.jpg");

        Playlist playlist = mock(Playlist.class);
        when(playlist.getId()).thenReturn(resourceId);
        when(playlist.getResource()).thenReturn(resource);
        when(playlist.isSystemManaged()).thenReturn(false);
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
