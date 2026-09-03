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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 발굴 파이프라인. 키워드 하나로 YouTube 를 검색해 채널을 찾고 DB 에 저장한다.
 *
 * <pre>
 * search.list(키워드)    100 units → 영상 ID
 * videos.list(배치)        1 unit  → 조회수·채널 ID
 * channels.list(배치)      1 unit  → 구독자수·채널 설명
 * playlistItems.list       1+ unit → 최근 활동일·최근 90일 활동 수
 *   → 채널 설명에서 인스타 핸들 추출
 *   → 브랜드 신호 점수 계산
 *   → creator_pool + 발굴 정보 + 발굴 출처 저장
 * </pre>
 *
 * <p>브랜드 계정이나 구독자 미달 계정은 전부 저장하고 실제로 빼는 일은 조회 API 조건이 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscoveryPipelineService {

    private static final String SNS_CODE_YOUTUBE = "YOUTUBE";
    private static final String COLLECTED_CREATOR_EMAIL = "jaewonwi98@gmail.com";

    private enum SaveResult {
        CREATED, UPDATED
    }

    private record SaveOutcome(SaveResult result, Long creatorId) {
    }

    private final YoutubeDiscoveryClient youtubeClient;
    private final IgHandleExtractor igHandleExtractor;
    private final BrandScoreCalculator brandScoreCalculator;

    private final DiscoveryKeywordRepository keywordRepository;
    private final CreatorPoolRepository creatorPoolRepository;
    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final CreatorDiscoverySourceRepository discoverySourceRepository;
    private final CreatorDiscoveryService creatorDiscoveryService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 키워드 하나로 발굴을 실행한다. 약 102 units 를 쓴다.
     *
     * @param keywordId {@code discovery_keyword} 의 ID
     */
    public DiscoveryRunResult runByKeyword(Long keywordId, Integer maxResults) {
        return runByKeywordInternal(keywordId, maxResults, false);
    }

    public DiscoveryRunResult runByKeyword(
            Long keywordId, Integer maxResults, boolean currentMonthOnly) {
        return runByKeywordInternal(keywordId, maxResults, currentMonthOnly);
    }

    private DiscoveryRunResult runByKeywordInternal(
            Long keywordId, Integer maxResults, boolean currentMonthOnly) {
        String keywordText = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND))
                .getKeyword();

        List<DiscoveredChannel> channels = currentMonthOnly
                ? youtubeClient.discoverByKeyword(keywordText, maxResults, true)
                : youtubeClient.discoverByKeyword(keywordText, maxResults);
        int consumedQuota = youtubeClient.consumedQuota();

        return Objects.requireNonNull(transactionTemplate.execute(status ->
                persistDiscoveryResult(keywordId, channels, consumedQuota)));
    }

    /** 외부 API 호출이 끝난 뒤 DB 변경 작업만 하나의 트랜잭션으로 처리한다. */
    private DiscoveryRunResult persistDiscoveryResult(
            Long keywordId, List<DiscoveredChannel> channels,
            int consumedQuota) {
        DiscoveryKeyword keyword = keywordRepository.findById(keywordId)
                .orElseThrow(() -> new BusinessException(ErrorCode.KEYWORD_NOT_FOUND));

        int created = 0;
        int updated = 0;
        Set<Long> creatorIds = new LinkedHashSet<>();
        for (DiscoveredChannel channel : channels) {
            SaveOutcome outcome = save(channel, keyword);
            switch (outcome.result()) {
                case CREATED -> created++;
                case UPDATED -> updated++;
            }
            if (outcome.creatorId() != null) {
                creatorIds.add(outcome.creatorId());
            }
        }

        keyword.markRun(LocalDateTime.now());

        DiscoveryRunResult result = new DiscoveryRunResult(
                keyword.getKeyword(),
                keyword.getCategory().getCode(),
                channels.size(), created, updated,
                consumedQuota, creatorIds);

        log.info("발굴 완료. {}", result);
        return result;
    }

    /**
     * 채널 하나를 저장한다.
     *
     * @return 신규 저장 또는 기존 갱신
     */
    private SaveOutcome save(DiscoveredChannel channel, DiscoveryKeyword keyword) {
        // 소프트 삭제된 계정도 찾아야 중복 행이 생기지 않는다
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc(SNS_CODE_YOUTUBE, channel.channelId())
                .orElse(null);

        boolean isNew = creator == null;

        IgHandle igHandle = igHandleExtractor.extract(channel.description()).orElse(null);
        BrandScore brandScore = brandScoreCalculator.calculate(
                channel.title(), channel.description(),
                igHandle == null ? null : igHandle.handle());

        if (isNew) {
            creator = creatorPoolRepository.save(CreatorPool.builder()
                    .snsCode(SNS_CODE_YOUTUBE)
                    .accountId(channel.channelId())
                    .creatorName(channel.title())
                    .email(COLLECTED_CREATOR_EMAIL)
                    .followerCount(channel.subscriberCount())
                    .lastContentAt(channel.lastUploadAt())
                    .engagementRate(engagementRate(channel))
                    .category(keyword.getCategory().getCode())
                    .build());
        } else {
            creator.updateEmail(COLLECTED_CREATOR_EMAIL);
            creator.updateMetrics(channel.subscriberCount(),
                    engagementRate(channel), channel.lastUploadAt());
            // 지웠던 계정이 다시 발굴되면 되살린다
            if (creator.isDeleted()) {
                creator.restore();
            }
        }

        saveDiscoveryInfo(creator, igHandle, brandScore,
                channel.recent90DayContentCount(), channel.profileImageUrl());
        saveDiscoverySource(creator, keyword, channel);

        // 발굴 출처가 쌓인 뒤에 대표 카테고리를 다시 정한다.
        // 여러 카테고리에 걸린 채널은 조회수 비중이 큰 쪽으로 잡힌다.
        creatorDiscoveryService.refreshRepresentativeCategory(creator.getId());

        return new SaveOutcome(
                isNew ? SaveResult.CREATED : SaveResult.UPDATED,
                creator.getId());
    }

    private void saveDiscoveryInfo(CreatorPool creator, IgHandle igHandle, BrandScore brandScore,
                                   Integer recent90DayContentCount, String profileImageUrl) {
        BigDecimal confidence = igHandle == null ? null : igHandle.confidence();
        String handle = igHandle == null ? null : igHandle.handle();

        discoveryInfoRepository.findById(creator.getId()).ifPresentOrElse(
                info -> {
                    info.refresh(brandScore.score(), brandScore.hitsAsText(), handle, confidence);
                    info.updateRecent90DayContentCount(recent90DayContentCount);
                    info.updateProfileImageUrl(profileImageUrl);
                },
                () -> discoveryInfoRepository.save(CreatorDiscoveryInfo.builder()
                        .creatorPool(creator)
                        .brandScore(brandScore.score())
                        .brandHits(brandScore.hitsAsText())
                        .igHandle(handle)
                        .igConfidence(confidence)
                        .recent90DayContentCount(recent90DayContentCount)
                        .profileImageUrl(profileImageUrl)
                        .build()));
    }

    private void saveDiscoverySource(CreatorPool creator, DiscoveryKeyword keyword,
                                     DiscoveredChannel channel) {
        BigDecimal viewShare = viewShare(
                channel.matchedVideoViews(), channel.totalViewCount());

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
     * 이 키워드로 걸린 영상이 채널 전체 조회수에서 차지하는 비중.
     *
     * <p>검색 결과 전체를 분모로 쓰면 관련 없는 바이럴 영상 한 편이
     * 대표 카테고리를 뒤집을 수 있다. 채널 전체 조회수로 나누어야
     * 해당 주제가 채널 전체에서 얼마나 대표적인지 비교할 수 있다.
     */
    private BigDecimal viewShare(long matchedVideoViews, Long channelTotalViews) {
        if (channelTotalViews == null || channelTotalViews <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(matchedVideoViews)
                .divide(BigDecimal.valueOf(channelTotalViews), 5, RoundingMode.HALF_UP);
    }

    /** creator_pool.engagement_rate 는 decimal(5,2) 이라 소수점 둘째 자리까지다. */
    private BigDecimal engagementRate(DiscoveredChannel channel) {
        return BigDecimal.valueOf(channel.engagementRatePercent())
                .setScale(2, RoundingMode.HALF_UP);
    }
}
