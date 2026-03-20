package com.vocawik.service.debate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.vocawik.common.i18n.Language;
import com.vocawik.domain.debate.Debate;
import com.vocawik.domain.debate.DebateComment;
import com.vocawik.domain.debate.DebateStatus;
import com.vocawik.domain.guest.Guest;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.resource.ResourceType;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserPvProvider;
import com.vocawik.domain.user.UserRole;
import com.vocawik.domain.user.UserTheme;
import com.vocawik.dto.debate.DebateCommentCreateRequest;
import com.vocawik.dto.debate.DebateCommentUpdateRequest;
import com.vocawik.dto.debate.DebateCreateRequest;
import com.vocawik.dto.debate.DebateStatusUpdateRequest;
import com.vocawik.repository.debate.DebateCommentCountProjection;
import com.vocawik.repository.debate.DebateCommentRepository;
import com.vocawik.repository.debate.DebateRepository;
import com.vocawik.repository.guest.GuestRepository;
import com.vocawik.repository.resource.ResourceRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.exception.BusinessException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

class DebateServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("List should return mapped debates with user and guest authors")
    void listByResourceUuid_shouldReturnMappedDebates() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d0f6d-f6a5-71d9-bd8e-b1b87dc60851");
        Resource resource = createResource(11L, resourceUuid);
        Debate userDebate =
                createUserDebate(
                        101L,
                        UUID.fromString("019d0f6d-f839-7433-97b9-a078d16cd8f8"),
                        resource,
                        "PV naming",
                        UUID.fromString("019d2050-7d23-7d71-ab56-c8884ff95db2"),
                        "testuser",
                        LocalDateTime.of(2026, 3, 21, 10, 30));
        Debate guestDebate =
                createGuestDebate(
                        102L,
                        UUID.fromString("019d0f6d-f994-799d-a936-5e4a5213552a"),
                        resource,
                        "Old alias cleanup",
                        UUID.fromString("019d2050-7dc7-732f-ba26-a8e53fe33cf7"),
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
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

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
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d0f6e-1fb2-7e30-9338-be1547495282");
        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> debateService.listByResourceUuid(resourceUuid))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Resource not found.");
        verifyNoInteractions(debateRepository, debateCommentRepository);
    }

    @Test
    @DisplayName("Detail should return first comment as body and remaining comments as replies")
    void getByResourceUuidAndDebateUuid_shouldReturnBodyAndReplies() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d20a4-5de0-7135-a6d0-e33068b4d9ff");
        UUID debateUuid = UUID.fromString("019d20a4-5fd0-7e62-b90a-0d8f09ec5ea4");
        UUID authorUuid = UUID.fromString("019d20a4-60f2-7f85-8d88-7f9997b3159b");
        UUID guestUuid = UUID.fromString("019d20a4-6152-75ec-a355-739381262b31");
        Debate debate =
                createUserDebate(
                        111L,
                        debateUuid,
                        createResource(11L, resourceUuid),
                        "PV naming",
                        authorUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 9, 0));
        DebateComment body =
                createUserComment(
                        211L,
                        UUID.fromString("019d20a4-61b8-713f-a087-8dd3120e9bad"),
                        debate,
                        null,
                        authorUuid,
                        "author",
                        "This is the main thread content.",
                        LocalDateTime.of(2026, 3, 21, 9, 0));
        DebateComment reply =
                createGuestComment(
                        212L,
                        UUID.fromString("019d20a4-6215-7ac0-8211-57be6d41015e"),
                        debate,
                        null,
                        guestUuid,
                        "This is a reply.",
                        LocalDateTime.of(2026, 3, 21, 9, 5));
        DebateComment nestedReply =
                createUserComment(
                        213L,
                        UUID.fromString("019d20a4-6270-7f17-ab74-c9a44c1a1f0c"),
                        debate,
                        reply,
                        authorUuid,
                        "author",
                        "This is a nested reply.",
                        LocalDateTime.of(2026, 3, 21, 9, 10));

        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        when(debateCommentRepository.findAllByDebateIdOrderByCreatedAtAscIdAsc(111L))
                .thenReturn(List.of(body, reply, nestedReply));

        var response = debateService.getByResourceUuidAndDebateUuid(resourceUuid, debateUuid);

        assertThat(response.title()).isEqualTo("PV naming");
        assertThat(response.body()).isNotNull();
        assertThat(response.body().commentUuid()).isEqualTo(body.getUuid());
        assertThat(response.body().content()).isEqualTo("This is the main thread content.");
        assertThat(response.comments()).hasSize(2);
        assertThat(response.comments().get(0).commentUuid()).isEqualTo(reply.getUuid());
        assertThat(response.comments().get(0).parentCommentUuid()).isNull();
        assertThat(response.comments().get(0).authorName()).isEqualTo("Guest");
        assertThat(response.comments().get(1).commentUuid()).isEqualTo(nestedReply.getUuid());
        assertThat(response.comments().get(1).parentCommentUuid()).isEqualTo(reply.getUuid());
        assertThat(response.comments().get(1).authorName()).isEqualTo("author");
    }

    @Test
    @DisplayName("Create should persist debate and first comment for an authenticated user")
    void create_shouldPersistDebateAndFirstCommentForUser() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d16a0-60d9-7a13-b5c8-59bf1c0b29b0");
        UUID userUuid = UUID.fromString("019d16a0-627d-7e2f-9b8b-a13d574bfb77");
        Resource resource = createResource(31L, resourceUuid);
        User user =
                User.create(
                        "testuser@vocawik.test",
                        "testuser",
                        Language.KO,
                        ZoneId.of("Asia/Seoul"),
                        UserTheme.LIGHT,
                        UserPvProvider.YOUTUBE,
                        UserRole.USER);
        ReflectionTestUtils.setField(user, "uuid", userUuid);
        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.of(resource));
        when(userRepository.findByUuidAndIsDeletedFalse(eq(userUuid)))
                .thenReturn(Optional.of(user));
        when(debateRepository.save(org.mockito.ArgumentMatchers.any(Debate.class)))
                .thenAnswer(
                        invocation -> {
                            Debate saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 301L);
                            ReflectionTestUtils.setField(
                                    saved,
                                    "uuid",
                                    UUID.fromString("019d16a0-6346-74fa-bb75-13b5e5a68efd"));
                            ReflectionTestUtils.setField(
                                    saved, "createdAt", LocalDateTime.of(2026, 3, 21, 19, 30));
                            return saved;
                        });
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        var response =
                debateService.create(
                        resourceUuid,
                        new DebateCreateRequest("PV naming", "This is the first comment.", null));

        assertThat(response.title()).isEqualTo("PV naming");
        assertThat(response.authorName()).isEqualTo("testuser");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.commentCount()).isEqualTo(1L);
        verify(debateCommentRepository)
                .save(
                        org.mockito.ArgumentMatchers.any(
                                com.vocawik.domain.debate.DebateComment.class));
    }

    @Test
    @DisplayName("Create should persist debate and first comment for a guest")
    void create_shouldPersistDebateAndFirstCommentForGuest() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d16a0-63ad-72bd-aab5-4bdcaef06cda");
        UUID guestUuid = UUID.fromString("019d16a0-6404-7794-b410-26d772b53903");
        Resource resource = createResource(41L, resourceUuid);
        Guest guest = Guest.create("127.0.0.1");
        ReflectionTestUtils.setField(guest, "uuid", guestUuid);
        when(resourceRepository.findByUuidAndIsDeletedFalse(eq(resourceUuid)))
                .thenReturn(Optional.of(resource));
        when(guestRepository.findByUuidAndIsDeletedFalse(eq(guestUuid)))
                .thenReturn(Optional.of(guest));
        when(debateRepository.save(org.mockito.ArgumentMatchers.any(Debate.class)))
                .thenAnswer(
                        invocation -> {
                            Debate saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 401L);
                            ReflectionTestUtils.setField(
                                    saved,
                                    "uuid",
                                    UUID.fromString("019d16a0-6453-7081-a428-f9c859c29ff7"));
                            ReflectionTestUtils.setField(
                                    saved, "createdAt", LocalDateTime.of(2026, 3, 21, 19, 45));
                            return saved;
                        });
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new GuestPrincipal(guestUuid), null));

        var response =
                debateService.create(
                        resourceUuid, new DebateCreateRequest("Guest topic", "Guest body", null));

        assertThat(response.title()).isEqualTo("Guest topic");
        assertThat(response.authorName()).isEqualTo("Guest");
        assertThat(response.commentCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Create comment should persist reply for an authenticated user")
    void createComment_shouldPersistReplyForUser() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d217f-11ea-7db3-b21e-2adf1e3e61c8");
        UUID debateUuid = UUID.fromString("019d217f-1279-7b15-ba08-34c0c575fd7c");
        UUID userUuid = UUID.fromString("019d217f-12df-7e2e-92df-55b85d2bb9ed");
        UUID parentUuid = UUID.fromString("019d217f-133f-79b1-9f6f-f26a80f70fef");
        Debate debate =
                createUserDebate(
                        901L,
                        debateUuid,
                        createResource(91L, resourceUuid),
                        "PV",
                        userUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 21, 0));
        DebateComment parent =
                createUserComment(
                        902L,
                        parentUuid,
                        debate,
                        null,
                        userUuid,
                        "author",
                        "body",
                        LocalDateTime.of(2026, 3, 21, 21, 0));
        User user =
                User.create(
                        "author@vocawik.test",
                        "author",
                        Language.KO,
                        ZoneId.of("Asia/Seoul"),
                        UserTheme.LIGHT,
                        UserPvProvider.YOUTUBE,
                        UserRole.USER);
        ReflectionTestUtils.setField(user, "uuid", userUuid);
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        when(userRepository.findByUuidAndIsDeletedFalse(eq(userUuid)))
                .thenReturn(Optional.of(user));
        when(debateCommentRepository.findByUuidAndDebateIdAndIsDeletedFalse(
                        eq(parentUuid), eq(901L)))
                .thenReturn(Optional.of(parent));
        when(debateCommentRepository.save(org.mockito.ArgumentMatchers.any(DebateComment.class)))
                .thenAnswer(
                        invocation -> {
                            DebateComment saved = invocation.getArgument(0);
                            ReflectionTestUtils.setField(saved, "id", 903L);
                            ReflectionTestUtils.setField(
                                    saved,
                                    "uuid",
                                    UUID.fromString("019d217f-139d-7fdc-a883-e5f143dc5e31"));
                            ReflectionTestUtils.setField(
                                    saved, "createdAt", LocalDateTime.of(2026, 3, 21, 21, 5));
                            ReflectionTestUtils.setField(
                                    saved, "updatedAt", LocalDateTime.of(2026, 3, 21, 21, 5));
                            return saved;
                        });
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        var response =
                debateService.createComment(
                        resourceUuid,
                        debateUuid,
                        new DebateCommentCreateRequest("reply", parentUuid, null));

        assertThat(response.parentCommentUuid()).isEqualTo(parentUuid);
        assertThat(response.authorName()).isEqualTo("author");
        assertThat(response.content()).isEqualTo("reply");
        assertThat(response.revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("Update comment should revise content for its author")
    void updateComment_shouldReviseForAuthor() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d217f-13fb-7ad6-b456-0b3f4a55cd60");
        UUID debateUuid = UUID.fromString("019d217f-1457-7fe5-b0db-f2ab9ddd42b7");
        UUID commentUuid = UUID.fromString("019d217f-14aa-7d7f-9d48-82f7f63b10c1");
        UUID userUuid = UUID.fromString("019d217f-1503-7cde-a3e5-5d39493912fc");
        Debate debate =
                createUserDebate(
                        1001L,
                        debateUuid,
                        createResource(101L, resourceUuid),
                        "PV",
                        userUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 21, 10));
        DebateComment comment =
                createUserComment(
                        1002L,
                        commentUuid,
                        debate,
                        null,
                        userUuid,
                        "author",
                        "before",
                        LocalDateTime.of(2026, 3, 21, 21, 10));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        when(debateCommentRepository.findByUuidAndDebateIdAndIsDeletedFalse(
                        eq(commentUuid), eq(1001L)))
                .thenReturn(Optional.of(comment));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        var response =
                debateService.updateComment(
                        resourceUuid,
                        debateUuid,
                        commentUuid,
                        new DebateCommentUpdateRequest("after", null));

        assertThat(response.content()).isEqualTo("after");
        assertThat(response.revision()).isEqualTo(2);
    }

    @Test
    @DisplayName("Delete comment should soft delete for its guest author")
    void deleteComment_shouldSoftDeleteForGuestAuthor() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d217f-155e-7899-8a66-c8650e9d2867");
        UUID debateUuid = UUID.fromString("019d217f-15b8-7353-b3f6-cdd33de97da4");
        UUID commentUuid = UUID.fromString("019d217f-160e-735c-92c3-99ded2c1eb7a");
        UUID guestUuid = UUID.fromString("019d217f-1662-7552-a9ef-3d349cfbbf2b");
        Debate debate =
                createGuestDebate(
                        1101L,
                        debateUuid,
                        createResource(111L, resourceUuid),
                        "PV",
                        guestUuid,
                        LocalDateTime.of(2026, 3, 21, 21, 20));
        DebateComment comment =
                createGuestComment(
                        1102L,
                        commentUuid,
                        debate,
                        null,
                        guestUuid,
                        "guest comment",
                        LocalDateTime.of(2026, 3, 21, 21, 20));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        when(debateCommentRepository.findByUuidAndDebateIdAndIsDeletedFalse(
                        eq(commentUuid), eq(1101L)))
                .thenReturn(Optional.of(comment));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new GuestPrincipal(guestUuid), null));

        debateService.deleteComment(resourceUuid, debateUuid, commentUuid);

        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Delete should soft delete debate for its author")
    void delete_shouldSoftDeleteForAuthor() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d2050-7e6a-79ee-a538-c598e6f1f650");
        UUID debateUuid = UUID.fromString("019d2050-7f04-71b1-97dd-d09310af6ac5");
        UUID userUuid = UUID.fromString("019d2050-7f59-733c-b0e1-eecbcb4deeb3");
        Debate debate =
                createUserDebate(
                        501L,
                        debateUuid,
                        createResource(51L, resourceUuid),
                        "PV",
                        userUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 20, 0));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        debateService.delete(resourceUuid, debateUuid);

        assertThat(debate.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Delete should allow admin")
    void delete_shouldAllowAdmin() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d2050-8007-7fef-841d-4f48d5e23952");
        UUID debateUuid = UUID.fromString("019d2050-805c-7cdd-a7df-59c590016eb0");
        UUID authorUuid = UUID.fromString("019d2050-80ad-792c-b43c-c41f0a7363d7");
        UUID adminUuid = UUID.fromString("019d2050-80f9-7f2b-b812-6cbc221bdc66");
        Debate debate =
                createUserDebate(
                        601L,
                        debateUuid,
                        createResource(61L, resourceUuid),
                        "PV",
                        authorUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 20, 5));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(adminUuid, UserRole.ADMIN.name()), null));

        debateService.delete(resourceUuid, debateUuid);

        assertThat(debate.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Delete should fail for a different user")
    void delete_shouldFailForDifferentUser() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d2050-8149-7c88-b5bd-dd2b0c997f2a");
        UUID debateUuid = UUID.fromString("019d2050-8198-7903-8f99-c7de8f268e3f");
        UUID authorUuid = UUID.fromString("019d2050-81e6-79a1-8a36-e18e82ef4f79");
        UUID otherUserUuid = UUID.fromString("019d2050-8231-788f-b38f-89dca61c9498");
        Debate debate =
                createUserDebate(
                        701L,
                        debateUuid,
                        createResource(71L, resourceUuid),
                        "PV",
                        authorUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 20, 10));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(otherUserUuid, UserRole.USER.name()), null));

        assertThatThrownBy(() -> debateService.delete(resourceUuid, debateUuid))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Access denied.");
        assertThat(debate.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Delete should soft delete debate for its guest author")
    void delete_shouldSoftDeleteForGuestAuthor() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d2050-827c-74be-befd-0c79843bfba1");
        UUID debateUuid = UUID.fromString("019d2050-82d1-7723-adad-309a6a5398bb");
        UUID guestUuid = UUID.fromString("019d2050-8323-7b31-a340-2c1a5a0ca938");
        Debate debate =
                createGuestDebate(
                        801L,
                        debateUuid,
                        createResource(81L, resourceUuid),
                        "PV",
                        guestUuid,
                        LocalDateTime.of(2026, 3, 21, 20, 15));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new GuestPrincipal(guestUuid), null));

        debateService.delete(resourceUuid, debateUuid);

        assertThat(debate.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("Update status should close debate for its author")
    void updateStatus_shouldCloseDebateForAuthor() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d24a8-76fc-711f-afde-4bf92e332eb5");
        UUID debateUuid = UUID.fromString("019d24a8-7775-7e8f-bc42-aef4bd5608df");
        UUID userUuid = UUID.fromString("019d24a8-77cf-7188-a3d8-aebc555ecce5");
        Debate debate =
                createUserDebate(
                        1201L,
                        debateUuid,
                        createResource(121L, resourceUuid),
                        "PV",
                        userUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 22, 0));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        var response =
                debateService.updateStatus(
                        resourceUuid, debateUuid, new DebateStatusUpdateRequest("CLOSED", null));

        assertThat(response.debateUuid()).isEqualTo(debateUuid);
        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(debate.getStatus()).isEqualTo(DebateStatus.CLOSED);
    }

    @Test
    @DisplayName("Update status should reopen debate for admin")
    void updateStatus_shouldReopenDebateForAdmin() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d24a8-7824-7d0d-ad77-9d32efa0b4ae");
        UUID debateUuid = UUID.fromString("019d24a8-7874-74c5-b0f2-f3b20f7d69b0");
        UUID authorUuid = UUID.fromString("019d24a8-78c0-78e2-b432-499817ec59f7");
        UUID adminUuid = UUID.fromString("019d24a8-790d-7c99-b11a-4416f3b2a9da");
        Debate debate =
                createUserDebate(
                        1301L,
                        debateUuid,
                        createResource(131L, resourceUuid),
                        "PV",
                        authorUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 22, 10));
        debate.close();
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(adminUuid, UserRole.ADMIN.name()), null));

        var response =
                debateService.updateStatus(
                        resourceUuid, debateUuid, new DebateStatusUpdateRequest("OPEN", null));

        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(debate.getStatus()).isEqualTo(DebateStatus.OPEN);
    }

    @Test
    @DisplayName("Update status should reject archived status")
    void updateStatus_shouldRejectArchivedStatus() {
        ResourceRepository resourceRepository = mock(ResourceRepository.class);
        DebateRepository debateRepository = mock(DebateRepository.class);
        DebateCommentRepository debateCommentRepository = mock(DebateCommentRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestRepository guestRepository = mock(GuestRepository.class);
        DebateService debateService =
                new DebateService(
                        resourceRepository,
                        debateRepository,
                        debateCommentRepository,
                        userRepository,
                        guestRepository);

        UUID resourceUuid = UUID.fromString("019d24a8-7961-7e64-b287-1302a8da5ef0");
        UUID debateUuid = UUID.fromString("019d24a8-79bb-7638-b1c2-169d437b08f1");
        UUID userUuid = UUID.fromString("019d24a8-7a0f-764a-b1e2-848bf025d5a7");
        Debate debate =
                createUserDebate(
                        1401L,
                        debateUuid,
                        createResource(141L, resourceUuid),
                        "PV",
                        userUuid,
                        "author",
                        LocalDateTime.of(2026, 3, 21, 22, 20));
        when(debateRepository.findByUuidAndResourceUuidAndIsDeletedFalse(
                        eq(debateUuid), eq(resourceUuid)))
                .thenReturn(Optional.of(debate));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                new AuthPrincipal(userUuid, UserRole.USER.name()), null));

        assertThatThrownBy(
                        () ->
                                debateService.updateStatus(
                                        resourceUuid,
                                        debateUuid,
                                        new DebateStatusUpdateRequest("ARCHIVED", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid input.");
        assertThat(debate.getStatus()).isEqualTo(DebateStatus.OPEN);
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
            UUID userUuid,
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
        ReflectionTestUtils.setField(user, "uuid", userUuid);
        Debate debate = Debate.create(resource, user, null, title);
        ReflectionTestUtils.setField(debate, "id", id);
        ReflectionTestUtils.setField(debate, "uuid", uuid);
        ReflectionTestUtils.setField(debate, "createdAt", createdAt);
        return debate;
    }

    private Debate createGuestDebate(
            Long id,
            UUID uuid,
            Resource resource,
            String title,
            UUID guestUuid,
            LocalDateTime createdAt) {
        Guest guest = Guest.create("127.0.0.1");
        ReflectionTestUtils.setField(guest, "uuid", guestUuid);
        Debate debate = Debate.create(resource, null, guest, title);
        ReflectionTestUtils.setField(debate, "id", id);
        ReflectionTestUtils.setField(debate, "uuid", uuid);
        ReflectionTestUtils.setField(debate, "createdAt", createdAt);
        return debate;
    }

    private DebateComment createUserComment(
            Long id,
            UUID uuid,
            Debate debate,
            DebateComment parentComment,
            UUID userUuid,
            String nickname,
            String content,
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
        ReflectionTestUtils.setField(user, "uuid", userUuid);
        DebateComment comment = DebateComment.create(debate, parentComment, user, null, content);
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "uuid", uuid);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        ReflectionTestUtils.setField(comment, "updatedAt", createdAt);
        return comment;
    }

    private DebateComment createGuestComment(
            Long id,
            UUID uuid,
            Debate debate,
            DebateComment parentComment,
            UUID guestUuid,
            String content,
            LocalDateTime createdAt) {
        Guest guest = Guest.create("127.0.0.1");
        ReflectionTestUtils.setField(guest, "uuid", guestUuid);
        DebateComment comment = DebateComment.create(debate, parentComment, null, guest, content);
        ReflectionTestUtils.setField(comment, "id", id);
        ReflectionTestUtils.setField(comment, "uuid", uuid);
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        ReflectionTestUtils.setField(comment, "updatedAt", createdAt);
        return comment;
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
