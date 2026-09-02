package com.fuma.hiselectors.taskrun.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("task-queue")
public record TaskQueueProperties(boolean enabled, boolean workerEnabled, String url,
        String dlqUrl, String region, int concurrency, int visibilitySeconds,
        int leaseSeconds, int heartbeatSeconds, int maxAttempts, int retryDelaySeconds,
        int resendAfterSeconds) {

    public TaskQueueProperties {
        if (workerEnabled && !enabled) {
            throw new IllegalArgumentException("Queue worker requires task-queue.enabled");
        }
        if (enabled && (url == null || url.isBlank() || region == null || region.isBlank())) {
            throw new IllegalArgumentException("Queue URL and region are required");
        }
        if (workerEnabled && (dlqUrl == null || dlqUrl.isBlank())) {
            throw new IllegalArgumentException("Worker DLQ URL is required");
        }
        if (enabled && (concurrency < 1 || concurrency > 4 || heartbeatSeconds < 5
                || leaseSeconds < heartbeatSeconds * 3 || visibilitySeconds < leaseSeconds
                || visibilitySeconds > 43_200 || maxAttempts < 1 || maxAttempts > 5
                || retryDelaySeconds < 1 || retryDelaySeconds > 900
                || resendAfterSeconds < visibilitySeconds)) {
            throw new IllegalArgumentException("Invalid queue concurrency, lease, or retry settings");
        }
    }
}
