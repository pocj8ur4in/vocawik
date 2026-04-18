package com.vocawik.module.logging.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("Should carry captured MDC into decorated task")
    void decorate_shouldCarryCapturedMdc() {
        MDC.put("requestId", "request-1");

        Runnable task =
                decorator.decorate(() -> assertThat(MDC.get("requestId")).isEqualTo("request-1"));

        MDC.clear();
        task.run();

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("Should restore previous MDC after decorated task")
    void decorate_shouldRestorePreviousMdc() {
        MDC.put("requestId", "request-1");
        Runnable task =
                decorator.decorate(() -> assertThat(MDC.get("requestId")).isEqualTo("request-1"));

        MDC.put("requestId", "worker-request");
        task.run();

        assertThat(MDC.get("requestId")).isEqualTo("worker-request");
    }

    @Test
    @DisplayName("Should clear MDC during task when captured MDC is empty")
    void decorateWithEmptyMdc_shouldClearMdcDuringTask() {
        Runnable task = decorator.decorate(() -> assertThat(MDC.getCopyOfContextMap()).isEmpty());

        MDC.put("requestId", "worker-request");
        task.run();

        assertThat(MDC.get("requestId")).isEqualTo("worker-request");
    }
}
