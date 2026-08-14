package com.fuma.hiselectors.creator.discovery.scheduler;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 일일 YouTube 발굴 스케줄러 설정. */
@Validated
@ConfigurationProperties(prefix = "youtube.discovery.scheduler")
public record YoutubeDiscoverySchedulerProperties(
        Boolean enabled,
        String cron,
        String zone,
        @Min(1) Integer maxKeywordsPerRun
) {

    public int maxKeywordsPerRunOrDefault() {
        return maxKeywordsPerRun == null ? 50 : maxKeywordsPerRun;
    }
}
