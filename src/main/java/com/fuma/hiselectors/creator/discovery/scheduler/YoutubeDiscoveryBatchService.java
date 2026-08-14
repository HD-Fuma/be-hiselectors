package com.fuma.hiselectors.creator.discovery.scheduler;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 활성 키워드를 순서대로 실행하면서 쿼터와 개별 실패를 관리한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeDiscoveryBatchService {

    private final DiscoveryKeywordRepository keywordRepository;
    private final DiscoveryPipelineService discoveryPipelineService;
    private final YoutubeDiscoveryProperties discoveryProperties;
    private final YoutubeDiscoverySchedulerProperties schedulerProperties;

    public YoutubeDiscoveryBatchResult runDaily() {
        List<DiscoveryKeyword> runnableKeywords = keywordRepository.findRunnable();

        if (!discoveryProperties.hasApiKey()) {
            log.error("YouTube 일일 발굴 건너뜀. API 키가 설정되지 않았습니다.");
            return YoutubeDiscoveryBatchResult.empty(runnableKeywords.size());
        }

        int dailyQuota = Math.max(0, discoveryProperties.dailyQuotaOrDefault());
        int quotaKeywordLimit = dailyQuota / YoutubeDiscoveryProperties.QUOTA_PER_KEYWORD;
        int runLimit = Math.min(
                runnableKeywords.size(),
                Math.min(quotaKeywordLimit, schedulerProperties.maxKeywordsPerRunOrDefault()));

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

                log.info("YouTube 일일 발굴 키워드 성공. keywordId={}, keyword={}, quota={}",
                        keyword.getId(), keyword.getKeyword(), result.consumedQuota());
            } catch (RuntimeException exception) {
                failed++;
                log.warn("YouTube 일일 발굴 키워드 실패. keywordId={}, keyword={}",
                        keyword.getId(), keyword.getKeyword(), exception);
            }
        }

        int reservedQuota = attempted * YoutubeDiscoveryProperties.QUOTA_PER_KEYWORD;
        YoutubeDiscoveryBatchResult batchResult = new YoutubeDiscoveryBatchResult(
                runnableKeywords.size(), attempted, succeeded, failed,
                reservedQuota, consumedQuota, discovered, created, updated);

        log.info("YouTube 일일 발굴 종료. {}", batchResult);
        return batchResult;
    }
}
