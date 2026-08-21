package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.creator.discovery.CreatorRecentActivityBackfillService.BackfillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 명시적으로 켠 별도 프로세스에서만 백필하고 종료한다. */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "creator.recent-activity-backfill",
        name = "enabled",
        havingValue = "true")
public class CreatorRecentActivityBackfillRunner implements ApplicationRunner {

    private final CreatorRecentActivityBackfillService backfillService;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        BackfillResult result = backfillService.run();
        log.info("creator-recent-activity-backfill={}", result);
        if (result.failed() > 0) {
            throw new IllegalStateException("YouTube 최근 활동 백필 실패: " + result);
        }
        applicationContext.close();
    }
}
