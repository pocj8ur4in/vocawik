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
import com.vocawik.domain.artist.ArtistGroup;
import com.vocawik.domain.playlist.Playlist;
import com.vocawik.domain.playlist.PlaylistSong;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongArtist;
import com.vocawik.domain.song.SongArtistRole;
import com.vocawik.domain.song.SongPv;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.domain.song.SongPvView;
import com.vocawik.domain.song.SongRelation;
import com.vocawik.domain.song.SongType;
import com.vocawik.domain.song.SongVocal;
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
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.service.acl.AclPermissionService;
import com.vocawik.service.audio.AudioObjectStorageService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private AudioObjectStorageService audioObjectStorageService;
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
        audioObjectStorageService = mock(AudioObjectStorageService.class);
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
                        mock(AclPermissionService.class),
                        mock(ResourceHistoryService.class),
                        mock(ResourcePopularityService.class),
                        audioObjectStorageService,
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
        SecurityContextHolder.clearContext();
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
        assertThat(result.items().getFirst().resourceType()).isEqualTo(ResourceType.SONG.name());
        assertThat(result.items().getFirst().hasMultipleResources()).isFalse();
    }

    @Test
    @DisplayName("Suggest should merge duplicate names and mark them as multiple")
    void suggest_withDuplicateNames_shouldMergeAndFlag() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ResourceName firstCandidate = candidate(firstUuid, "메스머라이저", ResourceType.SONG);
        ResourceName secondCandidate = candidate(secondUuid, "메스머라이저", ResourceType.ARTIST);
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
                                null, "메스머라이저", null, null, true));
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
        Artist artist = artist(2L, UUID.randomUUID(), "Hachioji-P");
        Vocal vocal = vocal(3L, UUID.randomUUID(), "Hatsune Miku");
        Song targetSong = song(4L, UUID.randomUUID(), "World is Mine");
        Song sourceSong = song(5L, UUID.randomUUID(), "Melt");
        Playlist playlist = playlist(6L, UUID.randomUUID(), "Miku Favorites");
        ResourceName japaneseName = localizedName(1L, "テル・ユア・ワールド", Language.JA);
        ResourceName artistJapaneseName = localizedName(2L, "八王子P", Language.JA);
        ResourceName vocalJapaneseName = localizedName(3L, "初音ミク", Language.JA);
        ResourceName targetJapaneseName = localizedName(4L, "ワールドイズマイン", Language.JA);
        ResourceName sourceJapaneseName = localizedName(5L, "メルト", Language.JA);
        ResourceName playlistJapaneseName = localizedName(6L, "ミクお気に入り", Language.JA);
        when(songRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(song));
        stubEmptyResourceDetails(1L);
        when(songArtistRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(songArtist(song, artist, true, 0)));
        when(songVocalRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(songVocal(song, vocal, true, 0)));
        when(songRelationRepository.findAllBySourceSongIdOrderByIdAsc(eq(1L)))
                .thenReturn(List.of(songRelation(song, targetSong)));
        when(songRelationRepository.findAllByTargetSongIdOrderByIdAsc(eq(1L)))
                .thenReturn(List.of(songRelation(sourceSong, song)));
        when(playlistSongRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(playlistSong(playlist, song, 0)));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 6
                                                && resourceIds.containsAll(
                                                        List.of(1L, 2L, 3L, 4L, 5L, 6L)))))
                .thenReturn(
                        List.of(
                                japaneseName,
                                artistJapaneseName,
                                vocalJapaneseName,
                                targetJapaneseName,
                                sourceJapaneseName,
                                playlistJapaneseName));

        var result = resourceService.getSongByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Tell Your World");
        assertThat(result.localizedName()).isEqualTo("テル・ユア・ワールド");
        assertThat(result.artists().getFirst().localizedName()).isEqualTo("八王子P");
        assertThat(result.vocals().getFirst().vocalLocalizedName()).isEqualTo("初音ミク");
        assertThat(result.relations().getFirst().targetSongLocalizedName()).isEqualTo("ワールドイズマイン");
        assertThat(result.relations().getFirst().targetSongThumbnailUrl())
                .isEqualTo("https://cdn.example.com/world-is-mine.webp");
        assertThat(result.incomingRelations().getFirst().sourceSongLocalizedName())
                .isEqualTo("メルト");
        assertThat(result.incomingRelations().getFirst().sourceSongThumbnailUrl())
                .isEqualTo("https://cdn.example.com/melt.webp");
        assertThat(result.playlists().getFirst().playlistLocalizedName()).isEqualTo("ミクお気に入り");
    }

    @Test
    @DisplayName("Artist detail should include localized name matching request locale")
    void getArtistByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Artist artist = artist(1L, resourceUuid, "Hachioji-P");
        Song song = song(2L, UUID.randomUUID(), "GimmexGimme");
        Artist memberArtist = artist(3L, UUID.randomUUID(), "KAFU");
        Artist groupArtist = artist(4L, UUID.randomUUID(), "V.W.P");
        ResourceName japaneseName = localizedName(1L, "八王子P", Language.JA);
        ResourceName songJapaneseName = localizedName(2L, "GimmexGimme", Language.JA);
        ResourceName memberJapaneseName = localizedName(3L, "可不", Language.JA);
        ResourceName groupJapaneseName = localizedName(4L, "花譜グループ", Language.JA);
        when(artistRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(artist));
        stubEmptyResourceDetails(1L);
        when(songArtistRepository.countByArtistId(eq(1L))).thenReturn(0L);
        when(songArtistRepository.findRecentByArtistId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(songArtist(song, artist, true, 0)));
        when(songArtistRepository.findPopularByArtistId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(songArtist(song, artist, true, 0)));
        when(artistGroupRepository.findAllByGroupArtistIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(artistGroup(artist, memberArtist, 0)));
        when(artistGroupRepository.findAllByMemberArtistIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(artistGroup(groupArtist, artist, 0)));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 4
                                                && resourceIds.containsAll(
                                                        List.of(1L, 2L, 3L, 4L)))))
                .thenReturn(
                        List.of(
                                japaneseName,
                                songJapaneseName,
                                memberJapaneseName,
                                groupJapaneseName));

        var result = resourceService.getArtistByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Hachioji-P");
        assertThat(result.localizedName()).isEqualTo("八王子P");
        assertThat(result.songs().recentSongs().getFirst().songLocalizedName())
                .isEqualTo("GimmexGimme");
        assertThat(result.songs().popularSongs().getFirst().songLocalizedName())
                .isEqualTo("GimmexGimme");
        assertThat(result.groups().getFirst().memberArtistLocalizedName()).isEqualTo("可不");
        assertThat(result.members().getFirst().groupArtistLocalizedName()).isEqualTo("花譜グループ");
    }

    @Test
    @DisplayName("Vocal detail should include localized name matching request locale")
    void getVocalByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Vocal vocal = vocal(1L, resourceUuid, "Hatsune Miku");
        Song song = song(2L, UUID.randomUUID(), "Tell Your World");
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        ResourceName songJapaneseName = localizedName(2L, "テル・ユア・ワールド", Language.JA);
        when(vocalRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(vocal));
        stubEmptyResourceDetails(1L);
        when(songVocalRepository.countByVocalId(eq(1L))).thenReturn(0L);
        when(songVocalRepository.findRecentByVocalId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(songVocal(song, vocal, true, 0)));
        when(songVocalRepository.findPopularByVocalId(eq(1L), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(songVocal(song, vocal, true, 0)));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 2
                                                && resourceIds.containsAll(List.of(1L, 2L)))))
                .thenReturn(List.of(japaneseName, songJapaneseName));

        var result = resourceService.getVocalByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Hatsune Miku");
        assertThat(result.localizedName()).isEqualTo("初音ミク");
        assertThat(result.songs().recentSongs().getFirst().songLocalizedName())
                .isEqualTo("テル・ユア・ワールド");
        assertThat(result.songs().popularSongs().getFirst().songLocalizedName())
                .isEqualTo("テル・ユア・ワールド");
    }

    @Test
    @DisplayName("Playlist detail should include localized name matching request locale")
    void getPlaylistByResourceUuid_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        Playlist playlist = playlist(1L, resourceUuid, "Miku Favorites");
        Song song = song(2L, UUID.randomUUID(), "Tell Your World");
        ResourceName japaneseName = localizedName(1L, "ミクお気に入り", Language.JA);
        ResourceName songJapaneseName = localizedName(2L, "テル・ユア・ワールド", Language.JA);
        when(playlistRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(playlist));
        stubEmptyResourceDetails(1L);
        when(playlistSongRepository.findAllByPlaylistIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(playlistSong(playlist, song, 0)));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        argThat(
                                resourceIds ->
                                        resourceIds.size() == 2
                                                && resourceIds.containsAll(List.of(1L, 2L)))))
                .thenReturn(List.of(japaneseName, songJapaneseName));

        var result = resourceService.getPlaylistByResourceUuid(resourceUuid);

        assertThat(result.canonicalName()).isEqualTo("Miku Favorites");
        assertThat(result.localizedName()).isEqualTo("ミクお気に入り");
        assertThat(result.songs().getFirst().songLocalizedName()).isEqualTo("テル・ユア・ワールド");
    }

    @Test
    @DisplayName("Song detail should hide PV view history for non-admin")
    void getSongByResourceUuid_shouldHidePvViewsForNonAdmin() {
        UUID resourceUuid = UUID.randomUUID();
        Song song = song(1L, resourceUuid, "Tell Your World");
        SongPv pv = songPv(10L);
        SongPvView pvView = songPvView(pv, 123L, LocalDateTime.parse("2026-03-20T12:45:43"));
        when(songRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(song));
        stubEmptyResourceDetails(1L);
        when(songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(pv));
        when(songPvViewRepository.findAllBySongPvIdIn(eq(List.of(10L))))
                .thenReturn(List.of(pvView));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(UUID.randomUUID(), "USER"), null, List.of()));

        var result = resourceService.getSongByResourceUuid(resourceUuid);

        assertThat(result.pvs()).hasSize(1);
        assertThat(result.pvs().getFirst().views()).isEmpty();
    }

    @Test
    @DisplayName("Song detail should expose PV view history for admin")
    void getSongByResourceUuid_shouldExposePvViewsForAdmin() {
        UUID resourceUuid = UUID.randomUUID();
        Song song = song(1L, resourceUuid, "Tell Your World");
        SongPv pv = songPv(10L);
        SongPvView pvView = songPvView(pv, 123L, LocalDateTime.parse("2026-03-20T12:45:43"));
        when(songRepository.findByResourceUuid(eq(resourceUuid)))
                .thenReturn(java.util.Optional.of(song));
        stubEmptyResourceDetails(1L);
        when(songPvRepository.findAllBySongIdOrderBySortOrderAscIdAsc(eq(1L)))
                .thenReturn(List.of(pv));
        when(songPvViewRepository.findAllBySongPvIdIn(eq(List.of(10L))))
                .thenReturn(List.of(pvView));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(UUID.randomUUID(), "ADMIN"), null, List.of()));

        var result = resourceService.getSongByResourceUuid(resourceUuid);

        assertThat(result.pvs()).hasSize(1);
        assertThat(result.pvs().getFirst().views()).hasSize(1);
        assertThat(result.pvs().getFirst().views().getFirst().viewCount()).isEqualTo(123L);
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
        when(songPvViewRepository.findAllBySongPvIdIn(eq(List.of()))).thenReturn(List.of());
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

    private ResourceName candidate(UUID uuid, String name, ResourceType resourceType) {
        return candidate(
                Math.abs(uuid.getMostSignificantBits()) % 10_000 + 1, uuid, name, resourceType);
    }

    private ResourceName candidate(Long resourceId, UUID uuid, String name) {
        return candidate(resourceId, uuid, name, ResourceType.SONG);
    }

    private ResourceName candidate(
            Long resourceId, UUID uuid, String name, ResourceType resourceType) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(uuid);
        when(resource.getResourceType()).thenReturn(resourceType);

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
        if (resourceId == 4L) {
            when(resource.getThumbnailUrl())
                    .thenReturn("https://cdn.example.com/world-is-mine.webp");
        } else if (resourceId == 5L) {
            when(resource.getThumbnailUrl()).thenReturn("https://cdn.example.com/melt.webp");
        } else {
            when(resource.getThumbnailUrl()).thenReturn(null);
        }
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

    private SongArtist songArtist(Song song, Artist artist, boolean isMain, int sortOrder) {
        return SongArtist.create(
                song, artist, java.util.Set.of(SongArtistRole.PRODUCER), isMain, sortOrder);
    }

    private SongVocal songVocal(Song song, Vocal vocal, boolean isMain, int sortOrder) {
        return SongVocal.create(song, vocal, isMain, sortOrder);
    }

    private SongRelation songRelation(Song sourceSong, Song targetSong) {
        return SongRelation.create(sourceSong, targetSong);
    }

    private SongPv songPv(Long id) {
        SongPv pv = mock(SongPv.class);
        Song song = mock(Song.class);
        when(pv.getId()).thenReturn(id);
        when(pv.getUuid()).thenReturn(UUID.randomUUID());
        when(pv.getSong()).thenReturn(song);
        when(pv.getService()).thenReturn(SongPvProvider.YOUTUBE);
        when(pv.getVideoKey()).thenReturn("video-" + id);
        when(pv.getUrl()).thenReturn("https://www.youtube.com/watch?v=video-" + id);
        when(pv.getTitle()).thenReturn("PV " + id);
        when(pv.getThumbnailUrl()).thenReturn(null);
        when(pv.getUploaderKey()).thenReturn(null);
        when(pv.getDurationSeconds()).thenReturn(null);
        when(pv.isOfficial()).thenReturn(true);
        when(pv.getPublishedAt()).thenReturn(null);
        when(pv.getSortOrder()).thenReturn(0);
        when(pv.getPiaproAudioUrl()).thenReturn(null);
        when(pv.getBilibiliCid()).thenReturn(null);
        when(pv.getBandcampExternalUrl()).thenReturn(null);
        when(pv.getCreatedAt()).thenReturn(LocalDateTime.parse("2026-03-20T12:45:43"));
        when(pv.getUpdatedAt()).thenReturn(LocalDateTime.parse("2026-03-20T12:45:43"));
        return pv;
    }

    private SongPvView songPvView(SongPv pv, Long viewCount, LocalDateTime createdAt) {
        SongPvView view = mock(SongPvView.class);
        when(view.getId()).thenReturn(1L);
        when(view.getSongPv()).thenReturn(pv);
        when(view.getUuid()).thenReturn(UUID.randomUUID());
        when(view.getViewCount()).thenReturn(viewCount);
        when(view.getCreatedAt()).thenReturn(createdAt);
        when(view.getUpdatedAt()).thenReturn(createdAt);
        return view;
    }

    private PlaylistSong playlistSong(Playlist playlist, Song song, int sortOrder) {
        return PlaylistSong.create(playlist, song, sortOrder);
    }

    private ArtistGroup artistGroup(Artist groupArtist, Artist memberArtist, int sortOrder) {
        return ArtistGroup.create(groupArtist, memberArtist, sortOrder);
    }
}
