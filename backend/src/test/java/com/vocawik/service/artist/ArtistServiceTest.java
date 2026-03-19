package com.vocawik.service.artist;

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
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.artist.ArtistListResponse;
import com.vocawik.dto.artist.ArtistSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistLinkRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.service.history.ResourceHistoryService;
import jakarta.persistence.EntityManager;
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

class ArtistServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private ArtistRepository artistRepository;
    private ArtistService artistService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        artistRepository = mock(ArtistRepository.class);
        artistService =
                new ArtistService(
                        artistRepository,
                        mock(ArtistGroupRepository.class),
                        mock(ArtistLinkRepository.class),
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(ResourceHistoryService.class),
                        mock(EntityManager.class),
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Suggest should return up to 10 distinct artists")
    void suggest_shouldReturnUpToTenDistinctArtists() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        List<ResourceName> candidates = new ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        ResourceName japaneseName = localizedName(1L, "八王子P", Language.JA);
        candidates.add(candidate(1L, duplicatedUuid, "Hachioji-P"));
        candidates.add(candidate(1L, duplicatedUuid, "8#Prince"));
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate(UUID.randomUUID(), "Candidate " + i));
        }
        when(resourceNameRepository.findArtistSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("hachi"),
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

        ArtistSuggestionListResponse result = artistService.suggest(" hachi ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().resourceUuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Hachioji-P");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("八王子P");
        assertThat(result.items().getFirst().hasMultipleResources()).isFalse();
    }

    @Test
    @DisplayName("Suggest should merge duplicate names and mark them as multiple")
    void suggest_withDuplicateNames_shouldMergeAndFlag() {
        UUID firstUuid = UUID.randomUUID();
        UUID secondUuid = UUID.randomUUID();
        ResourceName firstCandidate = candidate(firstUuid, "메스머라이저");
        ResourceName secondCandidate = candidate(secondUuid, "메스머라이저");
        when(resourceNameRepository.findArtistSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mes"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(List.of(firstCandidate, secondCandidate));

        ArtistSuggestionListResponse result = artistService.suggest(" mes ");

        assertThat(result.items())
                .containsExactly(
                        new com.vocawik.dto.artist.ArtistSuggestionElementResponse(
                                null, "메스머라이저", null, true));
    }

    @Test
    @DisplayName("Suggest should return empty list when query is blank")
    void suggest_withBlankQuery_shouldReturnEmptyList() {
        ArtistSuggestionListResponse result = artistService.suggest("   ");

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Search should include localized name matching request locale")
    void search_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        Artist artist = artist(1L, UUID.randomUUID(), "Hachioji-P");
        ResourceName japaneseName = localizedName(1L, "八王子P", Language.JA);
        when(artistRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()
                                                && criteria.groupArtistUuids().isEmpty()
                                                && criteria.memberArtistUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(artist), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        ArtistListResponse result =
                artistService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Hachioji-P");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("八王子P");
    }

    @Test
    @DisplayName("Search should return null localized name when request locale name is missing")
    void search_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Artist artist = artist(1L, UUID.randomUUID(), "Hachioji-P");
        ResourceName japaneseName = localizedName(1L, "八王子P", Language.JA);
        when(artistRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()
                                                && criteria.groupArtistUuids().isEmpty()
                                                && criteria.memberArtistUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(artist), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        ArtistListResponse result =
                artistService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Search should not query resource names when result is empty")
    void search_withEmptyResult_shouldSkipLocalizedNameLookup() {
        when(artistRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()
                                                && criteria.groupArtistUuids().isEmpty()
                                                && criteria.memberArtistUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ArtistListResponse result =
                artistService.search(null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(artistRepository)
                .search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()
                                                && criteria.groupArtistUuids().isEmpty()
                                                && criteria.memberArtistUuids().isEmpty()),
                        eq(PageRequest.of(0, 20)));
        verifyNoInteractions(resourceNameRepository);
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

    private Artist artist(Long resourceId, UUID resourceUuid, String canonicalName) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(resourceUuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getStatus()).thenReturn(ResourceStatus.ACTIVE);
        when(resource.getViewCount()).thenReturn(0L);

        Artist artist = mock(Artist.class);
        when(artist.getResource()).thenReturn(resource);
        return artist;
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
