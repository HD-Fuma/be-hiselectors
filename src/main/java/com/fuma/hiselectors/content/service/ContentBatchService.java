package com.fuma.hiselectors.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentBatchService {

    private final NewContentService newContentService;
    private final StoredContentService storedContentService;

    /** 신규 콘텐츠 수집 후 저장된 콘텐츠를 검수합니다. */
    public ContentBatchResult run() {
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

        return new ContentBatchResult(
                newContentCount,
                engagementCount,
                newContentSucceeded,
                storedContentSucceeded);
    }

    public record ContentBatchResult(
            int newContentCount,
            int engagementCount,
            boolean newContentSucceeded,
            boolean storedContentSucceeded) {
    }
}
