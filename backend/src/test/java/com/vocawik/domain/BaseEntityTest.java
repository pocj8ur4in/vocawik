package com.vocawik.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaseEntityTest {

    @Test
    @DisplayName("prePersist should generate uuid when uuid is null")
    void prePersist_shouldSetDefaultFields() throws Exception {
        TestEntity entity = new TestEntity();
        setField(entity, "uuid", null);

        entity.callPrePersist();

        assertThat(entity.getUuid()).isNotBlank();
    }

    @Test
    @DisplayName("prePersist should keep existing uuid")
    void prePersist_shouldKeepExistingValues() throws Exception {
        TestEntity entity = new TestEntity();
        setField(entity, "uuid", "existing-uuid");

        entity.callPrePersist();

        assertThat(entity.getUuid()).isEqualTo("existing-uuid");
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = BaseEntity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class TestEntity extends BaseEntity {
        private void callPrePersist() {
            prePersist();
        }
    }
}
