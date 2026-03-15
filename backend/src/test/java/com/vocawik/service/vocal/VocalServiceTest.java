package com.vocawik.service.vocal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceStatus;
import com.vocawik.dto.vocal.VocalSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.vocal.VocalLinkRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocalServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private VocalService vocalService;

    @BeforeEach
    void setUp() {
        resourceNameRepository = mock(ResourceNameRepository.class);
        vocalService =
                new VocalService(
                        mock(VocalRepository.class),
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(VocalLinkRepository.class),
                        mock(AclRepository.class),
                        mock(ResourceHistoryService.class),
                        new ObjectMapper());
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

    private ResourceName candidate(UUID uuid, String name) {
        Resource resource = mock(Resource.class);
        when(resource.getUuid()).thenReturn(uuid);

        ResourceName resourceName = mock(ResourceName.class);
        when(resourceName.getResource()).thenReturn(resource);
        when(resourceName.getName()).thenReturn(name);
        return resourceName;
    }
}
