package com.vocawik.module.web.i18n;

import com.vocawik.module.web.error.ErrorCode;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

/** Resolves localized API error messages from the active request locale. */
@Component
public class ErrorMessageResolver {

    private final MessageSource messageSource;

    /** Creates a resolver backed by the application's error message bundles. */
    @Autowired
    public ErrorMessageResolver() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        this.messageSource = source;
    }

    /**
     * Creates a resolver using the supplied message source.
     *
     * @param messageSource message source
     */
    public ErrorMessageResolver(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    /**
     * Resolves an error code for a locale.
     *
     * @param errorCode error code
     * @param locale requested locale
     * @return localized message, or the key when no translation exists
     */
    public String resolve(ErrorCode errorCode, Locale locale) {
        Objects.requireNonNull(errorCode, "errorCode");
        return messageSource.getMessage(
                errorCode.messageKey(), null, errorCode.messageKey(), locale);
    }
}
