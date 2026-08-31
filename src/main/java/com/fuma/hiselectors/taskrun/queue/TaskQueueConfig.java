package com.fuma.hiselectors.taskrun.queue;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "task-queue.enabled", havingValue = "true")
public class TaskQueueConfig {

    @Bean(name = "taskQueueSqsClient", destroyMethod = "close")
    SqsClient taskQueueSqsClient(TaskQueueProperties properties) {
        return SqsClient.builder().region(Region.of(properties.region()))
                .overrideConfiguration(builder -> builder
                        .apiCallTimeout(Duration.ofSeconds(45))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30)))
                .build();
    }
}
