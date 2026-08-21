package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchResult;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoredContentService {

    private final GenerationService generationService;
    private final ContentRepository contentRepository;
    private final List<ContentFetcher> fetchers;
    private final ContentEngagementRepository engagementRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentMediaRepository mediaRepository;
    private final ContentSnapshotFactory snapshotFactory;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 현재 기수 콘텐츠의 성과와 수정 내용을 저장합니다. */
    public StoredContentResult check() {
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);
        List<StoredContentFetch> results = fetchStoredContents();
        int savedEngagementCount = 0;
        int failedContentCount = 0;

        for (StoredContentFetch result : results) {
            try {
                Integer savedCount = transactionTemplate.execute(
                        status -> save(result, collectedAt));
                savedEngagementCount += savedCount == null ? 0 : savedCount;
            } catch (RuntimeException exception) {
                failedContentCount++;
                log.error(
                        "기존 콘텐츠 저장에 실패했습니다. contentId={}",
                        result.content().getId(),
                        exception);
            }
        }

        return new StoredContentResult(savedEngagementCount, failedContentCount);
    }

    private int save(StoredContentFetch result, LocalDateTime collectedAt) {
        if (result.fetched().status() == ContentFetcher.FetchStatus.FAILED) {
            return 0;
        }

        int savedEngagementCount = saveEngagement(result, collectedAt);
        boolean versionChanged = saveChangedVersion(result, collectedAt);
        boolean deletionStatusChanged = updateDeletionStatus(result);
        if (versionChanged || deletionStatusChanged) {
            contentRepository.saveAll(List.of(result.content()));
        }
        return savedEngagementCount;
    }

    /** 현재 기수에 저장된 콘텐츠 정보와 성과 조회 */
    List<StoredContentFetch> fetchStoredContents() {
        Generation generation = generationService.getCurrentActivity();

        // 현재 기수에 저장된 콘텐츠 조회
        List<Content> contents = contentRepository.findAllByGenerationId(generation.getId());
        Map<Content, FetchResult> fetchedByContent = new HashMap<>();

        // 플랫폼별 SNS ID를 묶어서 정보와 성과 조회
        for (SnsPlatform platform : SnsPlatform.values()) {
            List<Content> platformContents = contents.stream()
                    .filter(content -> content.getSnsCode() == platform)
                    .toList();
            if (platformContents.isEmpty()) {
                continue;
            }

            ContentFetcher fetcher = findFetcher(platform);
            List<String> snsContentIds = platformContents.stream()
                    .map(Content::getSnsContentId)
                    .toList();
            Map<String, FetchResult> fetchedById = fetcher
                    .fetchByContentIds(snsContentIds)
                    .stream()
                    .collect(Collectors.toMap(FetchResult::snsContentId, result -> result));

            for (Content content : platformContents) {
                fetchedByContent.put(content, Objects.requireNonNull(
                        fetchedById.get(content.getSnsContentId()),
                        "콘텐츠 조회 결과가 없습니다. id=" + content.getSnsContentId()));
            }
        }

        // DB 콘텐츠와 SNS 조회 결과 연결
        return contents.stream()
                .map(content -> new StoredContentFetch(content, fetchedByContent.get(content)))
                .toList();
    }

    private ContentFetcher findFetcher(SnsPlatform platform) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports() == platform)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "콘텐츠 Fetcher가 없습니다. platform=" + platform));
    }

    private ContentEngagement engagement(
            StoredContentFetch result, LocalDateTime collectedAt) {
        ContentFetcher.Engagement engagement = result.fetched().engagement();
        return ContentEngagement.builder()
                .contentId(result.content().getId())
                .viewCount(engagement.viewCount())
                .likeCount(engagement.likeCount())
                .commentCount(engagement.commentCount())
                .shareCount(engagement.shareCount())
                .createdAt(collectedAt)
                .build();
    }

    private int saveEngagement(StoredContentFetch result, LocalDateTime collectedAt) {
        if (result.fetched().status() != ContentFetcher.FetchStatus.FOUND
                || result.fetched().engagement() == null
                || engagementRepository.existsByContentIdAndCreatedAt(
                        result.content().getId(), collectedAt)) {
            return 0;
        }
        engagementRepository.saveAll(List.of(engagement(result, collectedAt)));
        return 1;
    }

    private boolean saveChangedVersion(
            StoredContentFetch result, LocalDateTime collectedAt) {
        if (result.fetched().status() != ContentFetcher.FetchStatus.FOUND) {
            return false;
        }

        Long contentId = result.content().getId();
        ContentVersion current = versionRepository
                .findCurrentByContentIdIn(List.of(contentId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new NullPointerException(
                        "현재 콘텐츠 버전이 없습니다. contentId=" + contentId));
        var fetchedContent = Objects.requireNonNull(
                result.fetched().content(),
                "조회된 콘텐츠 정보가 없습니다. contentId=" + contentId);
        if (current.getContentHash().equals(snapshotFactory.contentHash(fetchedContent))) {
            return false;
        }

        ContentVersion newVersion = snapshotFactory.createVersion(
                contentId,
                result.content().advanceVersion(),
                fetchedContent,
                collectedAt);
        newVersion = versionRepository.saveAll(List.of(newVersion)).getFirst();
        List<ContentMedia> media = snapshotFactory.createMedia(
                newVersion.getId(), fetchedContent);
        if (!media.isEmpty()) {
            mediaRepository.saveAll(media);
        }
        return true;
    }

    private boolean updateDeletionStatus(StoredContentFetch result) {
        Content content = result.content();
        if (result.fetched().status() == ContentFetcher.FetchStatus.NOT_FOUND
                && !content.isDeleted()) {
            content.markDeleted();
            return true;
        }
        if (result.fetched().status() == ContentFetcher.FetchStatus.FOUND
                && content.isDeleted()) {
            content.restore();
            return true;
        }
        return false;
    }

    record StoredContentFetch(Content content, FetchResult fetched) {
    }

    public record StoredContentResult(int savedEngagementCount, int failedContentCount) {
    }
}
