package com.fuma.hiselectors.taskrun.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfig {

    private final TaskRunProperties properties;

    public TaskExecutorConfig(TaskRunProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "taskRunExecutor")
    public ThreadPoolTaskExecutor taskRunExecutor() {
        TaskRunProperties.Executor executorProperties = properties.executor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(executorProperties.coreSize());
        executor.setMaxPoolSize(executorProperties.maxSize());
        executor.setQueueCapacity(executorProperties.queueCapacity());
        executor.setThreadNamePrefix(executorProperties.threadPrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
