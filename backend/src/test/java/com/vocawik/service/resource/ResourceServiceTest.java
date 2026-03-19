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
import com.vocawik.domain.artist.Artist;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongType;
import com.vocawik.domain.vocal.Vocal;
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
import java.time.LocalDateTime;
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

    private AclRepository aclRepository;
    private SongRepository songRepository;
    private SongLinkRepository songLinkRepository;
    private SongLyricRepository songLyricRepository;
    private SongPvRepository songPvRepository;
    private SongPvViewRepository songPvViewRepository;
    private SongArtistRepository songArtistRepository;
    private SongVocalRepository songVocalRepository;
    private SongRelationRepository songRelationRepository;
    private PlaylistSongRepository playlistSongRepository;
    private PlaylistRepository playlistRepository;
    private ArtistRepository artistRepository;
    private ArtistGroupRepository artistGroupRepository;
    private ArtistLinkRepository artistLinkRepository;
    private VocalRepository vocalRepository;
    private VocalLinkRepository vocalLinkRepository;
    private ResourceNameRepository resourceNameRepository;
    private ResourceRepository resourceRepository;
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        aclRepository = mock(AclRepository.class);
        songRepository = mock(SongRepository.class);
        songLinkRepository = mock(SongLinkRepository.class);
        songLyricRepository = mock(SongLyricRepository.class);
        songPvRepository = mock(SongPvRepository.class);
        songPvViewRepository = mock(SongPvViewRepository.class);
        songArtistRepository = mock(SongArtistRepository.class);
        songVocalRepository = mock(SongVocalRepository.class);
        songRelationRepository = mock(SongRelationRepository.class);
        playlistSongRepository = mock(PlaylistSongRepository.class);
        playlistRepository = mock(PlaylistRepository.class);
        artistRepository = mock(ArtistRepository.class);
        artistGroupRepository = mock(ArtistGroupRepository.class);
        artistLinkRepository = mock(ArtistLinkRepository.class);
        vocalRepository = mock(VocalRepository.class);
        vocalLinkRepository = mock(VocalLinkRepository.class);
        resourceNameRepository = mock(ResourceNameRepository.class);
        resourceRepository = mock(ResourceRepository.class);
        resourceService =
                new ResourceService(
                        resourceRepository,
                        resourceNameRepository,
                        aclRepository,
                        songRepository,
                        songLinkRepository,
                        songLyricRepository,
                        songPvRepository,
                        songPvViewRepository,
                        songArtistRepository,
                        songVocalRepository,
                        songRelationRepository,
                        playlistSongRepository,
                        playlistRepository,
                        artistRepository,
                        artistGroupRepository,
                        artistLinkRepository,
                        vocalRepository,
                        vocalLinkRepository,
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
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        List<ResourceName> candidates = new ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        candidates.add(candidate(1L, duplicatedUuid, "Miku"));
        candidates.add(candidate(1L, duplicatedUuid, "Hatsune Miku"));
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
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 11 && resourceIds.contains(1L))))
                .thenReturn(List.of(japaneseName));

        ResourceSuggestionListResponse result = resourceService.suggest(" mik ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().resourceUuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Miku");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("初音ミク");
        assertThat(result.items().getFirst().hasMultipleResources()).isFalse();
    }

    @Test
    @DisplayName("Suggest should merge duplicate names and mark them as multiple")
    void suggest_withDuplicateNames_shouldMergeAndFlag() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ResourceName firstCandidate = candidate(firstUuid, "메스머라이저");
        ResourceName secondCandidate = candidate(secondUuid, "메스머라이저");
        when(resourceNameRepository.findSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mes"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        ResourceSuggestionListResponse result = resourceService.suggest(" mes ");

        assertThat(result.items())
                .containsExactly(
                        new com.vocawik.dto.resource.ResourceSuggestionElementResponse(
                                null, "메스머라이저", null, true));
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

    @Test
    @DisplayName("Song detail should include localized name matching request locale")
    void getSongByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Song song = song(1L, resourceUuid, "Tell Your World");
        ResourceName japaneseName = localizedName(1L, "テル・ユア・ワールド", Language.JA);
        when(songRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(song));
        stubEmptyResourceDetails(1L);
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        var result = resourceService.getSongByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Tell Your World");
        assertThat(result.localizedName()).isEqualTo("テル・ユア・ワールド");
    }

    @Test
    @DisplayName("Artist detail should include localized name matching request locale")
    void getArtistByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Artist artist = artist(1L, resourceUuid, "Hachioji-P");
        ResourceName japaneseName = localizedName(1L, "八王子P", Language.JA);
        when(artistRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(artist));
        stubEmptyResourceDetails(1L);
        when(songArtistRepository.countByArtistId(eq(1L))).thenReturn(0L);
        when(songArtistRepository.findRecentByArtistId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());
        when(songArtistRepository.findPopularByArtistId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        var result = resourceService.getArtistByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Hachioji-P");
        assertThat(result.localizedName()).isEqualTo("八王子P");
    }

    @Test
    @DisplayName("Vocal detail should include localized name matching request locale")
    void getVocalByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Vocal vocal = vocal(1L, resourceUuid, "Hatsune Miku");
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(vocalRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(vocal));
        stubEmptyResourceDetails(1L);
        when(songVocalRepository.countByVocalId(eq(1L))).thenReturn(0L);
        when(songVocalRepository.findRecentByVocalId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());
        when(songVocalRepository.findPopularByVocalId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of());
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        var result = resourceService.getVocalByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Hatsune Miku");
        assertThat(result.localizedName()).isEqualTo("初音ミク");
    }

    @Test
    @DisplayName("Playlist detail should include localized name matching request locale")
    void getPlaylistByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Playlist playlist = playlist(1L, resourceUuid, "Miku Favorites");
        ResourceName japaneseName = localizedName(1L, "ミクお気に入り", Language.JA);
        when(playlistRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(playlist));
        stubEmptyResourceDetails(1L);
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        var result = resourceService.getPlaylistByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Miku Favorites");
        assertThat(result.localizedName()).isEqualTo("ミクお気に入り");
    }

    private void stubEmptyResourceDetails(Long resourceId) {
        when(resourceNameRepository.findAllByResourceIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songLinkRepository.findAllBySongIdOrderByIdAsc(eq(resourceId))).thenReturn(List.of());
        when(songLyricRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songRelationRepository.findAllBySourceSongIdOrderByIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(songRelationRepository.findAllByTargetSongIdOrderByIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(playlistSongRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(artistLinkRepository.findAllByArtistIdOrderByIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(artistGroupRepository.findAllByGroupArtistIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(artistGroupRepository.findAllByMemberArtistIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(vocalLinkRepository.findAllByVocalIdOrderByIdAsc(eq(resourceId)))
                .thenReturn(List.of());
        when(playlistSongRepository.findAllByPlaylistIdOrderBySortOrderAscIdAsc(eq(resourceId)))
                .thenReturn(List.of());
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

    private Song song(Long resourceId, UUID uuid, String canonicalName) {
        Song song = mock(Song.class);
        Resource resource = resource(resourceId, uuid, canonicalName, ResourceType.SONG);
        when(song.getId()).thenReturn(resourceId);
        when(song.getResource()).thenReturn(resource);
        when(song.getSongType()).thenReturn(SongType.ORIGINAL);
        when(song.getContent()).thenReturn(null);
        when(song.getPublishedAt()).thenReturn(LocalDateTime.parse("2026-03-19T12:45:43"));
        return song;
    }

    private Artist artist(Long resourceId, UUID uuid, String canonicalName) {
        Artist artist = mock(Artist.class);
        Resource resource = resource(resourceId, uuid, canonicalName, ResourceType.ARTIST);
        when(artist.getId()).thenReturn(resourceId);
        when(artist.getResource()).thenReturn(resource);
        when(artist.getContent()).thenReturn(null);
        return artist;
    }

    private Vocal vocal(Long resourceId, UUID uuid, String canonicalName) {
        Vocal vocal = mock(Vocal.class);
        Resource resource = resource(resourceId, uuid, canonicalName, ResourceType.VOCAL);
        when(vocal.getId()).thenReturn(resourceId);
        when(vocal.getResource()).thenReturn(resource);
        when(vocal.getContent()).thenReturn(null);
        return vocal;
    }

    private Playlist playlist(Long resourceId, UUID uuid, String canonicalName) {
        Playlist playlist = mock(Playlist.class);
        Resource resource = resource(resourceId, uuid, canonicalName, ResourceType.PLAYLIST);
        when(playlist.getId()).thenReturn(resourceId);
        when(playlist.getResource()).thenReturn(resource);
        when(playlist.getContent()).thenReturn(null);
        when(playlist.isPublic()).thenReturn(true);
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
