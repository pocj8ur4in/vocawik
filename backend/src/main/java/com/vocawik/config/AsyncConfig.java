package com.vocawik.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async configuration.
 *
 * <p>Enables {@code @Async} support and provides a thread pool for asynchronous tasks.
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final String threadNamePrefix;

    /**
     * Creates async configuration with thread pool properties.
     *
     * @param corePoolSize core number of threads
     * @param maxPoolSize maximum number of threads
     * @param queueCapacity queue capacity before spawning additional threads
     * @param threadNamePrefix thread name prefix for async executor threads
     */
    public AsyncConfig(
            @Value("${async.core-pool-size:5}") int corePoolSize,
            @Value("${async.max-pool-size:20}") int maxPoolSize,
            @Value("${async.queue-capacity:100}") int queueCapacity,
            @Value("${async.thread-name-prefix:async-}") String threadNamePrefix) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.threadNamePrefix = threadNamePrefix;
    }

    /**
     * Creates a thread pool executor for async tasks.
     *
     * @return configured {@link ThreadPoolTaskExecutor}
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * Returns the default executor for {@code @Async} methods.
     *
     * @return the task executor
     */
    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    /**
     * Handles uncaught exceptions from {@code @Async} methods.
     *
     * @return the exception handler
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                logger.error("Async exception in {}: {}", method.getName(), ex.getMessage(), ex);
    }
}
