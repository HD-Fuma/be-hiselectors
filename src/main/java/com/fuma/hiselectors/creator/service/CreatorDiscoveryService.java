package com.fuma.hiselectors.creator.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.dto.CategoryRefreshResponse;
import com.fuma.hiselectors.creator.dto.CategoryShare;
import com.fuma.hiselectors.creator.dto.CreatorDetailResponse;
import com.fuma.hiselectors.creator.dto.CreatorPoolResetResponse;
import com.fuma.hiselectors.creator.dto.CreatorSummary;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.logging.BatchEventLogger;
import com.fuma.hiselectors.logging.BatchLogContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발굴된 크리에이터 조회와 대표 카테고리 산출.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreatorDiscoveryService {

    private static final String RESET_CONFIRMATION = "DELETE_CREATOR_POOL";
    private static final List<String> RESET_PLATFORMS = List.of(
            SnsPlatform.YOUTUBE.name(), SnsPlatform.INSTAGRAM.name());

    private final CreatorPoolRepository creatorPoolRepository;
    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final CreatorDiscoverySourceRepository discoverySourceRepository;
    private final BatchEventLogger batchEventLogger;

    /**
     * 조건에 맞는 발굴 크리에이터를 조회한다. null 인 조건은 적용하지 않는다.
     *
     * @param activeWithinDays 최근 N일 안에 활동한 계정만. null 이면 제한 없음
     */
    public Page<CreatorSummary> search(String keyword, String categoryCode, String snsCode,
                                       Long minFollower, Long maxFollower,
                                       BigDecimal minEngagementRate,
                                       Integer minRecent90DayContentCount, Integer maxBrandScore,
                                       BigDecimal minIgConfidence, Integer activeWithinDays,
                                       Pageable pageable) {
        LocalDateTime activeAfter = activeWithinDays == null
                ? null
                : LocalDateTime.now().minusDays(activeWithinDays);

        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return creatorPoolRepository.search(normalizedKeyword, categoryCode, snsCode,
                minFollower, maxFollower, minEngagementRate, minRecent90DayContentCount,
                maxBrandScore, minIgConfidence, activeAfter, pageable);
    }

    /** 크리에이터 기본 정보와 발굴 판정 근거를 한 번에 조회한다. */
    public CreatorDetailResponse findDetail(Long creatorPoolId) {
        CreatorPool creator = getCreator(creatorPoolId);
        CreatorDiscoveryInfo discoveryInfo = discoveryInfoRepository.findById(creatorPoolId)
                .orElse(null);
        List<CategoryShare> categoryShares =
                discoverySourceRepository.findCategoryShares(creatorPoolId);

        return CreatorDetailResponse.of(creator, discoveryInfo, categoryShares);
    }

    /** 이 계정이 어떤 카테고리에서 얼마나 걸렸는지. 대표 카테고리 판정 근거. */
    public List<CategoryShare> findCategoryShares(Long creatorPoolId) {
        getCreator(creatorPoolId);
        return discoverySourceRepository.findCategoryShares(creatorPoolId);
    }

    /** 재생성 가능한 발굴 데이터만 지우고 업무 이력이 참조하는 중심 행은 숨긴다. */
    @Transactional
    public CreatorPoolResetResponse resetPool(String confirmation, String adminLoginId) {
        if (!RESET_CONFIRMATION.equals(confirmation)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "초기화 확인 문구가 올바르지 않습니다.");
        }

        BatchLogContext logContext = batchEventLogger.start("creator-pool-reset");
        int deletedSourceCount;
        int deletedInfoCount;
        int softDeletedCount;
        try {
            deletedSourceCount = discoverySourceRepository.deleteAllByCreatorPlatforms(
                    RESET_PLATFORMS);
            deletedInfoCount = discoveryInfoRepository.deleteAllByCreatorPlatforms(
                    RESET_PLATFORMS);
            softDeletedCount = creatorPoolRepository.softDeleteAllActiveByPlatforms(
                    RESET_PLATFORMS);
        } catch (RuntimeException | Error error) {
            batchEventLogger.failed(logContext, error);
            throw error;
        }

        batchEventLogger.succeeded(logContext, Map.of(
                "deletedSourceCount", (long) deletedSourceCount,
                "deletedInfoCount", (long) deletedInfoCount,
                "softDeletedCount", (long) softDeletedCount),
                Map.of("adminLoginId", adminLoginId));
        return new CreatorPoolResetResponse(softDeletedCount);
    }

    /**
     * 발굴 출처를 집계해 대표 카테고리를 다시 정한다.
     *
     * <p>조회수 비중 합이 가장 큰 카테고리를 고른다. 뷰티 크리에이터가 홈트 영상
     * 하나로 피트니스에 걸려도, 그 영상의 조회수 비중이 낮으면 뷰티로 남는다.
     *
     * <p>발굴 파이프라인이 한 계정의 수집을 마친 뒤 호출한다. 규칙을 바꾸고 싶으면
     * {@code findCategoryShares} 쿼리만 고치고 이 메서드를 다시 돌리면 되며,
     * API 를 재호출할 필요가 없다.
     *
     * @return 재산출 결과. 발굴 출처가 없으면 기존 카테고리를 그대로 담아 돌려준다
     */
    @Transactional
    public CategoryRefreshResponse refreshRepresentativeCategory(Long creatorPoolId) {
        CreatorPool creator = getCreator(creatorPoolId);

        List<CategoryShare> shares = discoverySourceRepository.findCategoryShares(creatorPoolId);
        if (shares.isEmpty()) {
            return CategoryRefreshResponse.unchanged(creator.getCategory());
        }

        String topCategory = shares.getFirst().categoryCode();
        creator.changeCategory(topCategory);
        return CategoryRefreshResponse.refreshed(topCategory);
    }

    private CreatorPool getCreator(Long creatorPoolId) {
        return creatorPoolRepository.findByIdAndDeletedFalse(creatorPoolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));
    }
}
