package com.kntro.reqsai.shared.infrastructure.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Provides a named {@code taskExecutor} bean so Spring's {@code @Async} infrastructure
 * resolves deterministically when multiple executor beans are present (e.g. the
 * {@code applicationTaskExecutor} and the Spring Modulith event-processing executor).
 * Without an explicit name Spring emits an WARN and falls back to {@code SimpleAsyncTaskExecutor}.
 */
@Configuration
class AsyncConfiguration {

    @Bean("taskExecutor")
    TaskExecutor taskExecutor() {
        var executor = new SimpleAsyncTaskExecutor("reqsai-async-");
        executor.setVirtualThreads(true);
        return executor;
    }
}
