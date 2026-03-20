package com.vocawik.service.acl;

import com.vocawik.domain.acl.Acl;
import com.vocawik.domain.acl.AclAction;
import com.vocawik.domain.acl.AclEffect;
import com.vocawik.domain.resource.Resource;
import com.vocawik.domain.user.User;
import com.vocawik.domain.user.UserRole;
import com.vocawik.repository.acl.AclRepository;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.guest.GuestPrincipal;
import com.vocawik.security.jwt.AuthPrincipal;
import com.vocawik.web.error.ErrorCode;
import com.vocawik.web.exception.BusinessException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Evaluates resource ACL permissions for the current request principal. */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Repositories are Spring-managed dependencies stored for internal use.")
public class AclPermissionService {

    private static final long USER_15_REQUIRED_DAYS = 15;

    private final AclRepository aclRepository;
    private final UserRepository userRepository;

    public void assertCanRead(Resource resource) {
        assertAllowed(resource, AclAction.READ);
    }

    public void assertCanEdit(Resource resource) {
        assertAllowed(resource, AclAction.EDIT);
    }

    public void assertCanDelete(Resource resource) {
        assertAllowed(resource, AclAction.DELETE);
    }

    public void assertCanManageAcl(Resource resource) {
        assertAllowed(resource, AclAction.ACL);
    }

    public void assertCanCreateDebate(Resource resource) {
        assertAllowed(resource, AclAction.DEBATE_CREATE);
    }

    public void assertCanCommentDebate(Resource resource) {
        assertAllowed(resource, AclAction.DEBATE_COMMENT);
    }

    public boolean isAllowed(Resource resource, AclAction action) {
        if (resource == null) {
            return false;
        }
        PrincipalContext principal = currentPrincipal();
        if (principal.isAdmin()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Acl> actionRules =
                aclRepository.findAllByResourceIdOrderByPriorityAscIdAsc(resource.getId()).stream()
                        .filter(acl -> acl.getAction() == action)
                        .filter(
                                acl ->
                                        acl.getExpiresAt() == null
                                                || acl.getExpiresAt().isAfter(now))
                        .toList();

        if (actionRules.isEmpty()) {
            return defaultAllows(action, principal);
        }

        for (Acl acl : actionRules) {
            if (matchesSubject(acl, principal)) {
                return acl.getEffect() == AclEffect.ALLOW;
            }
        }
        return false;
    }

    private void assertAllowed(Resource resource, AclAction action) {
        if (!isAllowed(resource, action)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean defaultAllows(AclAction action, PrincipalContext principal) {
        return switch (action) {
            case READ, EDIT, DEBATE_CREATE, DEBATE_COMMENT -> true;
            case DELETE, ACL -> principal.isAdmin();
            case EDIT_REQUEST -> false;
        };
    }

    private boolean matchesSubject(Acl acl, PrincipalContext principal) {
        return switch (acl.getSubjectType()) {
            case ANONYMOUS -> principal.isAnonymous();
            case USER -> principal.isUser();
            case USER_15 -> principal.isUser15();
            case USER_VERIFIED -> principal.isUserVerified();
            case ADMIN -> principal.isAdmin();
            case USER_ID ->
                    principal.userUuid() != null
                            && principal.userUuid().toString().equals(acl.getSubjectValue());
            case GUEST_ID ->
                    principal.guestUuid() != null
                            && principal.guestUuid().toString().equals(acl.getSubjectValue());
            case ACL_GROUP -> false;
        };
    }

    private PrincipalContext currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return PrincipalContext.anonymousContext();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthPrincipal authPrincipal) {
            boolean isAdmin = UserRole.ADMIN.name().equals(authPrincipal.role());
            User user =
                    userRepository
                            .findByUuidAndIsDeletedFalse(authPrincipal.userUuid())
                            .orElse(null);
            boolean isUser15 = isEligibleForUser15(user);
            boolean isUserVerified = isVerifiedUser(user);
            return new PrincipalContext(
                    authPrincipal.userUuid(), null, true, false, isAdmin, isUser15, isUserVerified);
        }
        if (principal instanceof GuestPrincipal guestPrincipal) {
            return new PrincipalContext(
                    null, guestPrincipal.guestUuid(), false, true, false, false, false);
        }
        return PrincipalContext.anonymousContext();
    }

    private record PrincipalContext(
            UUID userUuid,
            UUID guestUuid,
            boolean user,
            boolean anonymous,
            boolean admin,
            boolean user15,
            boolean userVerified) {

        private static PrincipalContext anonymousContext() {
            return new PrincipalContext(null, null, false, true, false, false, false);
        }

        private boolean isUser() {
            return user;
        }

        private boolean isAnonymous() {
            return anonymous;
        }

        private boolean isAdmin() {
            return admin;
        }

        private boolean isUser15() {
            return user15;
        }

        private boolean isUserVerified() {
            return userVerified;
        }
    }

    private boolean isEligibleForUser15(User user) {
        if (user == null || user.getCreatedAt() == null) {
            return false;
        }
        return !user.getCreatedAt().isAfter(LocalDateTime.now().minusDays(USER_15_REQUIRED_DAYS));
    }

    private boolean isVerifiedUser(User user) {
        return user != null && user.getEmailVerifiedAt() != null;
    }
}
