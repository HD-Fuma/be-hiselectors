package com.fuma.hiselectors.creator.discovery.scheduler;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 관리자가 발굴을 시작하면 활성 키워드를 순서대로 실행한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeDiscoveryBatchService {

    private final DiscoveryKeywordRepository keywordRepository;
    private final DiscoveryPipelineService discoveryPipelineService;
    private final YoutubeDiscoveryProperties discoveryProperties;
    private final YoutubeDiscoveryBatchProperties batchProperties;

    public YoutubeDiscoveryBatchResult run() {
        if (!discoveryProperties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }

        List<DiscoveryKeyword> runnableKeywords = keywordRepository.findRunnable();
        int dailyQuota = Math.max(0, discoveryProperties.dailyQuotaOrDefault());
        int quotaKeywordLimit = dailyQuota / YoutubeDiscoveryProperties.QUOTA_PER_KEYWORD;
        int runLimit = Math.min(
                runnableKeywords.size(),
                Math.min(quotaKeywordLimit, batchProperties.maxKeywordsPerRunOrDefault()));

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        int consumedQuota = 0;
        int discovered = 0;
        int created = 0;
        int updated = 0;

        for (DiscoveryKeyword keyword : runnableKeywords.subList(0, runLimit)) {
            attempted++;
            try {
                DiscoveryRunResult result = discoveryPipelineService.runByKeyword(
                        keyword.getId(), discoveryProperties.maxResultsOrDefault());
                succeeded++;
                consumedQuota += result.consumedQuota();
                discovered += result.discovered();
                created += result.created();
                updated += result.updated();

                log.info("YouTube 일괄 발굴 키워드 성공. keywordId={}, keyword={}, quota={}",
                        keyword.getId(), keyword.getKeyword(), result.consumedQuota());
            } catch (RuntimeException exception) {
                failed++;
                log.warn("YouTube 일괄 발굴 키워드 실패. keywordId={}, keyword={}",
                        keyword.getId(), keyword.getKeyword(), exception);
            }
        }

        int reservedQuota = attempted * YoutubeDiscoveryProperties.QUOTA_PER_KEYWORD;
        YoutubeDiscoveryBatchResult batchResult = new YoutubeDiscoveryBatchResult(
                runnableKeywords.size(), attempted, succeeded, failed,
                reservedQuota, consumedQuota, discovered, created, updated);

        log.info("YouTube 일괄 발굴 종료. {}", batchResult);
        return batchResult;
    }
}
