package com.vocawik.service.song;

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
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.domain.song.Song;
import com.vocawik.domain.song.SongPv;
import com.vocawik.domain.song.SongPvProvider;
import com.vocawik.domain.song.SongType;
import com.vocawik.dto.song.SongCreateRequest;
import com.vocawik.dto.song.SongListResponse;
import com.vocawik.dto.song.SongSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistRepository;
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
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.acl.AclPermissionService;
import com.vocawik.service.audio.SongAudioImportService;
import com.vocawik.service.history.ResourceHistoryService;
import com.vocawik.service.pv.client.PvMetaApiClientResolver;
import com.vocawik.service.pv.detector.PvUrlDetector;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
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
import org.springframework.test.util.ReflectionTestUtils;

class SongServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private SongRepository songRepository;
    private SongService songService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        songRepository = mock(SongRepository.class);
        songService =
                new SongService(
                        songRepository,
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(SongLinkRepository.class),
                        mock(SongLyricRepository.class),
                        mock(SongPvRepository.class),
                        mock(SongPvViewRepository.class),
                        mock(SongArtistRepository.class),
                        mock(SongVocalRepository.class),
                        mock(SongRelationRepository.class),
                        mock(ArtistRepository.class),
                        mock(VocalRepository.class),
                        mock(AclPermissionService.class),
                        mock(ResourceHistoryService.class),
                        mock(EntityManager.class),
                        new ObjectMapper(),
                        mock(PvUrlDetector.class),
                        mock(PvMetaApiClientResolver.class),
                        mock(SongAudioImportService.class));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Search should include localized name matching request locale")
    void search_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Song song =
                song(
                        1L,
                        UUID.randomUUID(),
                        "Tell Your World",
                        SongType.ORIGINAL,
                        LocalDateTime.parse("2026-03-01T12:00:00"));
        ResourceName koreanName = localizedName(1L, "텔 유어 월드", Language.KO);
        when(songRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.songTypes().isEmpty()
                                                && criteria.query() == null
                                                && criteria.artistUuids().isEmpty()
                                                && criteria.vocalUuids().isEmpty()
                                                && criteria.publishedFrom() == null
                                                && criteria.publishedTo() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(song), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(koreanName));

        SongListResponse result =
                songService.search(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Tell Your World");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("텔 유어 월드");
    }

    @Test
    @DisplayName("Search should return null localized name when request locale name is missing")
    void search_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        Song song =
                song(
                        1L,
                        UUID.randomUUID(),
                        "Tell Your World",
                        SongType.ORIGINAL,
                        LocalDateTime.parse("2026-03-01T12:00:00"));
        ResourceName koreanName = localizedName(1L, "텔 유어 월드", Language.KO);
        when(songRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.songTypes().isEmpty()
                                                && criteria.query() == null
                                                && criteria.artistUuids().isEmpty()
                                                && criteria.vocalUuids().isEmpty()
                                                && criteria.publishedFrom() == null
                                                && criteria.publishedTo() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(song), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(koreanName));

        SongListResponse result =
                songService.search(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Search should not query resource names when result is empty")
    void search_withEmptyResult_shouldSkipLocalizedNameLookup() {
        when(songRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.songTypes().isEmpty()
                                                && criteria.query() == null
                                                && criteria.artistUuids().isEmpty()
                                                && criteria.vocalUuids().isEmpty()
                                                && criteria.publishedFrom() == null
                                                && criteria.publishedTo() == null),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        SongListResponse result =
                songService.search(null, null, null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(songRepository)
                .search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.songTypes().isEmpty()
                                                && criteria.query() == null
                                                && criteria.artistUuids().isEmpty()
                                                && criteria.vocalUuids().isEmpty()
                                                && criteria.publishedFrom() == null
                                                && criteria.publishedTo() == null),
                        eq(PageRequest.of(0, 20)));
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Suggest should return resource uuid when name maps to a single resource")
    void suggest_withSingleResourceName_shouldReturnUuid() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        UUID resourceUuid = UUID.randomUUID();
        ResourceName candidate = candidate(1L, resourceUuid, "메스머라이저");
        ResourceName koreanName = localizedName(1L, "메스머라이저", Language.KO);
        when(resourceNameRepository.findSongSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mes"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(List.of(candidate));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(koreanName));

        SongSuggestionListResponse result = songService.suggest(" mes ");

        assertThat(result.items())
                .containsExactly(
                        new com.vocawik.dto.song.SongSuggestionElementResponse(
                                resourceUuid, "메스머라이저", "메스머라이저", false));
    }

    @Test
    @DisplayName("Suggest should merge duplicate names and mark them as multiple")
    void suggest_withDuplicateNames_shouldMergeAndFlag() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ResourceName firstCandidate = candidate(firstUuid, "메스머라이저");
        ResourceName secondCandidate = candidate(secondUuid, "메스머라이저");
        when(resourceNameRepository.findSongSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mes"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        SongSuggestionListResponse result = songService.suggest(" mes ");

        assertThat(result.items())
                .containsExactly(
                        new com.vocawik.dto.song.SongSuggestionElementResponse(
                                null, "메스머라이저", null, true));
    }

    @Test
    @DisplayName("normalizeSongPvExtra should preserve audioUrl for AUDIO provider")
    void normalizeSongPvExtra_withAudioProvider_shouldKeepAudioUrl() {
        SongCreateRequest.SongPvExtraCreateRequest extraRequest =
                new SongCreateRequest.SongPvExtraCreateRequest(
                        " https://cdn.example.com/audio.mp3 ", 123L, "https://example.com/track");

        Object extra =
                ReflectionTestUtils.invokeMethod(
                        songService, "normalizeSongPvExtra", SongPvProvider.AUDIO, extraRequest);
        Object audioUrl = ReflectionTestUtils.invokeMethod(extra, "audioUrl");
        Object cid = ReflectionTestUtils.invokeMethod(extra, "cid");
        Object externalUrl = ReflectionTestUtils.invokeMethod(extra, "externalUrl");

        assertThat(audioUrl).isEqualTo("https://cdn.example.com/audio.mp3");
        assertThat(cid).isNull();
        assertThat(externalUrl).isNull();
    }

    @Test
    @DisplayName("buildAudioImportCandidates should prefer persisted PV sort order")
    void buildAudioImportCandidates_shouldUsePersistedPvUrls() {
        SongPv firstPv = mock(SongPv.class);
        when(firstPv.getUrl()).thenReturn("https://example.com/custom-first");
        when(firstPv.getTitle()).thenReturn("First");
        when(firstPv.getThumbnailUrl()).thenReturn(null);
        when(firstPv.getSortOrder()).thenReturn(10);

        SongPv secondPv = mock(SongPv.class);
        when(secondPv.getUrl()).thenReturn("https://www.nicovideo.jp/watch/sm9");
        when(secondPv.getTitle()).thenReturn("Second");
        when(secondPv.getThumbnailUrl()).thenReturn(null);
        when(secondPv.getSortOrder()).thenReturn(5);

        List<SongPv> pvs = List.of(firstPv, secondPv);

        @SuppressWarnings("unchecked")
        List<SongAudioImportService.AudioSourceCandidate> candidates =
                (List<SongAudioImportService.AudioSourceCandidate>)
                        ReflectionTestUtils.invokeMethod(
                                songService, "buildAudioImportCandidates", pvs);

        assertThat(candidates).hasSize(2);
        assertThat(candidates.get(0).url()).isEqualTo("https://www.nicovideo.jp/watch/sm9");
        assertThat(candidates.get(1).url()).isEqualTo("https://example.com/custom-first");
    }

    @Test
    @DisplayName("normalizeRequired should use pvs.url field name for PV URL errors")
    void normalizeRequired_withBlankPvUrl_shouldUsePvFieldName() {
        assertThatThrownBy(
                        () ->
                                ReflectionTestUtils.invokeMethod(
                                        songService, "normalizeRequired", "   ", "pvs.url"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pvs.url is required");
    }

    private Song song(
            Long resourceId,
            UUID resourceUuid,
            String canonicalName,
            SongType songType,
            LocalDateTime publishedAt) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(resourceUuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getStatus()).thenReturn(ResourceStatus.ACTIVE);
        when(resource.getViewCount()).thenReturn(0L);

        Song song = mock(Song.class);
        when(song.getResource()).thenReturn(resource);
        when(song.getSongType()).thenReturn(songType);
        when(song.getPublishedAt()).thenReturn(publishedAt);
        return song;
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
}
