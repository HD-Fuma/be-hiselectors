package com.fuma.hiselectors.selectors.scheduler;

import com.fuma.hiselectors.selectors.service.SelectorSnsEnrichmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 프로필 이미지 또는 카테고리가 비어 있는 셀렉터스를 지원서 없이 보강한다.
 * 운영 API 비용을 막기 위해 기본은 꺼 둔다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectorSnsEnrichmentScheduler {

    private final SelectorSnsEnrichmentService enrichmentService;

    @Value("${selectors.sns-enrichment.scheduler-enabled:false}")
    private boolean schedulerEnabled;

    @Value("${selectors.sns-enrichment.batch-size:5}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${selectors.sns-enrichment.fixed-delay-ms:60000}",
            initialDelayString = "${selectors.sns-enrichment.initial-delay-ms:15000}")
    public void enrichMissingSelectors() {
        if (!schedulerEnabled) {
            return;
        }
        var result = enrichmentService.enrichMissing(false, batchSize);
        if (result.targetCount() > 0) {
            log.info("셀렉터스 SNS 보강 완료: targets={}, profileUpdated={}, categoryUpdated={}, failed={}",
                    result.targetCount(),
                    result.profileImageUpdatedCount(),
                    result.categoryUpdatedCount(),
                    result.failedCount());
        }
    }
}
