package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchResult;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
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
    private final ContentBatchAccountRepository accountRepository;
    private final List<ContentFetcher> fetchers;
    private final ContentEngagementRepository engagementRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentMediaRepository mediaRepository;
    private final ContentSnapshotFactory snapshotFactory;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 현재 기수 콘텐츠의 성과와 수정 내용을 저장합니다. */
    public StoredContentResult check() {
        return check(progress -> {
        });
    }

    public StoredContentResult check(Consumer<StoredContentProgress> progress) {
        Objects.requireNonNull(progress, "진행 콜백은 필수입니다.");
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);
        List<StoredContentFetch> results = fetchStoredContents(totalContentCount ->
                progress.accept(new StoredContentProgress(totalContentCount, 0, 0)));
        int savedEngagementCount = 0;
        int failedContentCount = 0;
        int checkedContentCount = 0;
        Map<SnsPlatform, PlatformStoredContentStats> platformStats =
                new EnumMap<>(SnsPlatform.class);

        for (StoredContentFetch result : results) {
            SnsPlatform platform = result.content().getSnsCode();
            if (result.fetched().status() == ContentFetcher.FetchStatus.FAILED) {
                failedContentCount++;
                mergeStats(platformStats, platform, 0, 1);
            } else {
                try {
                    StoredContentSaveResult saved = transactionTemplate.execute(
                            status -> save(result, collectedAt));
                    StoredContentSaveResult completed = saved == null
                            ? new StoredContentSaveResult(0, 0)
                            : saved;
                    savedEngagementCount += completed.savedEngagementCount();
                    mergeStats(platformStats, platform, completed.changedVersionCount(), 0);
                } catch (RuntimeException exception) {
                    failedContentCount++;
                    mergeStats(platformStats, platform, 0, 1);
                    log.error(
                            "기존 콘텐츠 저장에 실패했습니다. contentId={}",
                            result.content().getId(),
                            exception);
                }
            }
            checkedContentCount++;
            progress.accept(new StoredContentProgress(
                    results.size(), checkedContentCount, failedContentCount));
        }

        return new StoredContentResult(
                savedEngagementCount,
                failedContentCount,
                checkedContentCount,
                Map.copyOf(platformStats));
    }

    private StoredContentSaveResult save(
            StoredContentFetch result, LocalDateTime collectedAt) {
        if (result.fetched().status() == ContentFetcher.FetchStatus.FAILED) {
            return new StoredContentSaveResult(0, 0);
        }

        int savedEngagementCount = saveEngagement(result, collectedAt);
        boolean versionChanged = saveChangedVersion(result, collectedAt);
        boolean deletionStatusChanged = updateDeletionStatus(result);
        if (versionChanged || deletionStatusChanged) {
            contentRepository.saveAll(List.of(result.content()));
        }
        return new StoredContentSaveResult(savedEngagementCount, versionChanged ? 1 : 0);
    }

    private void mergeStats(
            Map<SnsPlatform, PlatformStoredContentStats> stats,
            SnsPlatform platform,
            int changedVersionCount,
            int failedContentCount) {
        stats.merge(
                platform,
                new PlatformStoredContentStats(changedVersionCount, failedContentCount),
                PlatformStoredContentStats::plus);
    }

    /** 현재 기수에 저장된 콘텐츠 정보와 성과 조회 */
    List<StoredContentFetch> fetchStoredContents() {
        return fetchStoredContents(ignored -> {
        });
    }

    private List<StoredContentFetch> fetchStoredContents(Consumer<Integer> totalProgress) {
        Generation generation = generationService.getCurrentActivity();

        // 현재 기수에 저장된 콘텐츠 조회
        List<Content> contents = contentRepository.findAllByGenerationId(generation.getId());
        totalProgress.accept(contents.size());
        Map<AccountKey, String> accountIds = new HashMap<>();
        for (SelectorsSnsAccount account : accountRepository
                .findAllByGenerationId(generation.getId())) {
            accountIds.put(
                    new AccountKey(account.getSelectorsId(), account.getSnsCode()),
                    account.getAccountId());
        }
        Map<Content, FetchResult> fetchedByContent = new HashMap<>();

        // 플랫폼별 SNS ID를 묶어서 정보와 성과 조회
        for (SnsPlatform platform : SnsPlatform.values()) {
            List<Content> platformContents = contents.stream()
                    .filter(content -> content.getSnsCode() == platform)
                    .toList();
            if (platformContents.isEmpty()) {
                continue;
            }

            try {
                ContentFetcher fetcher = findFetcher(platform);
                if (platform == SnsPlatform.INSTAGRAM) {
                    Map<Long, List<Content>> contentsBySelectors = platformContents.stream()
                            .collect(Collectors.groupingBy(Content::getSelectorsId));
                    for (Map.Entry<Long, List<Content>> entry : contentsBySelectors.entrySet()) {
                        try {
                            String accountId = Objects.requireNonNull(
                                    accountIds.get(new AccountKey(entry.getKey(), platform)),
                                    "활성 SNS 계정이 없습니다. selectorsId=" + entry.getKey());
                            attachFetchResults(
                                    entry.getValue(),
                                    fetcher.fetchByAccountContentIds(
                                            accountId, snsContentIds(entry.getValue())),
                                    fetchedByContent);
                        } catch (RuntimeException exception) {
                            log.error(
                                    "SNS 계정의 기존 콘텐츠 조회에 실패했습니다. platform={} selectorsId={}",
                                    platform,
                                    entry.getKey(),
                                    exception);
                            attachFailedResults(entry.getValue(), fetchedByContent);
                        }
                    }
                } else {
                    attachFetchResults(
                            platformContents,
                            fetcher.fetchByContentIds(snsContentIds(platformContents)),
                            fetchedByContent);
                }
            } catch (RuntimeException exception) {
                log.error("플랫폼의 기존 콘텐츠 조회에 실패했습니다. platform={}",
                        platform, exception);
                attachFailedResults(platformContents, fetchedByContent);
            }
        }

        // DB 콘텐츠와 SNS 조회 결과 연결
        return contents.stream()
                .map(content -> new StoredContentFetch(content, fetchedByContent.get(content)))
                .toList();
    }

    private List<String> snsContentIds(List<Content> contents) {
        return contents.stream()
                .map(Content::getSnsContentId)
                .toList();
    }

    private void attachFetchResults(
            List<Content> contents,
            List<FetchResult> results,
            Map<Content, FetchResult> fetchedByContent) {
        Map<String, FetchResult> fetchedById = results.stream()
                .collect(Collectors.toMap(FetchResult::snsContentId, result -> result));
        for (Content content : contents) {
            fetchedByContent.put(content, fetchedById.getOrDefault(
                    content.getSnsContentId(), failed(content)));
        }
    }

    private void attachFailedResults(
            List<Content> contents, Map<Content, FetchResult> fetchedByContent) {
        for (Content content : contents) {
            fetchedByContent.put(content, failed(content));
        }
    }

    private FetchResult failed(Content content) {
        return new FetchResult(
                content.getSnsContentId(), ContentFetcher.FetchStatus.FAILED, null, null);
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
                collectedAt,
                ContentVersionCreationReason.SOURCE_CHANGE);
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

    private record StoredContentSaveResult(
            int savedEngagementCount, int changedVersionCount) {
    }

    private record AccountKey(Long selectorsId, SnsPlatform platform) {
    }

    public record PlatformStoredContentStats(
            int changedVersionCount, int failedContentCount) {

        private PlatformStoredContentStats plus(PlatformStoredContentStats other) {
            return new PlatformStoredContentStats(
                    changedVersionCount + other.changedVersionCount,
                    failedContentCount + other.failedContentCount);
        }
    }

    public record StoredContentResult(
            int savedEngagementCount,
            int failedContentCount,
            int checkedContentCount,
            Map<SnsPlatform, PlatformStoredContentStats> platformStats) {

        public StoredContentResult(int savedEngagementCount, int failedContentCount) {
            this(savedEngagementCount, failedContentCount, 0, Map.of());
        }

        public StoredContentResult(
                int savedEngagementCount,
                int failedContentCount,
                Map<SnsPlatform, PlatformStoredContentStats> platformStats) {
            this(savedEngagementCount, failedContentCount, 0, platformStats);
        }
    }

    public record StoredContentProgress(
            int totalContentCount, int checkedContentCount, int failedContentCount) {
    }
}
