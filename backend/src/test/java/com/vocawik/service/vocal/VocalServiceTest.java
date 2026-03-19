package com.vocawik.service.vocal;

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
import com.vocawik.domain.vocal.Vocal;
import com.vocawik.dto.vocal.VocalListResponse;
import com.vocawik.dto.vocal.VocalSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
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

class VocalServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private VocalRepository vocalRepository;
    private VocalService vocalService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        resourceNameRepository = mock(ResourceNameRepository.class);
        vocalRepository = mock(VocalRepository.class);
        vocalService =
                new VocalService(
                        vocalRepository,
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(VocalLinkRepository.class),
                        mock(AclRepository.class),
                        mock(ResourceHistoryService.class),
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Suggest should return up to 10 distinct vocals")
    void suggest_shouldReturnUpToTenDistinctVocals() {
        List<ResourceName> candidates = new ArrayList<>();
        UUID duplicatedUuid = UUID.randomUUID();
        candidates.add(candidate(duplicatedUuid, "Hatsune Miku"));
        candidates.add(candidate(duplicatedUuid, "Miku"));
        for (int i = 0; i < 10; i++) {
            candidates.add(candidate(UUID.randomUUID(), "Candidate " + i));
        }
        when(resourceNameRepository.findVocalSuggestionCandidates(
                        eq(ResourceStatus.ACTIVE),
                        eq("mik"),
                        argThat(
                                pageable ->
                                        pageable.getPageNumber() == 0
                                                && pageable.getPageSize() == 30)))
                .thenReturn(candidates);

        VocalSuggestionListResponse result = vocalService.suggest(" mik ");

        assertThat(result.items()).hasSize(10);
        assertThat(result.items().getFirst().resourceUuid()).isEqualTo(duplicatedUuid);
        assertThat(result.items().getFirst().name()).isEqualTo("Hatsune Miku");
        assertThat(result.items()).extracting(item -> item.resourceUuid()).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Suggest should return empty list when query is blank")
    void suggest_withBlankQuery_shouldReturnEmptyList() {
        VocalSuggestionListResponse result = vocalService.suggest("   ");

        assertThat(result.items()).isEmpty();
        verifyNoInteractions(resourceNameRepository);
    }

    @Test
    @DisplayName("Search should include localized name matching request locale")
    void search_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        Vocal vocal = vocal(1L, UUID.randomUUID(), "Hatsune Miku");
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(vocalRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(vocal), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        VocalListResponse result = vocalService.search(null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Hatsune Miku");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("初音ミク");
    }

    @Test
    @DisplayName("Search should return null localized name when request locale name is missing")
    void search_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        Vocal vocal = vocal(1L, UUID.randomUUID(), "Hatsune Miku");
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(vocalRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(vocal), PageRequest.of(0, 20), 1));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        VocalListResponse result = vocalService.search(null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Search should not query resource names when result is empty")
    void search_withEmptyResult_shouldSkipLocalizedNameLookup() {
        when(vocalRepository.search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()),
                        eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        VocalListResponse result = vocalService.search(null, null, null, PageRequest.of(0, 20));

        assertThat(result.items()).isEmpty();
        verify(vocalRepository)
                .search(
                        argThat(
                                criteria ->
                                        criteria.status() == null
                                                && criteria.query() == null
                                                && criteria.songUuids().isEmpty()),
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

    private Vocal vocal(Long resourceId, UUID resourceUuid, String canonicalName) {
        Resource resource = mock(Resource.class);
        when(resource.getId()).thenReturn(resourceId);
        when(resource.getUuid()).thenReturn(resourceUuid);
        when(resource.getCanonicalName()).thenReturn(canonicalName);
        when(resource.getStatus()).thenReturn(ResourceStatus.ACTIVE);
        when(resource.getViewCount()).thenReturn(0L);

        Vocal vocal = mock(Vocal.class);
        when(vocal.getResource()).thenReturn(resource);
        return vocal;
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
