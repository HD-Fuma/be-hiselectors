package com.fuma.hiselectors.creator.discovery.batch;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.discovery.InstagramDiscoveryService;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.util.List;
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
        List<CreatorDiscoveryInfo> candidates = discoveryInfoRepository
                .findByCreatorPoolSnsCodeAndCreatorPoolDeletedFalseAndIgHandleIsNotNullOrderByIdAsc(
                        SnsPlatform.YOUTUBE.name());

        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        int created = 0;
        int updated = 0;

        // ponytail: keep this event-sized batch sequential; add paging only if it becomes slow.
        for (CreatorDiscoveryInfo candidate : candidates) {
            if (candidate.getIgHandle().isBlank()) {
                continue;
            }
            attempted++;
            try {
                InstagramDiscoveryResult result = instagramDiscoveryService
                        .discoverFromYoutubeCreator(candidate.getId());
                succeeded++;
                if (result.created()) {
                    created++;
                } else {
                    updated++;
                }
                log.info("Instagram 일괄 발굴 성공. youtubeCreatorId={}, handle={}, created={}",
                        candidate.getId(), candidate.getIgHandle(), result.created());
            } catch (BusinessException exception) {
                if (exception.getErrorCode() == ErrorCode.META_GRAPH_CONFIG_MISSING) {
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
        }

        InstagramDiscoveryBatchResult result = new InstagramDiscoveryBatchResult(
                attempted,
                succeeded,
                failed,
                created,
                updated
        );
        log.info("Instagram 일괄 발굴 종료. {}", result);
        return result;
    }
}
