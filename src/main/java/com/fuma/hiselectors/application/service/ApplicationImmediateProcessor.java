package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.scheduler.ContentAnalysisScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 지원서 제출이 커밋되면 백그라운드로 미디어 수집→분석→리포트를 즉시 실행한다.
 * 스케줄러(수집·분석)는 실패/누락분을 회수하는 백업으로 그대로 둔다. 분석 선점은 원자적이라 중복되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationImmediateProcessor {

    private final ApplicationMediaService applicationMediaService;
    private final ContentAnalysisScheduler contentAnalysisScheduler;

    @Value("${application.immediate-processing.enabled:true}")
    private boolean enabled;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(ApplicationCreatedEvent event) {
        if (!enabled) {
            return;
        }
        Long applicationId = event.applicationId();
        try {
            applicationMediaService.collect(applicationId);
        } catch (RuntimeException e) {
            // 실패해도 스케줄러가 재시도한다. 분석은 수집 성공 시에만 의미가 있어 여기서 중단.
            log.warn("지원자 즉시 미디어 수집 실패, 스케줄러가 재처리: applicationId={}", applicationId, e);
            return;
        }
        contentAnalysisScheduler.analyzeNow(applicationId);
    }
}
