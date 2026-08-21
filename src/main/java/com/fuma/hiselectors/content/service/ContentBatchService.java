package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.inspection.service.StaleContentInspectionService;
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
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 신규 콘텐츠 수집 후 저장된 콘텐츠를 검수합니다. */
    public ContentBatchResult run() {
        if (!running.compareAndSet(false, true)) {
            return new ContentBatchResult(0, 0, false, false);
        }

        try {
            int newContentCount = 0;
            int engagementCount = 0;
            boolean newContentSucceeded = true;
            boolean storedContentSucceeded = true;

            try {
                NewContentService.NewContentResult result = newContentService.collect();
                newContentCount = result.savedContentCount();
                newContentSucceeded = result.failedAccountCount() == 0;
            } catch (RuntimeException exception) {
                newContentSucceeded = false;
                log.error("신규 콘텐츠 수집 배치에 실패했습니다.", exception);
            }

            try {
                StoredContentService.StoredContentResult result = storedContentService.check();
                engagementCount = result.savedEngagementCount();
                storedContentSucceeded = result.failedContentCount() == 0;
            } catch (RuntimeException exception) {
                storedContentSucceeded = false;
                log.error("기존 콘텐츠 검수 배치에 실패했습니다.", exception);
            }

            // 수집·버전 저장 트랜잭션이 끝난 뒤 미검수 최신 버전을 별도로 처리한다.
            try {
                staleContentInspectionService.reinspectStale(null);
            } catch (RuntimeException exception) {
                log.error("콘텐츠 AI 검수 실행에 실패했습니다.", exception);
            }

            return new ContentBatchResult(
                    newContentCount,
                    engagementCount,
                    newContentSucceeded,
                    storedContentSucceeded);
        } finally {
            running.set(false);
        }
    }

    public record ContentBatchResult(
            int newContentCount,
            int engagementCount,
            boolean newContentSucceeded,
            boolean storedContentSucceeded) {
    }
}
