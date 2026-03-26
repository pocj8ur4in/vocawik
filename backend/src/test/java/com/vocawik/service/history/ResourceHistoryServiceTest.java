package com.vocawik.service.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocawik.common.i18n.Language;
import com.vocawik.domain.history.HistoryActionType;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceName;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.dto.history.RecentChangeListResponse;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.repository.history.HistoryRepository;
import com.vocawik.repository.history.RecentChangeProjection;
import com.vocawik.repository.resource.ResourceNameRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.service.acl.AclPermissionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;

class ResourceHistoryServiceTest {

    private HistoryRepository historyRepository;
    private ResourceNameRepository resourceNameRepository;
    private AclPermissionService aclPermissionService;
    private ResourceHistoryService resourceHistoryService;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.resetLocaleContext();
        historyRepository = mock(HistoryRepository.class);
        resourceNameRepository = mock(ResourceNameRepository.class);
        aclPermissionService = mock(AclPermissionService.class);
        when(aclPermissionService.isCurrentAdmin()).thenReturn(false);
        resourceHistoryService =
                new ResourceHistoryService(
                        historyRepository,
                        mock(ResourceRepository.class),
                        resourceNameRepository,
                        mock(UserRepository.class),
                        mock(GuestRepository.class),
                        aclPermissionService,
                        new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("Recent changes should include localized name matching request locale")
    void listRecentChanges_withMatchingLocale_shouldIncludeLocalizedName() {
        LocaleContextHolder.setLocale(Locale.JAPANESE);
        UUID resourceUuid = UUID.randomUUID();
        RecentChangeProjection projection =
                projection(
                        1L,
                        resourceUuid,
                        "Hatsune Miku",
                        ResourceType.VOCAL,
                        HistoryActionType.UPDATE);
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(historyRepository.findRecentVisibleChanges(eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(projection));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        RecentChangeListResponse result = resourceHistoryService.listRecentChanges(5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().canonicalName()).isEqualTo("Hatsune Miku");
        assertThat(result.items().getFirst().localizedName()).isEqualTo("初音ミク");
    }

    @Test
    @DisplayName(
            "Recent changes should return null localized name when request locale name is missing")
    void listRecentChanges_withoutMatchingLocale_shouldReturnNullLocalizedName() {
        LocaleContextHolder.setLocale(Locale.KOREAN);
        UUID resourceUuid = UUID.randomUUID();
        RecentChangeProjection projection =
                projection(
                        1L,
                        resourceUuid,
                        "Hatsune Miku",
                        ResourceType.VOCAL,
                        HistoryActionType.UPDATE);
        ResourceName japaneseName = localizedName(1L, "初音ミク", Language.JA);
        when(historyRepository.findRecentVisibleChanges(eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(projection));
        when(resourceNameRepository.findAllByResourceIdInOrderByResourceIdAscSortOrderAscIdAsc(
                        eq(List.of(1L))))
                .thenReturn(List.of(japaneseName));

        RecentChangeListResponse result = resourceHistoryService.listRecentChanges(5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
    }

    @Test
    @DisplayName("Recent changes should skip localized name lookup when locale is unsupported")
    void listRecentChanges_withUnsupportedLocale_shouldSkipLocalizedNameLookup() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("fr"));
        RecentChangeProjection projection =
                projection(
                        1L,
                        UUID.randomUUID(),
                        "Hatsune Miku",
                        ResourceType.VOCAL,
                        HistoryActionType.UPDATE);
        when(historyRepository.findRecentVisibleChanges(eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(projection));

        RecentChangeListResponse result = resourceHistoryService.listRecentChanges(5);

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().localizedName()).isNull();
        verifyNoInteractions(resourceNameRepository);
    }

    private RecentChangeProjection projection(
            Long resourceId,
            UUID resourceUuid,
            String canonicalName,
            ResourceType resourceType,
            HistoryActionType actionType) {
        RecentChangeProjection projection = mock(RecentChangeProjection.class);
        when(projection.getCreatedAt()).thenReturn(LocalDateTime.parse("2026-03-19T12:00:00"));
        when(projection.getResourceId()).thenReturn(resourceId);
        when(projection.getResourceUuid()).thenReturn(resourceUuid);
        when(projection.getCanonicalName()).thenReturn(canonicalName);
        when(projection.getResourceType()).thenReturn(resourceType);
        when(projection.getActionType()).thenReturn(actionType);
        when(projection.getActorUserNickname()).thenReturn("tester");
        return projection;
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
