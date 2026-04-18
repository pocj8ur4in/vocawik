package com.vocawik.module.logging.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.TaskDecorator;

class MdcTaskDecoratorConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(MdcTaskDecoratorConfiguration.class);

    @Test
    @DisplayName("Should register MDC task decorator")
    void mdcTaskDecorator_shouldRegisterBean() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(MdcTaskDecorator.class));
    }

    @Test
    @DisplayName("Should not override custom task decorator")
    void mdcTaskDecoratorWithCustomTaskDecorator_shouldBackOff() {
        contextRunner
                .withBean(TaskDecorator.class, () -> runnable -> runnable)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(TaskDecorator.class);
                            assertThat(context).doesNotHaveBean(MdcTaskDecorator.class);
                        });
    }
}
