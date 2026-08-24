package com.fuma.hiselectors.creator.discovery.batch;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
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

/** 관리자가 발굴을 시작하면 추출된 Instagram 핸들을 순서대로 조회한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class InstagramDiscoveryBatchService {

    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final InstagramDiscoveryService instagramDiscoveryService;

    public InstagramDiscoveryBatchResult run() {
        return run(false, ignored -> { });
    }

    public InstagramDiscoveryBatchResult run(
            Consumer<InstagramDiscoveryBatchResult> progressCallback) {
        return run(false, progressCallback);
    }

    public InstagramDiscoveryBatchResult run(
            boolean test, Consumer<InstagramDiscoveryBatchResult> progressCallback) {
        Objects.requireNonNull(progressCallback, "progressCallback");
        List<CreatorDiscoveryInfo> candidates = discoveryInfoRepository
                .findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                        SnsPlatform.YOUTUBE.name())
                .stream()
                .filter(candidate -> !candidate.getIgHandle().isBlank())
                .toList();
        if (test) {
            Set<String> categories = new HashSet<>();
            candidates = candidates.stream()
                    .filter(candidate -> categories.add(candidate.getCreatorPool().getCategory()))
                    .toList();
        }

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        int created = 0;
        int updated = 0;
        Set<Long> collectedCreatorIds = new HashSet<>();

        // ponytail: keep this event-sized batch sequential; add paging only if it becomes slow.
        for (CreatorDiscoveryInfo candidate : candidates) {
            attempted++;
            try {
                InstagramDiscoveryResult result = instagramDiscoveryService
                        .discoverFromYoutubeCreator(candidate.getId());
                succeeded++;
                if (result.instagramCreatorId() != null) {
                    collectedCreatorIds.add(result.instagramCreatorId());
                }
                if (result.created()) {
                    created++;
                } else {
                    updated++;
                }
                log.info("Instagram 일괄 발굴 성공. youtubeCreatorId={}, handle={}, created={}",
                        candidate.getId(), candidate.getIgHandle(), result.created());
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.META_GRAPH_CONFIG_MISSING
                        || exception.getErrorCode() == ErrorCode.META_GRAPH_API_CALL_FAILED) {
                    failed++;
                    progressCallback.accept(new InstagramDiscoveryBatchResult(
                            candidates.size(), attempted, succeeded, failed,
                            created, updated, collectedCreatorIds.size()));
                    throw exception;
                }
                failed++;
                log.warn("Instagram 일괄 발굴 실패. youtubeCreatorId={}, handle={}, code={}",
                        candidate.getId(), candidate.getIgHandle(),
                        exception.getErrorCode());
            } catch (RuntimeException exception) {
                failed++;
                log.error("Instagram 일괄 발굴 중 예상하지 못한 오류. youtubeCreatorId={}, handle={}",
                        candidate.getId(), candidate.getIgHandle(), exception);
            }

            progressCallback.accept(new InstagramDiscoveryBatchResult(
                    candidates.size(), attempted, succeeded, failed,
                    created, updated, collectedCreatorIds.size()));
        }

        InstagramDiscoveryBatchResult result = new InstagramDiscoveryBatchResult(
                candidates.size(),
                attempted,
                succeeded,
                failed,
                created,
                updated,
                collectedCreatorIds.size()
        );
        log.info("Instagram 일괄 발굴 종료. {}", result);
        return result;
    }
}
