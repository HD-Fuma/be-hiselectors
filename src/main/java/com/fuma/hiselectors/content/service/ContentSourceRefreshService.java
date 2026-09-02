package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchResult;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.dto.ContentSourceRefreshResponse;
import com.fuma.hiselectors.content.dto.ContentSourceRefreshResponse.Item;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 콘텐츠 배치가 계정 피드로 못 가져온 DB 저장 콘텐츠를
 * 저장된 링크·SNS ID로 다시 조회해 메타와 성과를 채운다.
 *
 * <p>검수 중인 최신 버전을 유지한 채 제목·본문과 성과 스냅샷만 갱신한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContentSourceRefreshService {

    private final GenerationService generationService;
    private final ContentRepository contentRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentMediaRepository mediaRepository;
    private final ContentEngagementRepository engagementRepository;
    private final SelectorsSnsAccountRepository accountRepository;
    private final List<ContentFetcher> fetchers;
    private final ContentSnapshotFactory snapshotFactory;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ContentSourceRefreshResponse refresh(Long contentId) {
        List<Content> targets = contentId == null ? incompleteCurrentGeneration() : List.of(required(contentId));
        if (targets.isEmpty()) {
            return new ContentSourceRefreshResponse(0, 0, 0, 0, 0, List.of());
        }

        Map<Long, SelectorsSnsAccount> accounts = accountsBySelectorsId(targets);
        Map<Content, String> fetchIds = new LinkedHashMap<>();
        List<Item> unresolved = new ArrayList<>();
        for (Content content : targets) {
            String fetchId = SnsContentIdResolver.resolve(
                    content.getSnsCode(), content.getSnsContentId(), content.getContentUrl());
            if (fetchId == null) {
                unresolved.add(failedItem(content, "콘텐츠 링크에서 SNS 조회 키를 찾지 못했습니다."));
                continue;
            }
            fetchIds.put(content, fetchId);
        }

        Map<Content, FetchResult> fetched = fetch(fetchIds, accounts);
        Map<Long, ContentFetcher.Profile> profiles = fetchProfiles(
                foundContents(fetched), accounts);
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);

        List<Item> results = new ArrayList<>(unresolved);
        for (Map.Entry<Content, String> entry : fetchIds.entrySet()) {
            Content content = entry.getKey();
            FetchResult result = fetched.get(content);
            if (result == null || result.status() == ContentFetcher.FetchStatus.FAILED) {
                results.add(failedItem(content, "SNS 콘텐츠 조회에 실패했습니다."));
                continue;
            }
            if (result.status() == ContentFetcher.FetchStatus.NOT_FOUND) {
                results.add(failedItem(content, "SNS에서 콘텐츠를 찾지 못했습니다."));
                continue;
            }
            try {
                Item saved = transactionTemplate.execute(status -> save(
                        content,
                        entry.getValue(),
                        result,
                        profiles.get(content.getSelectorsId()),
                        collectedAt));
                results.add(saved == null
                        ? failedItem(content, "콘텐츠 저장 중 오류가 발생했습니다.")
                        : saved);
            } catch (RuntimeException exception) {
                log.error("기존 콘텐츠 원본 갱신에 실패했습니다. contentId={}", content.getId(), exception);
                results.add(failedItem(content, "콘텐츠 저장 중 오류가 발생했습니다."));
            }
        }

        return summary(targets.size(), results);
    }

    private Content required(Long contentId) {
        return contentRepository.findById(contentId)
                .filter(content -> !content.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
    }

    private List<Content> incompleteCurrentGeneration() {
        Generation generation = generationService.getCurrentActivity();
        List<Content> contents = contentRepository.findAllByGenerationId(generation.getId()).stream()
                .filter(content -> !content.isDeleted())
                .toList();
        if (contents.isEmpty()) {
            return List.of();
        }
        List<Long> contentIds = contents.stream().map(Content::getId).toList();
        Map<Long, ContentVersion> versions = versionRepository
                .findCurrentByContentIdIn(contentIds).stream()
                .collect(Collectors.toMap(ContentVersion::getContentId, version -> version));
        Set<Long> versionIds = versions.values().stream()
                .map(ContentVersion::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<ContentMedia>> mediaByVersionId = new HashMap<>();
        if (!versionIds.isEmpty()) {
            for (ContentMedia media : mediaRepository
                    .findAllByContentVersionIdInOrderByContentVersionIdAscSequenceNoAsc(versionIds)) {
                mediaByVersionId.computeIfAbsent(media.getContentVersionId(), ignored ->
                        new ArrayList<>()).add(media);
            }
        }
        Set<Long> engagedContentIds = engagementRepository.findLatestByContentIds(contentIds).stream()
                .map(ContentEngagement::getContentId)
                .collect(Collectors.toSet());
        return contents.stream()
                .filter(content -> incomplete(
                        versions.get(content.getId()),
                        mediaByVersionId,
                        engagedContentIds.contains(content.getId())))
                .toList();
    }

    private boolean incomplete(
            ContentVersion version,
            Map<Long, List<ContentMedia>> mediaByVersionId,
            boolean hasEngagement) {
        if (!hasEngagement) {
            return true;
        }
        if (version == null || version.getId() == null) {
            return true;
        }
        List<ContentMedia> media = mediaByVersionId.getOrDefault(version.getId(), List.of());
        return media.stream().noneMatch(item -> item.getMediaType() == MediaType.TEXT);
    }

    private Map<Long, SelectorsSnsAccount> accountsBySelectorsId(List<Content> contents) {
        List<Long> selectorsIds = contents.stream()
                .map(Content::getSelectorsId)
                .distinct()
                .toList();
        Map<Long, SelectorsSnsAccount> accounts = new HashMap<>();
        for (SelectorsSnsAccount account : accountRepository
                .findAllBySelectorsIdInAndDeletedFalse(selectorsIds)) {
            accounts.put(account.getSelectorsId(), account);
        }
        return accounts;
    }

    private Map<Content, FetchResult> fetch(
            Map<Content, String> fetchIds, Map<Long, SelectorsSnsAccount> accounts) {
        Map<Content, FetchResult> fetched = new HashMap<>();
        Map<SnsPlatform, List<Content>> byPlatform = fetchIds.keySet().stream()
                .collect(Collectors.groupingBy(
                        Content::getSnsCode, () -> new EnumMap<>(SnsPlatform.class), Collectors.toList()));
        for (Map.Entry<SnsPlatform, List<Content>> platformEntry : byPlatform.entrySet()) {
            ContentFetcher fetcher = findFetcher(platformEntry.getKey());
            if (platformEntry.getKey() == SnsPlatform.INSTAGRAM) {
                fetchInstagram(fetcher, platformEntry.getValue(), fetchIds, accounts, fetched);
                continue;
            }
            attachFetchResults(
                    platformEntry.getValue(),
                    fetchIds,
                    fetcher.fetchByContentIds(fetchIdsFor(platformEntry.getValue(), fetchIds)),
                    fetched);
        }
        return fetched;
    }

    private void fetchInstagram(
            ContentFetcher fetcher,
            List<Content> contents,
            Map<Content, String> fetchIds,
            Map<Long, SelectorsSnsAccount> accounts,
            Map<Content, FetchResult> fetched) {
        Map<Long, List<Content>> bySelectors = contents.stream()
                .collect(Collectors.groupingBy(Content::getSelectorsId));
        for (Map.Entry<Long, List<Content>> entry : bySelectors.entrySet()) {
            SelectorsSnsAccount account = accounts.get(entry.getKey());
            if (account == null || account.getSnsCode() != SnsPlatform.INSTAGRAM
                    || account.getAccountId() == null || account.getAccountId().isBlank()) {
                attachFailedResults(entry.getValue(), fetchIds, fetched);
                continue;
            }
            try {
                attachFetchResults(
                        entry.getValue(),
                        fetchIds,
                        fetcher.fetchByAccountContentIds(
                                account.getAccountId(), fetchIdsFor(entry.getValue(), fetchIds)),
                        fetched);
            } catch (RuntimeException exception) {
                log.error(
                        "Instagram 기존 콘텐츠 조회에 실패했습니다. selectorsId={}",
                        entry.getKey(),
                        exception);
                attachFailedResults(entry.getValue(), fetchIds, fetched);
            }
        }
    }

    private List<String> fetchIdsFor(List<Content> contents, Map<Content, String> fetchIds) {
        return contents.stream().map(fetchIds::get).toList();
    }

    private void attachFetchResults(
            List<Content> contents,
            Map<Content, String> fetchIds,
            List<FetchResult> results,
            Map<Content, FetchResult> fetched) {
        Map<String, FetchResult> byId = new HashMap<>();
        for (FetchResult result : results) {
            byId.putIfAbsent(result.snsContentId(), result);
        }
        for (Content content : contents) {
            fetched.put(content, byId.getOrDefault(
                    fetchIds.get(content),
                    new FetchResult(
                            fetchIds.get(content), ContentFetcher.FetchStatus.FAILED, null, null)));
        }
    }

    private void attachFailedResults(
            List<Content> contents,
            Map<Content, String> fetchIds,
            Map<Content, FetchResult> fetched) {
        for (Content content : contents) {
            fetched.put(content, new FetchResult(
                    fetchIds.get(content), ContentFetcher.FetchStatus.FAILED, null, null));
        }
    }

    private Set<Content> foundContents(Map<Content, FetchResult> fetched) {
        return fetched.entrySet().stream()
                .filter(entry -> entry.getValue() != null
                        && entry.getValue().status() == ContentFetcher.FetchStatus.FOUND)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Map<Long, ContentFetcher.Profile> fetchProfiles(
            Set<Content> contents, Map<Long, SelectorsSnsAccount> accounts) {
        Map<Long, ContentFetcher.Profile> profiles = new HashMap<>();
        for (Content content : contents) {
            Long selectorsId = content.getSelectorsId();
            if (profiles.containsKey(selectorsId)) {
                continue;
            }
            SelectorsSnsAccount account = accounts.get(selectorsId);
            if (account == null || account.getSnsCode() != content.getSnsCode()
                    || account.getAccountId() == null || account.getAccountId().isBlank()) {
                profiles.put(selectorsId, null);
                continue;
            }
            try {
                profiles.put(selectorsId, findFetcher(content.getSnsCode())
                        .fetchProfile(account.getAccountId()));
            } catch (RuntimeException exception) {
                log.warn(
                        "콘텐츠 작성자 프로필 조회에 실패했습니다. selectorsId={} platform={}",
                        selectorsId,
                        content.getSnsCode(),
                        exception);
                profiles.put(selectorsId, null);
            }
        }
        return profiles;
    }

    private Item save(
            Content content,
            String fetchId,
            FetchResult fetched,
            ContentFetcher.Profile profile,
            LocalDateTime collectedAt) {
        Content locked = contentRepository.findByIdForUpdate(content.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        ContentVersion version = versionRepository.findCurrentByContentIdIn(List.of(locked.getId()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTENT_NOT_FOUND));
        RawContent raw = Objects.requireNonNull(fetched.content(), "조회된 콘텐츠 정보가 없습니다.");
        List<ContentMedia> media = new ArrayList<>(mediaRepository
                .findByContentVersionIdOrderBySequenceNoAsc(version.getId()));
        boolean textsUpdated = upsertTexts(version.getId(), media, raw.texts());
        boolean engagementUpdated = saveEngagement(locked.getId(), fetched.engagement(), collectedAt);
        boolean contentChanged = locked.updateSnsContentId(fetchId);
        if (version.replaceContentHash(snapshotFactory.contentHash(raw))) {
            versionRepository.save(version);
        }
        boolean profileImageUpdated = applyProfile(locked.getSelectorsId(), profile);
        if (contentChanged) {
            contentRepository.save(locked);
        }
        List<String> texts = media.stream()
                .filter(item -> item.getMediaType() == MediaType.TEXT)
                .map(item -> item.bodyOrEmpty().get("text"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        ContentFetcher.Engagement engagement = fetched.engagement();
        SelectorsSnsAccount account = accountRepository
                .findBySelectorsIdAndDeletedFalse(locked.getSelectorsId())
                .orElse(null);
        return new Item(
                locked.getId(),
                locked.getSelectorsId(),
                account == null ? null : account.getProfileImageUrl(),
                profileImageUpdated,
                texts,
                textsUpdated,
                engagement == null ? null : engagement.viewCount(),
                engagement == null ? null : engagement.likeCount(),
                engagement == null ? null : engagement.commentCount(),
                engagementUpdated,
                null);
    }

    private boolean upsertTexts(Long versionId, List<ContentMedia> media, List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return false;
        }
        List<ContentMedia> existingTexts = media.stream()
                .filter(item -> item.getMediaType() == MediaType.TEXT)
                .toList();
        boolean updated = false;
        List<ContentMedia> dirty = new ArrayList<>();
        int nextSequence = media.stream()
                .map(ContentMedia::getSequenceNo)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(-1) + 1;
        for (int index = 0; index < texts.size(); index++) {
            String text = texts.get(index);
            if (index < existingTexts.size()) {
                ContentMedia existing = existingTexts.get(index);
                Object current = existing.bodyOrEmpty().get("text");
                if (!text.equals(current)) {
                    existing.replaceBody(Map.of("text", text));
                    dirty.add(existing);
                    updated = true;
                }
                continue;
            }
            ContentMedia createdText = ContentMedia.create(
                    versionId, MediaType.TEXT, null, null, nextSequence++, Map.of("text", text));
            dirty.add(createdText);
            media.add(createdText);
            updated = true;
        }
        if (!dirty.isEmpty()) {
            mediaRepository.saveAll(dirty);
        }
        return updated;
    }

    private boolean saveEngagement(
            Long contentId, ContentFetcher.Engagement engagement, LocalDateTime collectedAt) {
        if (engagement == null
                || engagementRepository.existsByContentIdAndCreatedAt(contentId, collectedAt)) {
            return false;
        }
        engagementRepository.saveAll(List.of(ContentEngagement.builder()
                .contentId(contentId)
                .viewCount(engagement.viewCount())
                .likeCount(engagement.likeCount())
                .commentCount(engagement.commentCount())
                .shareCount(engagement.shareCount())
                .createdAt(collectedAt)
                .build()));
        return true;
    }

    private boolean applyProfile(Long selectorsId, ContentFetcher.Profile profile) {
        if (profile == null || profile.imageUrl() == null || profile.imageUrl().isBlank()) {
            return false;
        }
        return accountRepository.findBySelectorsIdAndDeletedFalseForUpdate(selectorsId)
                .map(account -> {
                    String before = account.getProfileImageUrl();
                    account.applyPublicProfile(profile.imageUrl(), profile.followerCount(), true);
                    return !Objects.equals(before, account.getProfileImageUrl());
                })
                .orElse(false);
    }

    private ContentFetcher findFetcher(SnsPlatform platform) {
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports() == platform)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "콘텐츠 Fetcher가 없습니다. platform=" + platform));
    }

    private Item failedItem(Content content, String reason) {
        return new Item(
                content.getId(),
                content.getSelectorsId(),
                null,
                false,
                List.of(),
                false,
                null,
                null,
                null,
                false,
                reason);
    }

    private ContentSourceRefreshResponse summary(int targetCount, List<Item> results) {
        return new ContentSourceRefreshResponse(
                targetCount,
                (int) results.stream().filter(Item::profileImageUpdated).count(),
                (int) results.stream().filter(Item::textsUpdated).count(),
                (int) results.stream().filter(Item::engagementUpdated).count(),
                (int) results.stream().filter(item -> item.failureReason() != null).count(),
                results);
    }
}
