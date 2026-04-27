package com.vocawik.module.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    @DisplayName("Should assign an UUID before persistence")
    void initializeUuid_shouldAssignUuidOnlyOnce() {
        TestEntity entity = new TestEntity();

        entity.initialize();
        UUID firstUuid = entity.getUuid();
        entity.initialize();

        assertThat(firstUuid).isNotNull();
        assertThat(entity.getUuid()).isEqualTo(firstUuid);
    }

    @Entity
    private static class TestEntity extends BaseEntity {

        private void initialize() {
            initializeUuid();
        }
    }
}
