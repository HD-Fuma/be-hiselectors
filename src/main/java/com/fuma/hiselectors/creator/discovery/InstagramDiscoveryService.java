package com.fuma.hiselectors.creator.discovery;

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
import org.springframework.transaction.support.TransactionTemplate;

/** YouTube에서 추출한 Instagram 사용자명을 Meta로 조회해 별도 크리에이터로 저장한다. */
@Service
@RequiredArgsConstructor
public class InstagramDiscoveryService {

    private static final String SNS_CODE_YOUTUBE = "YOUTUBE";
    private static final String SNS_CODE_INSTAGRAM = "INSTAGRAM";
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final MetaGraphApiClient metaGraphApiClient;
    private final InstagramEngagementCalculator engagementCalculator;
    private final CreatorPoolRepository creatorPoolRepository;
    private final CreatorDiscoveryInfoRepository discoveryInfoRepository;
    private final TransactionTemplate transactionTemplate;

    public InstagramDiscoveryResult discoverFromYoutubeCreator(Long youtubeCreatorId) {
        CreatorPool sourceCreator = creatorPoolRepository.findById(youtubeCreatorId)
                .filter(creator -> SNS_CODE_YOUTUBE.equals(creator.getSnsCode()))
                .filter(creator -> !creator.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CREATOR_NOT_FOUND));

        String instagramHandle = discoveryInfoRepository.findById(youtubeCreatorId)
                .map(CreatorDiscoveryInfo::getIgHandle)
                .filter(handle -> !handle.isBlank())
                .orElseThrow(() -> new BusinessException(ErrorCode.INSTAGRAM_HANDLE_NOT_FOUND));

        // 외부 네트워크 호출은 DB 저장 트랜잭션 밖에서 수행한다.
        BusinessDiscovery discovered = metaGraphApiClient.discover(instagramHandle);
        BigDecimal engagementRate = engagementCalculator.calculate(
                discovered.followersCount(), discovered.media());
        LocalDateTime lastContentAt = lastContentAt(discovered);

        return Objects.requireNonNull(transactionTemplate.execute(status -> persist(
                youtubeCreatorId,
                sourceCreator.getCategory(),
                discovered,
                engagementRate,
                lastContentAt
        )));
    }

    private InstagramDiscoveryResult persist(
            Long sourceCreatorId,
            String sourceCategory,
            BusinessDiscovery discovered,
            BigDecimal engagementRate,
            LocalDateTime lastContentAt) {

        String username = discovered.username();
        CreatorPool creator = creatorPoolRepository
                .findFirstBySnsCodeAndAccountIdOrderByIdAsc(SNS_CODE_INSTAGRAM, username)
                .orElse(null);

        boolean created = creator == null;
        if (created) {
            creator = creatorPoolRepository.save(CreatorPool.builder()
                    .snsCode(SNS_CODE_INSTAGRAM)
                    .accountId(username)
                    .creatorName(displayName(discovered))
                    .followerCount(discovered.followersCount())
                    .lastContentAt(lastContentAt)
                    .engagementRate(engagementRate)
                    .category(sourceCategory)
                    .build());
        } else {
            creator.updateMetrics(discovered.followersCount(), engagementRate, lastContentAt);
            if (creator.isDeleted()) {
                creator.restore();
            }
        }

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

    private String displayName(BusinessDiscovery discovered) {
        return discovered.name() == null || discovered.name().isBlank()
                ? discovered.username()
                : discovered.name();
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
