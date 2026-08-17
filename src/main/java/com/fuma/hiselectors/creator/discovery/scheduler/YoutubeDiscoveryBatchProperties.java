package com.fuma.hiselectors.creator.discovery.scheduler;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** 관리자 실행형 YouTube 일괄 발굴 설정. */
@Validated
@ConfigurationProperties(prefix = "youtube.discovery.batch")
public record YoutubeDiscoveryBatchProperties(
        @Min(1) Integer maxKeywordsPerRun
) {

    public int maxKeywordsPerRunOrDefault() {
        return maxKeywordsPerRun == null ? 50 : maxKeywordsPerRun;
    }
}
