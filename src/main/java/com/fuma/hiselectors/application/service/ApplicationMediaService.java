package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationMediaCollectionResponse;
import com.fuma.hiselectors.application.dto.ApplicationMediaResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.ApplicationStatus;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApplicationMediaService {

    private static final int COLLECTION_DAYS = 90;
    private static final int INSTAGRAM_CONTENT_LIMIT = 10;
    private static final BigDecimal LIKE_WEIGHT = new BigDecimal("0.5");
    private static final BigDecimal COMMENT_WEIGHT = new BigDecimal("5");
    private static final BigDecimal PERCENT = new BigDecimal("100");

    private final ApplicationRepository applicationRepository;
    private final ApplicationMediaRepository mediaRepository;
    private final List<ContentFetcher> contentFetchers;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Optional<AnalysisQueuePublisher> analysisQueuePublisher;
    private final SelectorsRepository selectorsRepository;
    private final SelectorsSnsAccountRepository selectorsSnsAccountRepository;

    public ApplicationMediaCollectionResponse collect(Long applicationId) {
        Application application = findApplication(applicationId);
        ApplicationMediaCollectionResponse response;
        try {
            LocalDateTime collectedAt = LocalDateTime.now(clock);
            LocalDateTime collectedAfter = application.getSnsCode() == SnsPlatform.INSTAGRAM
                    ? LocalDateTime.MIN
                    : collectedAt.minusDays(COLLECTION_DAYS);
            ContentFetcher fetcher = findFetcher(application);
            List<RawContent> contents = application.getSnsCode() == SnsPlatform.INSTAGRAM
                    ? fetcher.fetchByAccount(
                            application.getSnsAccountId(), collectedAfter, INSTAGRAM_CONTENT_LIMIT)
                    : fetcher.fetchByAccount(application.getSnsAccountId(), collectedAfter);
            Snapshot snapshot = createSnapshot(
                    application, fetcher, contents, collectedAfter, collectedAt);
            ContentFetcher.Profile profile = profile(fetcher, application);

            List<ApplicationMedia> saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
                Application lockedApplication = applicationRepository
                        .findByIdForUpdate(applicationId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.APPLICATION_USER_NOT_FOUND));
                mediaRepository.deleteByApplicationId(applicationId);
                mediaRepository.flush();
                List<ApplicationMedia> values = mediaRepository.saveAll(snapshot.media());
                lockedApplication.updateProfileImageUrl(profile.imageUrl());
                lockedApplication.fillMissingPublicMetrics(
                        profile.followerCount(), profile.contentCount());
                lockedApplication.completeMediaCollection(
                        collectedAt, snapshot.engagementRate());
                applicationRepository.save(lockedApplication);
                synchronizeProfileImageUrl(lockedApplication);
                return values;
            }));

            response = new ApplicationMediaCollectionResponse(
                    applicationId,
                    application.getSnsCode(),
                    contents.size(),
                    saved.size(),
                    saved.stream().map(ApplicationMediaResponse::from).toList());
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> {
                applicationRepository.findByIdForUpdate(applicationId)
                        .ifPresent(lockedApplication -> {
                            lockedApplication.failMediaCollection(e.getMessage());
                            applicationRepository.save(lockedApplication);
                        });
            });
            throw e;
        }
        analysisQueuePublisher.ifPresent(publisher -> publisher.publish(applicationId));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ApplicationMediaResponse> findLatest(Long applicationId) {
        findApplication(applicationId);
        return mediaRepository
                .findTop3ByApplicationIdOrderBySequenceNoAscMediaSequenceNoAsc(applicationId)
                .stream()
                .map(ApplicationMediaResponse::from)
                .toList();
    }

    private Application findApplication(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICATION_USER_NOT_FOUND));
    }

    private ContentFetcher findFetcher(Application application) {
        return contentFetchers.stream()
                .filter(fetcher -> fetcher.supports() == application.getSnsCode())
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT, "지원하지 않는 SNS 플랫폼입니다."));
    }

    private ContentFetcher.Profile profile(ContentFetcher fetcher, Application application) {
        if (hasText(application.getProfileImageUrl())
                && application.getContentCount() != null) {
            return new ContentFetcher.Profile(null, null, null);
        }
        try {
            ContentFetcher.Profile profile = fetcher.fetchProfile(application.getSnsAccountId());
            ContentFetcher.Profile value = profile == null
                    ? new ContentFetcher.Profile(null, null, null) : profile;
            if (application.getContentCount() == null && value.contentCount() == null) {
                throw new IllegalStateException("공개 프로필 전체 콘텐츠 수가 없습니다.");
            }
            return value;
        } catch (RuntimeException e) {
            log.warn("지원자 공개 프로필 조회 실패: applicationId={}, platform={}, cause={}",
                    application.getId(), application.getSnsCode(), e.getClass().getSimpleName());
            if (application.getContentCount() == null) {
                throw e;
            }
            return new ContentFetcher.Profile(null, null, null);
        }
    }

    private void synchronizeProfileImageUrl(Application application) {
        if (application.getStatus() != ApplicationStatus.APPROVED
                || !hasText(application.getProfileImageUrl())) {
            return;
        }
        selectorsRepository.findByUserIdForUpdate(application.getUserId())
                .filter(selectors -> !selectors.isDeleted())
                .filter(selectors -> Objects.equals(
                        selectors.getApplicationId(), application.getId()))
                .flatMap(selectors -> selectorsSnsAccountRepository
                        .findBySelectorsIdAndDeletedFalse(selectors.getId()))
                .ifPresent(account -> account.synchronizeProfileImageUrl(
                        application.getSnsCode(),
                        application.getSnsAccountId(),
                        application.getProfileImageUrl()));
    }

    private Snapshot createSnapshot(
            Application application, ContentFetcher fetcher,
            List<RawContent> contents,
            LocalDateTime collectedAfter,
            LocalDateTime collectedAt) {
        Map<String, RawContent> latestById = contents.stream()
                .filter(content -> validContent(
                        content, application.getSnsCode(), collectedAfter, collectedAt))
                .sorted(Comparator.comparing(RawContent::createdAt).reversed())
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<RawContent> filtered = List.copyOf(latestById.values());
        Map<String, RawContent> enrichedById = fetcher.addStatistics(filtered).stream()
                .filter(Objects::nonNull)
                .filter(content -> latestById.containsKey(content.snsContentId()))
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        Function.identity(),
                        (first, ignored) -> first));
        List<RawContent> snapshotContents = filtered.stream()
                .map(content -> enrichedById.getOrDefault(content.snsContentId(), content))
                .toList();

        List<ApplicationMedia> rows = new ArrayList<>();
        for (int contentIndex = 0; contentIndex < snapshotContents.size(); contentIndex++) {
            RawContent content = snapshotContents.get(contentIndex);
            List<RawContentMedia> media = validMedia(content, application.getSnsCode());
            for (int mediaIndex = 0; mediaIndex < media.size(); mediaIndex++) {
                rows.add(toEntity(
                        application, content, media.get(mediaIndex),
                        contentIndex, mediaIndex, collectedAt));
            }
        }
        return new Snapshot(List.copyOf(rows), engagementRate(snapshotContents, collectedAt));
    }

    private boolean validContent(
            RawContent content,
            SnsPlatform platform,
            LocalDateTime collectedAfter,
            LocalDateTime collectedAt) {
        return content != null
                && content.snsCode() == platform
                && hasText(content.snsContentId())
                && hasText(content.contentUrl())
                && content.createdAt() != null
                && !content.createdAt().isBefore(collectedAfter)
                && !content.createdAt().isAfter(collectedAt)
                && !validMedia(content, platform).isEmpty();
    }

    private List<RawContentMedia> validMedia(RawContent content, SnsPlatform platform) {
        return List.copyOf(content.media().stream()
                .filter(Objects::nonNull)
                .filter(media -> hasText(media.snsMediaId())
                        && (media.mediaType() == RawContentMedia.MediaType.IMAGE
                                || media.mediaType() == RawContentMedia.MediaType.VIDEO))
                .filter(media -> platform != SnsPlatform.INSTAGRAM || hasText(media.mediaUrl()))
                .collect(Collectors.toMap(
                        RawContentMedia::snsMediaId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new))
                .values());
    }

    private ApplicationMedia toEntity(
            Application application,
            RawContent content,
            RawContentMedia media,
            int sequenceNo,
            int mediaSequenceNo,
            LocalDateTime collectedAt) {
        return ApplicationMedia.builder()
                .applicationId(application.getId())
                .snsCode(application.getSnsCode())
                .snsContentId(content.snsContentId())
                .snsMediaId(media.snsMediaId())
                .contentUrl(content.contentUrl())
                .mediaUrl(nullIfBlank(media.mediaUrl()))
                .thumbnailUrl(media.thumbnailUrls().isEmpty()
                        ? null : media.thumbnailUrls().getLast())
                .contentType(content.contentType())
                .mediaType(mediaType(media.mediaType()))
                .caption(application.getSnsCode() == SnsPlatform.INSTAGRAM
                        ? nullIfBlank(content.caption()) : null)
                .title(youtubeText(application, content, 0))
                .description(youtubeText(application, content, 1))
                .sequenceNo(sequenceNo)
                .mediaSequenceNo(mediaSequenceNo)
                .publishedAt(content.createdAt())
                .viewCount(content.viewCount())
                .likeCount(content.likeCount())
                .commentCount(content.commentCount())
                .collectedAt(collectedAt)
                .build();
    }

    private MediaType mediaType(RawContentMedia.MediaType source) {
        return switch (source) {
            case IMAGE -> MediaType.IMAGE;
            case VIDEO -> MediaType.VIDEO;
            case TEXT -> throw new IllegalArgumentException("미디어는 IMAGE/VIDEO만 저장할 수 있습니다.");
        };
    }

    private String youtubeText(Application application, RawContent content, int index) {
        if (application.getSnsCode() != SnsPlatform.YOUTUBE || content.texts().size() <= index) {
            return null;
        }
        return nullIfBlank(content.texts().get(index));
    }

    private BigDecimal engagementRate(List<RawContent> contents, LocalDateTime collectedAt) {
        LocalDateTime collectedAfter = collectedAt.minusDays(COLLECTION_DAYS);
        List<BigDecimal> rates = contents.stream()
                .filter(content -> content.createdAt() != null)
                .filter(content -> !content.createdAt().isBefore(collectedAfter))
                .filter(content -> !content.createdAt().isAfter(collectedAt))
                .map(this::engagementRate)
                .filter(Objects::nonNull)
                .toList();
        if (rates.isEmpty()) {
            return null;
        }
        BigDecimal sum = rates.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(rates.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal engagementRate(RawContent content) {
        if (content.viewCount() == null || content.viewCount() <= 0
                || content.likeCount() == null || content.likeCount() < 0
                || content.commentCount() == null || content.commentCount() < 0) {
            return null;
        }
        BigDecimal weightedEngagement = BigDecimal.valueOf(content.likeCount())
                .multiply(LIKE_WEIGHT)
                .add(BigDecimal.valueOf(content.commentCount()).multiply(COMMENT_WEIGHT));
        return weightedEngagement.multiply(PERCENT)
                .divide(BigDecimal.valueOf(content.viewCount()), 8, RoundingMode.HALF_UP);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullIfBlank(String value) {
        return hasText(value) ? value : null;
    }

    private record Snapshot(List<ApplicationMedia> media, BigDecimal engagementRate) {
    }
}
