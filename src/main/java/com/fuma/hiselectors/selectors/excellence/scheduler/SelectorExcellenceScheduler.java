package com.fuma.hiselectors.selectors.excellence.scheduler;

import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceSelectionService;
import com.fuma.hiselectors.selectors.excellence.service.SelectorExcellenceSelectionService.BatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "selectors.excellence.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SelectorExcellenceScheduler {

    private final SelectorExcellenceSelectionService selectionService;

    @Scheduled(
            cron = "${selectors.excellence.cron:0 20 0 * * *}",
            zone = "${selectors.excellence.zone:Asia/Seoul}")
    public void selectExcellentSelectors() {
        BatchResult result = selectionService.selectEligibleGenerations();
        log.info(
                "셀렉터스 우수 활동자 선정 완료: candidates={}, processed={}, skipped={}, selections={}, failed={}",
                result.candidateGenerationCount(),
                result.processedGenerationCount(),
                result.skippedGenerationCount(),
                result.selectionCount(),
                result.failedGenerationCount());
    }
}
