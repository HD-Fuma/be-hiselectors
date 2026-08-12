package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.category.model.DiscoveryKeyword;
import com.fuma.hiselectors.category.repository.DiscoveryKeywordRepository;
import com.fuma.hiselectors.creator.discovery.BrandScoreCalculator.BrandScore;
import com.fuma.hiselectors.creator.discovery.IgHandleExtractor.IgHandle;
import com.fuma.hiselectors.creator.discovery.YoutubeDiscoveryClient.DiscoveredChannel;
import com.fuma.hiselectors.creator.discovery.dto.DiscoveryRunResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorDiscoverySource;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorDiscoverySourceRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.creator.service.CreatorDiscoveryService;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발굴 파이프라인. 키워드 하나로 YouTube 를 검색해 채널을 찾고 DB 에 저장한다.
 *
 * <pre>
 * search.list(키워드)  100 units → 영상 ID
 * videos.list(배치)      1 unit  → 조회수·채널 ID
 * channels.list(배치)    1 unit  → 구독자수·채널 설명
 *   → 채널 설명에서 인스타 핸들 추출
 *   → 브랜드 신호 점수 계산
 *   → creator_pool + 발굴 정보 + 발굴 출처 저장
 * </pre>
 *
 * <p><b>여기서 후보를 걸러내지 않는다.</b> 브랜드 계정도 구독자 미달 계정도 전부 저장하고,
 * 실제로 빼는 일은 조회 API 조건이 한다. 판정 기준은 반드시 한 번은 틀리는데
 * 수집 시점에 버리면 기준을 고쳐도 재수집이 필요하고 그게 쿼터를 또 쓰기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryPipelineService {

    private static final String SNS_CODE_YOUTUBE = "YOUTUBE";

    private final YoutubeDiscoveryClient youtubeClient;
    private final IgHandleExtractor igHandleExtractor;
    private final BrandScoreCalculator brandScoreCalculator;

    private final DiscoveryKeywordRepository keywordRepository;
    private final CreatorPoolRepository creatorPoolRepository;
    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final CreatorDiscoverySourceRepository discoverySourceRepository;
    private final CreatorDiscoveryService creatorDiscoveryService;

    /**
     * 키워드 하나로 발굴을 실행한다. 약 102 units 를 쓴다.
     *
     * @param keywordId {@code discovery_keyword} 의 ID
     */
    @Transactional
    public DiscoveryRunResult runByKeyword(Long keywordId, Integer maxResults) {
        DiscoveryKeyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        List<DiscoveredChannel> channels =
                youtubeClient.discoverByKeyword(keyword.getKeyword(), maxResults);

        long totalViews = channels.stream()
                .mapToLong(DiscoveredChannel::matchedVideoViews)
                .sum();

        int created = 0;
        int updated = 0;
        for (DiscoveredChannel channel : channels) {
            boolean isNew = save(channel, keyword, totalViews);
            if (isNew) {
                created++;
            } else {
                updated++;
            }
        }

        keyword.markRun(LocalDateTime.now());

        DiscoveryRunResult result = new DiscoveryRunResult(
                keyword.getKeyword(),
                keyword.getCategory().getCode(),
                channels.size(), created, updated,
                youtubeClient.consumedQuota());

        log.info("발굴 완료. {}", result);
        return result;
    }

    /**
     * 채널 하나를 저장한다.
     *
     * @return 새로 만들었으면 true, 기존 계정을 갱신했으면 false
     */
    private boolean save(DiscoveredChannel channel, DiscoveryKeyword keyword, long totalViews) {
        IgHandle igHandle = igHandleExtractor.extract(channel.description()).orElse(null);
        BrandScore brandScore = brandScoreCalculator.calculate(
                channel.title(), channel.description(),
                igHandle == null ? null : igHandle.handle());

        // 소프트 삭제된 계정도 찾아야 중복 행이 생기지 않는다
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc(SNS_CODE_YOUTUBE, channel.channelId())
                .orElse(null);

        boolean isNew = creator == null;
        if (isNew) {
            creator = creatorPoolRepository.save(CreatorPool.builder()
                    .snsCode(SNS_CODE_YOUTUBE)
                    .accountId(channel.channelId())
                    .creatorName(channel.title())
                    .followerCount(channel.subscriberCount())
                    .lastContentAt(channel.lastUploadAt())
                    .engagementRate(engagementRate(channel))
                    .category(keyword.getCategory().getCode())
                    .build());
        } else {
            creator.updateMetrics(channel.subscriberCount(),
                    engagementRate(channel), channel.lastUploadAt());
            // 지웠던 계정이 다시 발굴되면 되살린다
            if (creator.isDeleted()) {
                creator.restore();
            }
        }

        saveDiscoveryInfo(creator, igHandle, brandScore);
        saveDiscoverySource(creator, keyword, channel, totalViews);

        // 발굴 출처가 쌓인 뒤에 대표 카테고리를 다시 정한다.
        // 여러 카테고리에 걸린 채널은 조회수 비중이 큰 쪽으로 잡힌다.
        creatorDiscoveryService.refreshRepresentativeCategory(creator.getId());

        return isNew;
    }

    private void saveDiscoveryInfo(CreatorPool creator, IgHandle igHandle, BrandScore brandScore) {
        BigDecimal confidence = igHandle == null ? null : igHandle.confidence();
        String handle = igHandle == null ? null : igHandle.handle();

        discoveryInfoRepository.findById(creator.getId()).ifPresentOrElse(
                info -> info.refresh(brandScore.score(), brandScore.hitsAsText(),
                        handle, confidence),
                () -> discoveryInfoRepository.save(CreatorDiscoveryInfo.builder()
                        .creatorPool(creator)
                        .brandScore(brandScore.score())
                        .brandHits(brandScore.hitsAsText())
                        .igHandle(handle)
                        .igConfidence(confidence)
                        .build()));
    }

    private void saveDiscoverySource(CreatorPool creator, DiscoveryKeyword keyword,
                                     DiscoveredChannel channel, long totalViews) {
        BigDecimal viewShare = viewShare(channel.matchedVideoViews(), totalViews);

        discoverySourceRepository
                .findByCreatorPoolIdAndKeywordId(creator.getId(), keyword.getId())
                .ifPresentOrElse(
                        source -> source.refresh(viewShare),
                        () -> discoverySourceRepository.save(CreatorDiscoverySource.builder()
                                .creatorPool(creator)
                                .keyword(keyword)
                                .viewShare(viewShare)
                                .build()));
    }

    /**
     * 이 채널이 이번 검색 결과 전체 조회수에서 차지하는 비중.
     *
     * <p>영상 개수가 아니라 조회수로 보는 이유: 뷰티 크리에이터가 홈트 영상 하나로
     * 피트니스에 걸려도 그 영상의 조회수 비중이 낮으면 뷰티로 남는다.
     */
    private BigDecimal viewShare(long channelViews, long totalViews) {
        if (totalViews <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(channelViews)
                .divide(BigDecimal.valueOf(totalViews), 5, RoundingMode.HALF_UP);
    }

    /** creator_pool.engagement_rate 는 decimal(5,2) 이라 소수점 둘째 자리까지다. */
    private BigDecimal engagementRate(DiscoveredChannel channel) {
        return BigDecimal.valueOf(channel.engagementRatePercent())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
