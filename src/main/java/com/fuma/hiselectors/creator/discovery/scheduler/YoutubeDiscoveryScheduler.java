package com.fuma.hiselectors.creator.discovery.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 설정된 시각에 YouTube 발굴 배치를 시작한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "youtube.discovery.scheduler",
        name = "enabled",
        havingValue = "true"
)
public class YoutubeDiscoveryScheduler {

    private final YoutubeDiscoveryBatchService batchService;

    @Scheduled(
            cron = "${youtube.discovery.scheduler.cron:0 0 2 * * *}",
            zone = "${youtube.discovery.scheduler.zone:Asia/Seoul}"
    )
    public void runDaily() {
        log.info("YouTube 일일 발굴 시작");
        batchService.runDaily();
    }
}
