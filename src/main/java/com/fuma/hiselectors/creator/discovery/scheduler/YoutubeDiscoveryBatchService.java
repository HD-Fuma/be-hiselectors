package com.fuma.hiselectors.creator.discovery.scheduler;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.DiscoveryPipelineService;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryProperties;
import com.fuma.hiselectors.creator.discovery.batch.InstagramDiscoveryBatchService;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 활성 YouTube 키워드를 순서대로 실행한 뒤 추출된 Instagram 계정도 발굴한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class YoutubeDiscoveryBatchService {

    private final DiscoveryKeywordRepository keywordRepository;
    private final DiscoveryPipelineService discoveryPipelineService;
    private final YoutubeDiscoveryProperties discoveryProperties;
    private final YoutubeDiscoveryBatchProperties batchProperties;
    private final InstagramDiscoveryBatchService instagramDiscoveryBatchService;

    public YoutubeDiscoveryBatchResult run() {
        YoutubeDiscoveryBatchResult result = runYoutubeOnly();
        instagramDiscoveryBatchService.run();
        return result;
    }

    public YoutubeDiscoveryBatchResult runYoutubeOnly() {
        return runYoutubeOnly(ignored -> { });
    }

    public YoutubeDiscoveryBatchResult runYoutubeOnly(
            Consumer<YoutubeDiscoveryBatchResult> progressCallback) {
        Objects.requireNonNull(progressCallback, "progressCallback");
        if (!discoveryProperties.hasApiKey()) {
            throw new BusinessException(ErrorCode.YOUTUBE_API_KEY_MISSING);
        }

        List<DiscoveryKeyword> runnableKeywords = keywordRepository.findRunnable();
        int dailyQuota = Math.max(0, discoveryProperties.dailyQuotaOrDefault());
        int reservedQuotaPerKeyword = discoveryProperties.quotaPerKeyword();
        int quotaKeywordLimit = dailyQuota / reservedQuotaPerKeyword;
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
        Set<Long> collectedCreatorIds = new HashSet<>();

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
                collectedCreatorIds.addAll(result.creatorIds());

                log.info("YouTube 일괄 발굴 키워드 성공. keywordId={}, keyword={}, quota={}",
                        keyword.getId(), keyword.getKeyword(), result.consumedQuota());
            } catch (RuntimeException exception) {
                failed++;
                log.warn("YouTube 일괄 발굴 키워드 실패. keywordId={}, keyword={}",
                        keyword.getId(), keyword.getKeyword(), exception);
            }

            progressCallback.accept(new YoutubeDiscoveryBatchResult(
                    runnableKeywords.size(), runLimit, attempted, succeeded, failed,
                    attempted * reservedQuotaPerKeyword, consumedQuota,
                    discovered, created, updated, collectedCreatorIds.size()));
        }

        int reservedQuota = attempted * reservedQuotaPerKeyword;
        YoutubeDiscoveryBatchResult batchResult = new YoutubeDiscoveryBatchResult(
                runnableKeywords.size(), runLimit, attempted, succeeded, failed,
                reservedQuota, consumedQuota, discovered, created, updated,
                collectedCreatorIds.size());

        log.info("YouTube 일괄 발굴 종료. {}", batchResult);
        return batchResult;
    }
}
