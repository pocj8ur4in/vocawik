package com.vocawik.module.persistence.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class EmailAttributeConverterTest {

    private final EmailAttributeConverter converter = new EmailAttributeConverter();

    @Test
    void convertToDatabaseColumn_shouldTrimAndLowercaseWithRootLocale() {
        assertThat(converter.convertToDatabaseColumn("  Alice@EXAMPLE.COM  "))
                .isEqualTo("alice@example.com");
    }

    @Test
    void convertToDatabaseColumn_shouldUseRootLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(converter.convertToDatabaseColumn(" I@EXAMPLE.COM "))
                    .isEqualTo("i@example.com");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void convertToDatabaseColumn_shouldPreserveNullAndNormalizeEmptyValues() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToDatabaseColumn("")).isEmpty();
        assertThat(converter.convertToDatabaseColumn("   ")).isEmpty();
    }

    @Test
    void convertToEntityAttribute_shouldReturnDatabaseValueUnchanged() {
        String databaseValue = "  Mixed@Example.COM  ";

        assertThat(converter.convertToEntityAttribute(databaseValue)).isSameAs(databaseValue);
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
