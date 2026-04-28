package com.vocawik.module.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.ZoneId;

/** Converts IANA {@link ZoneId} values to their database string representation. */
@Converter
public class ZoneIdAttributeConverter implements AttributeConverter<ZoneId, String> {

    /** Creates a zone ID attribute converter. */
    public ZoneIdAttributeConverter() {}

    /**
     * Converts a zone ID to its IANA string representation.
     *
     * @param attribute zone ID from the entity
     * @return IANA zone ID, or {@code null} when the attribute is null
     */
    @Override
    public String convertToDatabaseColumn(ZoneId attribute) {
        return attribute == null ? null : attribute.getId();
    }

    /**
     * Converts an IANA string from the database to a zone ID.
     *
     * @param dbData IANA zone ID from the database
     * @return converted zone ID, or {@code null} for null or blank data
     * @throws java.time.DateTimeException when the value is not a valid zone ID
     */
    @Override
    public ZoneId convertToEntityAttribute(String dbData) {
        return dbData == null || dbData.isBlank() ? null : ZoneId.of(dbData);
    }
}
