package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.ContentFetcher.FetchResult;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentEngagement;
import com.fuma.hiselectors.content.repository.ContentEngagementRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StoredContentService {

    private final GenerationService generationService;
    private final ContentRepository contentRepository;
    private final List<ContentFetcher> fetchers;
    private final ContentEngagementRepository engagementRepository;
    private final Clock clock;

    /** 현재 기수 콘텐츠의 성과를 저장합니다. */
    public int check() {
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);
        List<ContentEngagement> engagements = fetchStoredContents().stream()
                .filter(result -> result.fetched().status()
                        == ContentFetcher.FetchStatus.FOUND)
                .filter(result -> result.fetched().engagement() != null)
                .map(result -> engagement(result, collectedAt))
                .toList();

        if (!engagements.isEmpty()) {
            engagementRepository.saveAll(engagements);
        }
        return engagements.size();
    }

    /** 현재 기수에 저장된 콘텐츠 정보와 성과 조회 */
    List<StoredContentResult> fetchStoredContents() {
        Generation generation = generationService.getActive();

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
                .map(content -> new StoredContentResult(content, fetchedByContent.get(content)))
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
            StoredContentResult result, LocalDateTime collectedAt) {
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

    record StoredContentResult(Content content, FetchResult fetched) {
    }
}
