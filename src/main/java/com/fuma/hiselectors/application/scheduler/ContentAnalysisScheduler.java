package com.fuma.hiselectors.application.scheduler;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.ApplicationAnalysisService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미디어 수집(DONE)이 끝난 인스타·유튜브 지원자를 STT/OCR 분석 → 취합 → application_report 로 만든다.
 * 수집 스케줄러(ApplicationMediaCollectionScheduler) 다음 단계.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentAnalysisScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    /** 일시적 인프라 장애. 재시도 카운트를 소진하지 않는다(복구 시 자동 재개). */
    private static final Set<ErrorCode> TRANSIENT_ERRORS = EnumSet.of(
            ErrorCode.ANALYZER_UNAVAILABLE,
            ErrorCode.STT_WORKER_CALL_FAILED,
            ErrorCode.GEMINI_API_CALL_FAILED);

    private final ApplicationRepository applicationRepository;
    private final ApplicationAnalysisService analysisService;

    @Value("${application.content-analysis.batch-size:5}")
    private int batchSize;

    /** IN_PROGRESS lease(분). 처리 중 크래시하면 이 시간 뒤 다른 워커가 회수. 최대 처리시간보다 넉넉히. */
    @Value("${application.content-analysis.lease-minutes:30}")
    private long leaseMinutes;

    @Scheduled(
            fixedDelayString = "${application.content-analysis.fixed-delay-ms:60000}",
            initialDelayString = "${application.content-analysis.initial-delay-ms:20000}")
    public void analyzeCollectedApplications() {
        LocalDateTime leaseBefore = LocalDateTime.now().minusMinutes(leaseMinutes);
        List<Application> targets = applicationRepository.findAnalysisTargets(
                MediaCollectionStatus.DONE,
                EnumSet.of(ContentAnalysisStatus.PENDING, ContentAnalysisStatus.FAILED),
                ContentAnalysisStatus.IN_PROGRESS,
                leaseBefore,
                MAX_RETRY_COUNT,
                EnumSet.of(SnsPlatform.INSTAGRAM, SnsPlatform.YOUTUBE),
                PageRequest.of(0, batchSize));

        int succeeded = 0;
        int failed = 0;
        for (Application application : targets) {
            Long id = application.getId();
            // 원자적 선점: PENDING/FAILED(또는 lease 만료된 IN_PROGRESS) → IN_PROGRESS.
            // 0이면 다른 인스턴스가 이미 처리 중 → skip.
            int claimed = applicationRepository.claimForAnalysis(
                    id, ContentAnalysisStatus.IN_PROGRESS,
                    EnumSet.of(ContentAnalysisStatus.PENDING, ContentAnalysisStatus.FAILED),
                    LocalDateTime.now(), leaseBefore);
            if (claimed != 1) {
                continue;
            }
            try {
                analysisService.analyzeAndReport(id);
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                boolean infraDown = e instanceof BusinessException be
                        && TRANSIENT_ERRORS.contains(be.getErrorCode());
                analysisService.markFailed(id, e.getMessage(), !infraDown);
                log.warn("지원자 콘텐츠 분석 실패: applicationId={}, retryCounted={}", id, !infraDown, e);
            }
        }
        if (!targets.isEmpty()) {
            log.info("지원자 콘텐츠 분석 완료: targets={}, succeeded={}, failed={}",
                    targets.size(), succeeded, failed);
        }
    }
}
