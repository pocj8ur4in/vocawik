package com.vocawik.module.logging.context;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/** Configures MDC propagation support. */
@Configuration(proxyBeanMethods = false)
public class MdcTaskDecoratorConfiguration {

    /**
     * Creates the task decorator used to propagate MDC into async tasks.
     *
     * @return MDC task decorator
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    public MdcTaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
