package com.vocawik.dto.song;

import static org.assertj.core.api.Assertions.assertThat;

import com.vocawik.common.i18n.Language;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SongRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("Create request should reject blank pvs.url")
    void createRequest_withBlankPvUrl_shouldFailValidation() {
        SongCreateRequest request =
                new SongCreateRequest(
                        new SongCreateRequest.CanonicalNameCreateRequest(Language.KO, "test"),
                        null,
                        null,
                        List.of(),
                        null,
                        "ORIGINAL",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new SongCreateRequest.SongPvCreateRequest(
                                        "YOUTUBE", "abc123", " ", "title", null, null, null, true,
                                        null, null, 0)),
                        List.of(),
                        List.of(),
                        null,
                        null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pvs[0].url");
    }

    @Test
    @DisplayName("Update request should reject blank pvs.url")
    void updateRequest_withBlankPvUrl_shouldFailValidation() {
        SongUpdateRequest request =
                new SongUpdateRequest(
                        new SongUpdateRequest.CanonicalNameUpdateRequest(Language.KO, "test"),
                        null,
                        null,
                        List.of(),
                        null,
                        "ORIGINAL",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(
                                new SongUpdateRequest.SongPvUpdateRequest(
                                        "YOUTUBE", "abc123", " ", "title", null, null, null, true,
                                        null, null, 0)),
                        List.of(),
                        List.of(),
                        null,
                        null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("pvs[0].url");
    }
}
