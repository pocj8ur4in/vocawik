package com.vocawik.module.web.locale;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

/**
 * Properties for request locale resolution.
 *
 * @param defaultLocale locale used when the request does not provide a supported locale
 * @param supported supported locales
 */
@ConfigurationProperties(prefix = "web.locale")
public record WebLocaleProperties(
        @Name("default") @DefaultValue("en") Locale defaultLocale,
        @DefaultValue({"ko", "en", "ja", "zh"}) List<Locale> supported) {

    /**
     * Creates an immutable locale policy for request resolution.
     *
     * @param defaultLocale locale used when a request has no supported locale
     * @param supported locales accepted from requests
     * @throws NullPointerException if the supported locale list or one of its elements is null
     */
    public WebLocaleProperties {
        supported = List.copyOf(supported);
    }
}
