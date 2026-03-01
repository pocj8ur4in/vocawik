package com.vocawik.common.i18n;

import com.vocawik.domain.user.User;
import com.vocawik.repository.user.UserRepository;
import com.vocawik.security.jwt.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves request locale by authenticated user setting or Accept-Language header. */
@Component
@RequiredArgsConstructor
public class I18nService {

    private static final List<Locale> SUPPORTED_LOCALES =
            Arrays.stream(Language.values())
                    .filter(language -> !Language.UND.equals(language))
                    .filter(language -> !Language.LA.equals(language))
                    .map(I18nService::toLocale)
                    .toList();

    private final UserRepository userRepository;

    /**
     * Resolves effective locale for current request.
     *
     * @param request current HTTP request
     * @return resolved locale
     */
    public Locale resolve(HttpServletRequest request) {
        Locale userLocale = resolveFromAuthenticatedUser();
        if (userLocale != null) {
            return userLocale;
        }

        Locale headerLocale = resolveFromAcceptLanguage(request.getHeader("Accept-Language"));
        if (headerLocale != null) {
            return headerLocale;
        }

        return Locale.ENGLISH;
    }

    private Locale resolveFromAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof AuthPrincipal authPrincipal)) {
            return null;
        }

        return userRepository
                .findByUuidAndIsDeletedFalse(authPrincipal.userUuid())
                .map(User::getLangCode)
                .filter(language -> !Language.UND.equals(language))
                .map(I18nService::toLocale)
                .orElse(null);
    }

    private Locale resolveFromAcceptLanguage(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(headerValue);
            Locale matched = Locale.lookup(ranges, SUPPORTED_LOCALES);
            return matched == null ? null : matched;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Locale toLocale(Language language) {
        return switch (language) {
            case KO -> Locale.KOREAN;
            case EN -> Locale.ENGLISH;
            case JA -> Locale.JAPANESE;
            case ZH -> Locale.CHINESE;
            default ->
                    throw new IllegalArgumentException(language + " cannot map to fixed Locale.");
        };
    }
}
