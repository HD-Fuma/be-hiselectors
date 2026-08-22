package com.fuma.hiselectors.creator.discovery;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.BusinessDiscovery;
import com.fuma.hiselectors.creator.discovery.dto.InstagramBusinessDiscoveryResponse.MediaItem;
import com.fuma.hiselectors.creator.discovery.dto.InstagramDiscoveryResult;
import com.fuma.hiselectors.creator.model.CreatorDiscoveryInfo;
import com.fuma.hiselectors.creator.model.CreatorPool;
import com.fuma.hiselectors.creator.repository.CreatorDiscoveryInfoRepository;
import com.fuma.hiselectors.creator.repository.CreatorPoolRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/** YouTube에서 추출한 Instagram 사용자명을 Meta로 조회해 별도 크리에이터로 저장한다. */
@Service
@RequiredArgsConstructor
public class InstagramDiscoveryService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int RECENT_MEDIA_LIMIT = 25;
    private static final int ACTIVITY_WINDOW_DAYS = 90;

    private final MetaGraphApiClient metaGraphApiClient;
    private final InstagramEngagementCalculator engagementCalculator;
    private final PublicEmailExtractor publicEmailExtractor;
    private final CreatorPoolRepository creatorPoolRepository;
    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final TransactionTemplate transactionTemplate;

    public InstagramDiscoveryResult discoverFromYoutubeCreator(Long youtubeCreatorId) {
        CreatorPool sourceCreator = creatorPoolRepository.findById(youtubeCreatorId)
                .filter(creator -> SnsPlatform.YOUTUBE.name().equals(creator.getSnsCode()))
                .filter(creator -> !creator.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        String instagramHandle = discoveryInfoRepository.findById(youtubeCreatorId)
                .map(CreatorDiscoveryInfo::getIgHandle)
                .filter(handle -> !handle.isBlank())
                .orElseThrow(() -> new BusinessException(ErrorCode.INSTAGRAM_HANDLE_NOT_FOUND));

        // 외부 네트워크 호출은 DB 저장 트랜잭션 밖에서 수행한다.
        BusinessDiscovery discovered = metaGraphApiClient.discover(
                instagramHandle, RECENT_MEDIA_LIMIT);
        BigDecimal engagementRate = engagementCalculator.calculate(
                discovered.followersCount(), discovered.media());
        LocalDateTime lastContentAt = lastContentAt(discovered);
        int recent90DayContentCount = recent90DayContentCount(discovered);

        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> persist(
                    youtubeCreatorId,
                    sourceCreator.getCategory(),
                    discovered,
                    engagementRate,
                    lastContentAt,
                    recent90DayContentCount
            )));
        } catch (DataIntegrityViolationException e) {
            // 같은 계정이 동시에 최초 발굴되면 DB 유니크 제약에서 한 요청만 성공한다.
            // 실패한 요청은 승리한 행을 다시 읽어 정상적인 갱신 결과로 반환한다.
            return Objects.requireNonNull(transactionTemplate.execute(status ->
                    updateAfterConcurrentInsert(
                            youtubeCreatorId, discovered, engagementRate, lastContentAt,
                            recent90DayContentCount)));
        }
    }

    private InstagramDiscoveryResult persist(
            Long sourceCreatorId,
            String sourceCategory,
            BusinessDiscovery discovered,
            BigDecimal engagementRate,
            LocalDateTime lastContentAt,
            int recent90DayContentCount) {

        String username = discovered.username();
        String instagramId = discovered.id();
        if (instagramId == null || instagramId.isBlank()) {
            throw new BusinessException(ErrorCode.INSTAGRAM_DISCOVERY_ACCOUNT_NOT_FOUND);
        }
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                        SnsPlatform.INSTAGRAM.name(), instagramId)
                // 이 기능 도입 전 username을 account_id로 저장한 행은 즉시 이전한다.
                .or(() -> creatorPoolRepository
                        .findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                                SnsPlatform.INSTAGRAM.name(), username))
                .orElse(null);

        boolean created = creator == null;
        if (created) {
            creator = creatorPoolRepository.saveAndFlush(CreatorPool.builder()
                    .snsCode(SnsPlatform.INSTAGRAM.name())
                    .accountId(instagramId)
                    .creatorName(username)
                    .email(publicEmailExtractor.extract(discovered.biography()).orElse(null))
                    .followerCount(discovered.followersCount())
                    .lastContentAt(lastContentAt)
                    .engagementRate(engagementRate)
                    .category(sourceCategory)
                    .build());
        } else {
            if (!instagramId.equals(creator.getAccountId())) {
                creator.migrateAccountId(instagramId);
            }
            creator.updateProfile(username, discovered.followersCount(),
                    engagementRate, lastContentAt);
            if (creator.isDeleted()) {
                creator.restore();
            }
        }

        saveRecentActivity(creator, recent90DayContentCount);

        return new InstagramDiscoveryResult(
                sourceCreatorId,
                creator.getId(),
                username,
                created,
                discovered.followersCount(),
                discovered.mediaCount(),
                engagementRate,
                lastContentAt
        );
    }

    private InstagramDiscoveryResult updateAfterConcurrentInsert(
            Long sourceCreatorId,
            BusinessDiscovery discovered,
            BigDecimal engagementRate,
            LocalDateTime lastContentAt,
            int recent90DayContentCount) {
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc(
                        SnsPlatform.INSTAGRAM.name(), discovered.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        creator.updateProfile(discovered.username(), discovered.followersCount(),
                engagementRate, lastContentAt);
        if (creator.isDeleted()) {
            creator.restore();
        }
        saveRecentActivity(creator, recent90DayContentCount);
        return new InstagramDiscoveryResult(
                sourceCreatorId,
                creator.getId(),
                discovered.username(),
                false,
                discovered.followersCount(),
                discovered.mediaCount(),
                engagementRate,
                lastContentAt
        );
    }

    private void saveRecentActivity(CreatorPool creator, int recent90DayContentCount) {
        discoveryInfoRepository.findById(creator.getId()).ifPresentOrElse(
                info -> info.updateRecent90DayContentCount(recent90DayContentCount),
                () -> discoveryInfoRepository.save(CreatorDiscoveryInfo.builder()
                        .creatorPool(creator)
                        .brandScore(0)
                        .recent90DayContentCount(recent90DayContentCount)
                        .build()));
    }

    private int recent90DayContentCount(BusinessDiscovery discovered) {
        if (discovered.media() == null || discovered.media().data() == null) {
            return 0;
        }
        LocalDateTime cutoff = LocalDateTime.now(SEOUL).minusDays(ACTIVITY_WINDOW_DAYS);
        // ponytail: Meta 1회 응답의 최근 25건까지만 센다. 25건 초과 계정의 정확한 수가
        // 필요해지면 media paging cursor를 따라 추가 조회한다.
        return (int) discovered.media().data().stream()
                .filter(Objects::nonNull)
                .map(MediaItem::timestamp)
                .map(this::parseTimestamp)
                .filter(Objects::nonNull)
                .filter(timestamp -> !timestamp.isBefore(cutoff))
                .count();
    }

    private LocalDateTime lastContentAt(BusinessDiscovery discovered) {
        if (discovered.media() == null || discovered.media().data() == null) {
            return null;
        }
        return discovered.media().data().stream()
                .filter(Objects::nonNull)
                .map(MediaItem::timestamp)
                .map(this::parseTimestamp)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /** Meta의 +0000 형식과 ISO-8601의 +00:00 형식을 모두 처리한다. */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        String normalized = timestamp.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
        try {
            return OffsetDateTime.parse(normalized)
                    .atZoneSameInstant(SEOUL)
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
