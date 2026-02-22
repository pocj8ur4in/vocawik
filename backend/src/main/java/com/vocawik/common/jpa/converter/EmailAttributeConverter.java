package com.vocawik.common.jpa.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Normalizes email values before persisting to DB. */
@Converter
public class EmailAttributeConverter implements AttributeConverter<String, String> {

    /**
     * Converts an value to a normalized DB representation.
     *
     * @param attribute email value from entity
     * @return normalized email for DB column, or {@code null} if input is null
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Converts a DB email value back to an entity attribute.
     *
     * @param dbData email value from DB column
     * @return same email value without additional transformation
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }
}
