package com.fuma.hiselectors.selectors.scheduler;

import com.fuma.hiselectors.selectors.service.SelectorsLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "selectors.lifecycle.enabled", havingValue = "true")
public class SelectorsLifecycleScheduler {

    private final SelectorsLifecycleService selectorsLifecycleService;

    @Scheduled(
            cron = "${selectors.lifecycle.cron:0 15 0 * * *}",
            zone = "${selectors.lifecycle.zone:Asia/Seoul}")
    public void enrollQualifiedSelectors() {
        int enrolled = selectorsLifecycleService.enrollQualifiedSelectors();
        log.info("셀렉터스 기수 자동 연장 완료: enrolled={}", enrolled);
    }
}
