package com.vocawik.module.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** Converts explicitly mapped email attributes to their normalized database value. */
@Converter(autoApply = false)
public class EmailAttributeConverter implements AttributeConverter<String, String> {

    /** Creates an email attribute converter. */
    public EmailAttributeConverter() {}

    /**
     * Normalizes an entity email before it is written to the database.
     *
     * @param attribute email value from the entity
     * @return the trimmed, root-locale lowercase email, or {@code null} when the attribute is null
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : attribute.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the database value without changing existing stored data on read.
     *
     * @param dbData email value from the database
     * @return the database value unchanged
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData;
    }
}
