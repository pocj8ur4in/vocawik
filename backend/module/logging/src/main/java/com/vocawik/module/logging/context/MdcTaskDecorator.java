package com.vocawik.module.logging.context;

import java.util.Map;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/** Propagates the current MDC to tasks executed on a different thread. */
public class MdcTaskDecorator implements TaskDecorator {

    /**
     * Captures the submitting thread's MDC and restores the worker thread MDC after execution.
     *
     * @param runnable task to decorate
     * @return task wrapped with MDC propagation
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        Map<String, String> capturedContext = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                replaceContext(capturedContext);
                runnable.run();
            } finally {
                replaceContext(previousContext);
            }
        };
    }

    /**
     * Replaces the current MDC with the given context or clears it when absent.
     *
     * @param context MDC context map to restore
     */
    private void replaceContext(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
            return;
        }
        MDC.setContextMap(context);
    }
}
