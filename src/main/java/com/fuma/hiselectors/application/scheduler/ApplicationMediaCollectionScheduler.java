package com.fuma.hiselectors.application.scheduler;

import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.MediaCollectionStatus;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.application.service.ApplicationMediaService;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationMediaCollectionScheduler {

    private static final int MAX_RETRY_COUNT = 3;

    private final ApplicationRepository applicationRepository;
    private final ApplicationMediaService applicationMediaService;

    @Value("${application.media-collection.batch-size:20}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${application.media-collection.fixed-delay-ms:60000}",
            initialDelayString = "${application.media-collection.initial-delay-ms:10000}")
    public void collectPendingApplications() {
        List<Application> targets = applicationRepository
                .findByMediaCollectionStatusInAndMediaCollectionRetryCountLessThanOrderByIdAsc(
                        EnumSet.of(MediaCollectionStatus.PENDING, MediaCollectionStatus.FAILED),
                        MAX_RETRY_COUNT,
                        PageRequest.of(0, batchSize));

        int succeeded = 0;
        int failed = 0;
        for (Application application : targets) {
            try {
                applicationMediaService.collect(application.getId());
                succeeded++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("지원자 미디어 자동 수집 실패: applicationId={}", application.getId(), e);
            }
        }
        if (!targets.isEmpty()) {
            log.info("지원자 미디어 자동 수집 완료: targets={}, succeeded={}, failed={}",
                    targets.size(), succeeded, failed);
        }
    }
}
