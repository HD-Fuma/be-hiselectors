package com.fuma.hiselectors.application.scheduler;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ContentAnalysisStatus;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.ApplicationAnalysisService;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 미디어 수집(DONE)이 끝난 인스타 지원자를 STT/OCR 분석 → 취합 → application_report 로 만든다.
 * 수집 스케줄러(ApplicationMediaCollectionScheduler) 다음 단계.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentAnalysisScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final ApplicationRepository applicationRepository;
    private final ApplicationAnalysisService analysisService;

    @Value("${application.content-analysis.batch-size:5}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${application.content-analysis.fixed-delay-ms:60000}",
            initialDelayString = "${application.content-analysis.initial-delay-ms:20000}")
    public void analyzeCollectedApplications() {
        List<Application> targets = applicationRepository.findAnalysisTargets(
                MediaCollectionStatus.DONE,
                EnumSet.of(ContentAnalysisStatus.PENDING, ContentAnalysisStatus.FAILED),
                MAX_RETRY_COUNT,
                SnsPlatform.INSTAGRAM,
                PageRequest.of(0, batchSize));

        int succeeded = 0;
        int failed = 0;
        for (Application application : targets) {
            Long id = application.getId();
            // 원자적 선점: PENDING/FAILED → IN_PROGRESS. 0이면 다른 인스턴스가 이미 가져감 → skip.
            int claimed = applicationRepository.claimForAnalysis(
                    id, ContentAnalysisStatus.IN_PROGRESS,
                    EnumSet.of(ContentAnalysisStatus.PENDING, ContentAnalysisStatus.FAILED));
            if (claimed != 1) {
                continue;
            }
            try {
                analysisService.analyzeAndReport(id);
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                analysisService.markFailed(id, e.getMessage());
                log.warn("지원자 콘텐츠 분석 실패: applicationId={}", id, e);
            }
        }
        if (!targets.isEmpty()) {
            log.info("지원자 콘텐츠 분석 완료: targets={}, succeeded={}, failed={}",
                    targets.size(), succeeded, failed);
        }
    }
}
