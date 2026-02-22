package com.vocawik.common.jpa.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Converts {@link Locale} to and from BCP 47 language-tag strings. */
@Converter
public class LocaleAttributeConverter implements AttributeConverter<Locale, String> {

    /**
     * Converts a {@link Locale} to a BCP 47 language tag for DB storage.
     *
     * @param attribute locale value from entity
     * @return language tag (e.g. {@code ko-KR}), or {@code null} if input is null
     */
    @Override
    public String convertToDatabaseColumn(Locale attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.toLanguageTag();
    }

    /**
     * Converts a DB language-tag value back to a {@link Locale}.
     *
     * @param dbData BCP 47 language tag from DB column
     * @return converted locale, or {@code null} when DB value is blank
     */
    @Override
    public Locale convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return Locale.forLanguageTag(dbData);
    }
}
