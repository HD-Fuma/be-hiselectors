package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import com.fuma.hiselectors.taskrun.service.TaskProgressReporter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentBatchService {

    private final NewContentService newContentService;
    private final StoredContentService storedContentService;
    private final BatchEventLogger batchEventLogger;

    /** 신규 콘텐츠를 수집하고 기존 콘텐츠 변경을 확인합니다. */
    public ContentBatchResult run(TaskProgressReporter progress) {
        return run(progress, ContentBatchMode.STANDARD);
    }

    public ContentBatchResult run(
            TaskProgressReporter progress, ContentBatchMode mode) {
        BatchLogContext logContext = batchEventLogger.start("content-sync");
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
            int[] collectedContentCount = {0};
            int[] checkedContentCount = {0};
            int[] failedContentCount = {0};
            boolean[] storedProgressStarted = {false};
            List<ContentSyncFailure> representativeFailures = new ArrayList<>(3);
            int[] totalFailureCount = {0};

            progress.reportStep("NEW_CONTENT_SYNC", null, 0);
            progress.reportStep("STORED_CONTENT_SYNC", null, 0);
            progress.describe("신규 콘텐츠 수집 중: 0건 처리");
            progress.start("NEW_CONTENT_SYNC", null);
            try {
                NewContentService.NewContentResult result = collect(mode, update ->
                        reportProgress(() -> {
                            collectFailure(
                                    update.failure(), representativeFailures, totalFailureCount);
                            collectedContentCount[0] += update.savedContentDelta();
                            progress.reportStep(
                                    "NEW_CONTENT_SYNC", null, collectedContentCount[0]);
                            progress.describe("신규 콘텐츠 수집 중: "
                                    + collectedContentCount[0] + "건 처리");
                            progress.advance(
                                    update.savedContentDelta(), update.failedAccountDelta(), 0);
                        }));
                newContentResult = result;
                newContentCount = result.savedContentCount();
                collectedContentCount[0] = newContentCount;
                newContentSucceeded = result.failedAccountCount() == 0;
            } catch (ProgressUpdateException exception) {
                throw exception.failure();
            } catch (RuntimeException exception) {
                collectFailure(
                        ContentSyncFailure.stageFailure(
                                "NEW_CONTENT_SYNC",
                                exception,
                                "신규 콘텐츠 수집 단계에서 오류가 발생했습니다."),
                        representativeFailures,
                        totalFailureCount);
                failedStageCount++;
                newContentSucceeded = false;
                log.error("신규 콘텐츠 수집 배치에 실패했습니다.", exception);
                progress.advance(0, 1, 0);
            }
            progress.reportStep(
                    "NEW_CONTENT_SYNC",
                    (long) collectedContentCount[0],
                    collectedContentCount[0]);

            try {
                StoredContentService.StoredContentResult result = check(mode, update ->
                        reportProgress(() -> {
                            collectFailure(
                                    update.failure(), representativeFailures, totalFailureCount);
                            if (!storedProgressStarted[0]) {
                                progress.reportStep(
                                        "STORED_CONTENT_SYNC", (long) update.totalContentCount(), 0);
                                progress.describe("기존 콘텐츠 수집 중: 0건 처리");
                                progress.changeStep("STORED_CONTENT_SYNC");
                                storedProgressStarted[0] = true;
                                return;
                            }
                            int checkedDelta = update.checkedContentCount()
                                    - checkedContentCount[0];
                            int failedDelta = update.failedContentCount()
                                    - failedContentCount[0];
                            checkedContentCount[0] = update.checkedContentCount();
                            failedContentCount[0] = update.failedContentCount();
                            progress.reportStep(
                                    "STORED_CONTENT_SYNC",
                                    (long) update.totalContentCount(),
                                    checkedContentCount[0]);
                            progress.describe("기존 콘텐츠 수집 중: "
                                    + checkedContentCount[0] + "건 처리");
                            progress.advance(
                                    checkedDelta - failedDelta,
                                    failedDelta,
                                    0);
                        }));
                storedContentResult = result;
                engagementCount = result.savedEngagementCount();
                checkedContentCount[0] = result.checkedContentCount();
                storedContentSucceeded = result.failedContentCount() == 0;
            } catch (ProgressUpdateException exception) {
                throw exception.failure();
            } catch (RuntimeException exception) {
                collectFailure(
                        ContentSyncFailure.stageFailure(
                                "STORED_CONTENT_SYNC",
                                exception,
                                "기존 콘텐츠 확인 단계에서 오류가 발생했습니다."),
                        representativeFailures,
                        totalFailureCount);
                failedStageCount++;
                storedContentSucceeded = false;
                log.error("기존 콘텐츠 변경 확인 배치에 실패했습니다.", exception);
                if (!storedProgressStarted[0]) {
                    progress.reportStep("STORED_CONTENT_SYNC", null, 0);
                    progress.describe("기존 콘텐츠 수집 중: 0건 처리");
                    progress.changeStep("STORED_CONTENT_SYNC");
                }
                progress.advance(0, 1, 0);
            }

            progress.describe("신규 콘텐츠 " + collectedContentCount[0]
                    + "건 수집, 기존 콘텐츠 " + checkedContentCount[0] + "건 수집");
            if (totalFailureCount[0] > 0) {
                progress.recordFailure(
                        "CONTENT_SYNC_PARTIAL_FAILURE",
                        ContentSyncFailureFormatter.format(
                                representativeFailures,
                                totalFailureCount[0] - representativeFailures.size()));
            }

            Set<Long> changedVersionIds = new HashSet<>(newContentResult.savedVersionIds());
            changedVersionIds.addAll(storedContentResult.changedVersionIds());
            ContentBatchResult batchResult = new ContentBatchResult(
                    newContentCount,
                    engagementCount,
                    newContentSucceeded,
                    storedContentSucceeded,
                    Set.copyOf(changedVersionIds));
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
        }
    }

    private void reportProgress(Runnable update) {
        try {
            update.run();
        } catch (RuntimeException exception) {
            throw new ProgressUpdateException(exception);
        }
    }

    private NewContentService.NewContentResult collect(
            ContentBatchMode mode,
            Consumer<NewContentService.NewContentProgress> progress) {
        return mode == ContentBatchMode.STANDARD
                ? newContentService.collect(progress)
                : newContentService.collect(mode, progress);
    }

    private StoredContentService.StoredContentResult check(
            ContentBatchMode mode,
            Consumer<StoredContentService.StoredContentProgress> progress) {
        return mode == ContentBatchMode.STANDARD
                ? storedContentService.check(progress)
                : storedContentService.check(mode, progress);
    }

    private void collectFailure(
            ContentSyncFailure failure,
            List<ContentSyncFailure> representativeFailures,
            int[] totalFailureCount) {
        if (failure == null) {
            return;
        }
        totalFailureCount[0]++;
        if (representativeFailures.size() < 3) {
            representativeFailures.add(failure);
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
            boolean storedContentSucceeded,
            Set<Long> changedVersionIds) {

        public ContentBatchResult(
                int newContentCount,
                int engagementCount,
                boolean newContentSucceeded,
                boolean storedContentSucceeded) {
            this(
                    newContentCount,
                    engagementCount,
                    newContentSucceeded,
                    storedContentSucceeded,
                    Set.of());
        }
    }

    private static final class ProgressUpdateException extends RuntimeException {

        private final RuntimeException failure;

        private ProgressUpdateException(RuntimeException failure) {
            this.failure = failure;
        }

        private RuntimeException failure() {
            return failure;
        }
    }
}
