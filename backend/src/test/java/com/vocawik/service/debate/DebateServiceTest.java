package com.vocawik.service.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateStatus;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserPvProvider;
import com.vocawik.domain.user.UserRole;
import com.vocawik.domain.user.UserTheme;
import com.vocawik.repository.debate.DebateCommentCountProjection;
import com.vocawik.repository.debate.DebateCommentRepository;
import com.vocawik.repository.debate.DebateRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.web.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DebateServiceTest {

    @Test
    @DisplayName("List should return mapped debates with user and guest authors")
    void listByResourceUuid_shouldReturnMappedDebates() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        DebateService debateService =
                new DebateService(resourceRepository, debateRepository, debateCommentRepository);

        UUID resourceUuid = UUID.fromString("019d0f6d-f6a5-71d9-bd8e-b1b87dc60851");
        Resource resource = createResource(11L, resourceUuid);
        Debate userDebate =
                createUserDebate(
                        101L,
                        UUID.fromString("019d0f6d-f839-7433-97b9-a078d16cd8f8"),
                        resource,
                        "PV naming",
                        "testuser",
                        LocalDateTime.of(2026, 3, 21, 10, 30));
        Debate guestDebate =
                createGuestDebate(
                        102L,
                        UUID.fromString("019d0f6d-f994-799d-a936-5e4a5213552a"),
                        resource,
                        "Old alias cleanup",
                        LocalDateTime.of(2026, 3, 20, 9, 0));

        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.of(resource));
        when(debateRepository
                        .findAllByResourceIdAndIsDeletedFalseAndStatusNotOrderByCreatedAtDescIdDesc(
                                eq(11L), eq(DebateStatus.ARCHIVED)))
                .thenReturn(List.of(userDebate, guestDebate));
        when(debateCommentRepository.countActiveCommentsByDebateIds(List.of(101L, 102L)))
                .thenReturn(List.of(commentCount(101L, 3L), commentCount(102L, 1L)));

        var response = debateService.listByResourceUuid(resourceUuid);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).debateUuid()).isEqualTo(userDebate.getUuid());
        assertThat(response.items().get(0).title()).isEqualTo("PV naming");
        assertThat(response.items().get(0).authorName()).isEqualTo("testuser");
        assertThat(response.items().get(0).status()).isEqualTo("OPEN");
        assertThat(response.items().get(0).commentCount()).isEqualTo(3L);
        assertThat(response.items().get(1).authorName()).isEqualTo("Guest");
        assertThat(response.items().get(1).commentCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("List should return empty items when resource has no debates")
    void listByResourceUuid_shouldReturnEmptyItems() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        DebateService debateService =
                new DebateService(resourceRepository, debateRepository, debateCommentRepository);

        UUID resourceUuid = UUID.fromString("019d0f6e-10f6-7667-abcc-879987ecf48f");
        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.of(createResource(21L, resourceUuid)));
        when(debateRepository
                        .findAllByResourceIdAndIsDeletedFalseAndStatusNotOrderByCreatedAtDescIdDesc(
                                eq(21L), eq(DebateStatus.ARCHIVED)))
                .thenReturn(List.of());

        var response = debateService.listByResourceUuid(resourceUuid);

        assertThat(response.items()).isEmpty();
        verifyNoInteractions(debateCommentRepository);
    }

    @Test
    @DisplayName("List should fail when resource does not exist")
    void listByResourceUuid_shouldThrowWhenResourceMissing() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        DebateService debateService =
                new DebateService(resourceRepository, debateRepository, debateCommentRepository);

        UUID resourceUuid = UUID.fromString("019d0f6e-1fb2-7e30-9338-be1547495282");
        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> debateService.listByResourceUuid(resourceUuid))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Resource not found.");
        verifyNoInteractions(debateRepository, debateCommentRepository);
    }

    private Resource createResource(Long id, UUID uuid) {
        Resource resource = Resource.create(ResourceType.SONG, "Mesmerizer", null);
        ReflectionTestUtils.setField(resource, "id", id);
        ReflectionTestUtils.setField(resource, "uuid", uuid);
        return resource;
    }

    private Debate createUserDebate(
            Long id,
            UUID uuid,
            Resource resource,
            String title,
            String nickname,
            LocalDateTime createdAt) {
        User user =
                User.create(
                        nickname.toLowerCase() + "@vocawik.test",
                        nickname,
                        Language.KO,
                        ZoneId.of("Asia/Seoul"),
                        UserTheme.LIGHT,
                        UserPvProvider.YOUTUBE,
                        UserRole.USER);
        Debate debate = Debate.create(resource, user, null, title);
        ReflectionTestUtils.setField(debate, "id", id);
        ReflectionTestUtils.setField(debate, "uuid", uuid);
        ReflectionTestUtils.setField(debate, "createdAt", createdAt);
        return debate;
    }

    private Debate createGuestDebate(
            Long id, UUID uuid, Resource resource, String title, LocalDateTime createdAt) {
        Guest guest = Guest.create("127.0.0.1");
        Debate debate = Debate.create(resource, null, guest, title);
        ReflectionTestUtils.setField(debate, "id", id);
        ReflectionTestUtils.setField(debate, "uuid", uuid);
        ReflectionTestUtils.setField(debate, "createdAt", createdAt);
        return debate;
    }

    private DebateCommentCountProjection commentCount(Long debateId, long commentCount) {
        return new DebateCommentCountProjection() {
            @Override
            public Long getDebateId() {
                return debateId;
            }

            @Override
            public long getCommentCount() {
                return commentCount;
            }
        };
    }
}
