package com.vocawik.common.jpa.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.ZoneId;

/** Converts {@link ZoneId} to and from IANA time-zone strings. */
@Converter
public class ZoneIdAttributeConverter implements AttributeConverter<ZoneId, String> {

    /**
     * Converts a {@link ZoneId} to an IANA zone-id string for DB storage.
     *
     * @param attribute zone value from entity
     * @return zone id (e.g. {@code Asia/Seoul}), or {@code null} if input is null
     */
    @Override
    public String convertToDatabaseColumn(ZoneId attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getId();
    }

    /**
     * Converts a DB zone-id string back to {@link ZoneId}.
     *
     * @param dbData IANA zone-id string from DB column
     * @return converted zone id, or {@code null} when DB value is blank
     */
    @Override
    public ZoneId convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        return ZoneId.of(dbData);
    }
}
