package com.vocawik.module.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ZoneIdAttributeConverterTest {

    private final ZoneIdAttributeConverter converter = new ZoneIdAttributeConverter();

    @Test
    @DisplayName("Should convert a zone ID to its IANA database value")
    void convertToDatabaseColumn_shouldReturnIanaId() {
        assertThat(converter.convertToDatabaseColumn(ZoneId.of("Asia/Seoul")))
                .isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("Should convert an IANA database value to a zone ID")
    void convertToEntityAttribute_shouldReturnZoneId() {
        assertThat(converter.convertToEntityAttribute("Asia/Seoul"))
                .isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("Should convert null and blank values to null")
    void conversion_shouldReturnNullForEmptyValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
        assertThat(converter.convertToEntityAttribute("  ")).isNull();
    }

    @Test
    @DisplayName("Should reject an invalid zone ID from the database")
    void convertToEntityAttribute_shouldRejectInvalidZoneId() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("Not/AZone"))
                .isInstanceOf(java.time.DateTimeException.class);
    }
}
