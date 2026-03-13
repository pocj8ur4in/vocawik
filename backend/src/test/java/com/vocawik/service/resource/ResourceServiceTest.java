package com.vocawik.service.resource;

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
import com.vocawik.dto.resource.ResourceSuggestionListResponse;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.artist.ArtistGroupRepository;
import com.vocawik.repository.artist.ArtistRepository;
import com.vocawik.repository.playlist.PlaylistRepository;
import com.vocawik.repository.playlist.PlaylistSongRepository;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.song.SongArtistRepository;
import com.vocawik.repository.song.SongLyricRepository;
import com.vocawik.repository.song.SongPvRepository;
import com.vocawik.repository.song.SongPvViewRepository;
import com.vocawik.repository.song.SongRelationRepository;
import com.vocawik.repository.song.SongRepository;
import com.vocawik.repository.song.SongVocalRepository;
import com.vocawik.repository.vocal.VocalRepository;
import com.vocawik.service.history.ResourceHistoryService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResourceServiceTest {

    private ResourceNameRepository resourceNameRepository;
    private ResourceService resourceService;

    @BeforeEach
    void setUp() {
        resourceNameRepository = mock(ResourceNameRepository.class);
        resourceService =
                new ResourceService(
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(AclRepository.class),
                        mock(SongRepository.class),
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
                        mock(VocalRepository.class),
                        mock(ResourceHistoryService.class),
                        mock(ResourcePopularityService.class),
                        new ObjectMapper());
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

    private ResourceName candidate(UUID uuid, String name) {
        Resource resource = mock(Resource.class);
        when(resource.getUuid()).thenReturn(uuid);

        ResourceName resourceName = mock(ResourceName.class);
        when(resourceName.getResource()).thenReturn(resource);
        when(resourceName.getName()).thenReturn(name);
        return resourceName;
    }
}
