package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentBatchService {

    private final NewContentService newContentService;
    private final StoredContentService storedContentService;
    private final StaleContentInspectionService staleContentInspectionService;
    private final BatchEventLogger batchEventLogger;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 신규 콘텐츠를 수집하고 기존 콘텐츠 변경을 확인합니다. */
    public ContentBatchResult run() {
        BatchLogContext logContext = batchEventLogger.start("content-sync");
        if (!running.compareAndSet(false, true)) {
            batchEventLogger.skipped(logContext, "ALREADY_RUNNING", Map.of(), Map.of());
            return new ContentBatchResult(0, 0, false, false);
        }

        try {
            int newContentCount = 0;
            int engagementCount = 0;
            int failedStageCount = 0;
            boolean newContentSucceeded = true;
            boolean storedContentSucceeded = true;
            NewContentService.NewContentResult newContentResult =
                    new NewContentService.NewContentResult(0, 0);
            StoredContentService.StoredContentResult storedContentResult =
                    new StoredContentService.StoredContentResult(0, 0);

            try {
                NewContentService.NewContentResult result = newContentService.collect();
                newContentResult = result;
                newContentCount = result.savedContentCount();
                newContentSucceeded = result.failedAccountCount() == 0;
            } catch (RuntimeException exception) {
                failedStageCount++;
                newContentSucceeded = false;
                log.error("신규 콘텐츠 수집 배치에 실패했습니다.", exception);
            }

            try {
                StoredContentService.StoredContentResult result = storedContentService.check();
                storedContentResult = result;
                engagementCount = result.savedEngagementCount();
                storedContentSucceeded = result.failedContentCount() == 0;
            } catch (RuntimeException exception) {
                failedStageCount++;
                storedContentSucceeded = false;
                log.error("기존 콘텐츠 변경 확인 배치에 실패했습니다.", exception);
            }

            // 수집·버전 저장 트랜잭션이 끝난 뒤 미검수 최신 버전을 별도로 처리한다.
            try {
                staleContentInspectionService.reinspectStale(null);
            } catch (RuntimeException exception) {
                log.error("콘텐츠 AI 검수 실행에 실패했습니다.", exception);
            }

            ContentBatchResult batchResult = new ContentBatchResult(
                    newContentCount,
                    engagementCount,
                    newContentSucceeded,
                    storedContentSucceeded);
            Map<String, Long> counts = batchCounts(
                    newContentResult, storedContentResult, failedStageCount);
            if (failedStageCount > 0 || !newContentSucceeded || !storedContentSucceeded) {
                batchEventLogger.partialFailure(logContext, counts, Map.of());
            } else if (newContentResult.platformStats().isEmpty()
                    && storedContentResult.platformStats().isEmpty()) {
                batchEventLogger.skipped(logContext, "NO_TARGETS", counts, Map.of());
            } else {
                batchEventLogger.succeeded(logContext, counts, Map.of());
            }
            return batchResult;
        } catch (Error error) {
            batchEventLogger.failed(logContext, error);
            throw error;
        } finally {
            running.set(false);
        }
    }

    private Map<String, Long> batchCounts(
            NewContentService.NewContentResult newContentResult,
            StoredContentService.StoredContentResult storedContentResult,
            int failedStageCount) {
        Map<String, Long> counts = new HashMap<>();
        for (SnsPlatform platform : SnsPlatform.values()) {
            NewContentService.PlatformCollectionStats collected =
                    newContentResult.platformStats().getOrDefault(
                            platform,
                            new NewContentService.PlatformCollectionStats(0, 0, 0, 0));
            StoredContentService.PlatformStoredContentStats stored =
                    storedContentResult.platformStats().getOrDefault(
                            platform,
                            new StoredContentService.PlatformStoredContentStats(0, 0));
            String prefix = platform.name().toLowerCase(Locale.ROOT);
            counts.put(prefix + "NewCandidateCount", (long) collected.candidateCount());
            counts.put(
                    prefix + "SelectorsContentCount",
                    (long) collected.selectorsContentCount());
            counts.put(
                    prefix + "ChangedContentCount",
                    (long) stored.changedVersionCount());
            counts.put(
                    prefix + "SavedVersionCount",
                    (long) collected.savedVersionCount() + stored.changedVersionCount());
            counts.put(
                    prefix + "FailedCount",
                    (long) collected.failedAccountCount() + stored.failedContentCount());
        }
        counts.put("failedStageCount", (long) failedStageCount);
        return Map.copyOf(counts);
    }

    public record ContentBatchResult(
            int newContentCount,
            int engagementCount,
            boolean newContentSucceeded,
            boolean storedContentSucceeded) {
    }
}
