package com.fuma.hiselectors.application.service;

import com.fuma.hiselectors.application.dto.ApplicationMediaCollectionResponse;
import com.fuma.hiselectors.application.dto.ApplicationMediaResponse;
import com.fuma.hiselectors.application.model.Application;
import com.fuma.hiselectors.application.model.ApplicationMedia;
import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.application.repository.ApplicationMediaRepository;
import com.fuma.hiselectors.application.repository.ApplicationRepository;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ApplicationMediaService {

    private static final int COLLECTION_DAYS = 90;
    private static final int STATISTICS_LIMIT = 10;

    private final ApplicationRepository applicationRepository;
    private final ApplicationMediaRepository mediaRepository;
    private final List<ContentFetcher> contentFetchers;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ApplicationMediaCollectionResponse collect(Long applicationId) {
        Application application = findApplication(applicationId);
        try {
            LocalDateTime collectedAt = LocalDateTime.now(clock);
            LocalDateTime collectedAfter = application.getSnsCode() == SnsPlatform.INSTAGRAM
                    ? LocalDateTime.MIN
                    : collectedAt.minusDays(COLLECTION_DAYS);
            ContentFetcher fetcher = findFetcher(application);
            List<RawContent> contents = fetcher.fetchByAccount(
                    application.getSnsAccountId(), collectedAfter);
            List<ApplicationMedia> snapshot = createSnapshot(
                    application, fetcher, contents, collectedAfter, collectedAt);

            List<ApplicationMedia> saved = Objects.requireNonNull(transactionTemplate.execute(status -> {
                mediaRepository.deleteByApplicationId(applicationId);
                mediaRepository.flush();
                List<ApplicationMedia> values = mediaRepository.saveAll(snapshot);
                application.completeMediaCollection(collectedAt);
                applicationRepository.save(application);
                return values;
            }));

            return new ApplicationMediaCollectionResponse(
                    applicationId,
                    application.getSnsCode(),
                    contents.size(),
                    saved.size(),
                    saved.stream().map(ApplicationMediaResponse::from).toList());
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> {
                application.failMediaCollection(e.getMessage());
                applicationRepository.save(application);
            });
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationMediaResponse> findLatest(Long applicationId) {
        findApplication(applicationId);
        return mediaRepository.findTop3ByApplicationIdOrderBySequenceNoAsc(applicationId).stream()
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

    private List<ApplicationMedia> createSnapshot(
            Application application, ContentFetcher fetcher,
            List<RawContent> contents,
            LocalDateTime collectedAfter,
            LocalDateTime collectedAt) {
        Map<String, RawContent> latestById = contents.stream()
                .filter(content -> content != null
                        && content.snsCode() == application.getSnsCode()
                        && content.snsContentId() != null
                        && !content.snsContentId().isBlank()
                        && content.contentUrl() != null
                        && !content.contentUrl().isBlank()
                        && content.createdAt() != null
                        && !content.createdAt().isBefore(collectedAfter)
                        && !content.createdAt().isAfter(collectedAt)
                        && (application.getSnsCode() != SnsPlatform.INSTAGRAM
                                || content.media().stream().anyMatch(media ->
                                        media != null
                                                && media.mediaUrl() != null
                                                && !media.mediaUrl().isBlank())))
                .sorted(Comparator.comparing(RawContent::createdAt).reversed())
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        Function.identity(),
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<RawContent> filtered = List.copyOf(latestById.values());
        List<RawContent> statisticsTargets = filtered.stream()
                .limit(STATISTICS_LIMIT)
                .toList();
        Map<String, RawContent> enrichedById = fetcher.addStatistics(statisticsTargets).stream()
                .filter(Objects::nonNull)
                .filter(content -> latestById.containsKey(content.snsContentId()))
                .collect(Collectors.toMap(
                        RawContent::snsContentId,
                        Function.identity(),
                        (first, ignored) -> first));
        List<RawContent> snapshot = filtered.stream()
                .map(content -> enrichedById.getOrDefault(content.snsContentId(), content))
                .toList();

        return IntStream.range(0, snapshot.size())
                .mapToObj(index -> toEntity(
                        application, snapshot.get(index), index, collectedAt))
                .toList();
    }

    private ApplicationMedia toEntity(
            Application application,
            RawContent content,
            int sequenceNo,
            LocalDateTime collectedAt) {
        List<String> mediaUrls = content.media().stream()
                .map(media -> media.mediaUrl())
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        List<String> thumbnailUrls = content.media().stream()
                .flatMap(media -> media.thumbnailUrls().stream())
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .toList();
        return ApplicationMedia.builder()
                .applicationId(application.getId())
                .snsCode(application.getSnsCode())
                .snsContentId(content.snsContentId())
                .contentUrl(content.contentUrl())
                .mediaUrl(mediaUrls.isEmpty() ? null : mediaUrls.getFirst())
                .mediaUrls(mediaUrls)
                .thumbnailUrls(thumbnailUrls)
                // videos.list duration만으로는 Shorts의 세로 비율을 확인할 수 없다.
                .contentType(application.getSnsCode() == SnsPlatform.YOUTUBE
                        ? null : content.contentType())
                .sequenceNo(sequenceNo)
                .publishedAt(content.createdAt())
                .viewCount(sequenceNo < STATISTICS_LIMIT ? content.viewCount() : null)
                .likeCount(sequenceNo < STATISTICS_LIMIT ? content.likeCount() : null)
                .commentCount(sequenceNo < STATISTICS_LIMIT ? content.commentCount() : null)
                .collectedAt(collectedAt)
                .build();
    }
}
