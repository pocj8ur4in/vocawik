package com.vocawik.module.web.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.module.web.error.ErrorCode;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ErrorMessageResolverTest {

    private final ErrorMessageResolver resolver = new ErrorMessageResolver();

    @Test
    @DisplayName("Should resolve an error message using the requested locale")
    void resolve_withKoreanLocale_shouldReturnKoreanMessage() {
        assertThat(resolver.resolve(ErrorCode.BAD_REQUEST, Locale.KOREAN)).isEqualTo("잘못된 요청입니다.");
    }

    @Test
    @DisplayName("Should fall back to the message key when a translation is missing")
    void resolve_withMissingTranslation_shouldReturnMessageKey() {
        assertThat(resolver.resolve(ErrorCode.BAD_REQUEST, Locale.FRENCH))
                .isEqualTo("Invalid request.");
    }
}
