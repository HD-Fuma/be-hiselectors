package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NewContentService {

    private final GenerationService generationService;
    private final ContentBatchAccountRepository accountRepository;
    private final List<ContentFetcher> fetchers;
    private final SelectorsContentClassifier classifier;
    private final ContentRepository contentRepository;

    /** 현재 기수의 계정별 수집 시작 시각 결정 */
    List<CollectionTarget> collectionTargets() {
        Generation generation = generationService.getActive();
        return accountRepository.findAllByGenerationId(generation.getId()).stream()
                .map(account -> new CollectionTarget(
                        account, since(account, generation.getStartDate())))
                .toList();
    }

    List<RawContent> newCandidates(CollectionTarget target) {
        SelectorsSnsAccount account = target.account();

        // 계정 플랫폼에 맞는 Fetcher로 신규 후보 조회
        ContentFetcher fetcher = fetchers.stream()
                .filter(candidate -> candidate.supports() == account.getSnsCode())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "콘텐츠 Fetcher가 없습니다. platform=" + account.getSnsCode()));
        List<RawContent> contents = Objects.requireNonNull(
                fetcher.fetchByAccount(account.getAccountId(), target.since()).contents());

        // 같은 응답에 중복된 SNS 콘텐츠 제거
        Set<String> collectedIds = new HashSet<>();
        List<RawContent> uniqueContents = new ArrayList<>();
        for (RawContent content : contents) {
            if (content == null || content.snsCode() != account.getSnsCode()) {
                throw new IllegalStateException("수집 계정과 콘텐츠 플랫폼이 일치하지 않습니다.");
            }
            if (collectedIds.add(content.snsContentId())) {
                uniqueContents.add(content);
            }
        }
        if (uniqueContents.isEmpty()) {
            return List.of();
        }

        List<String> candidateIds = uniqueContents.stream()
                .map(RawContent::snsContentId)
                .toList();

        // 이미 저장된 콘텐츠 제외
        Set<String> existingIds = new HashSet<>();
        for (Content content : contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                account.getSnsCode(), candidateIds)) {
            existingIds.add(content.getSnsContentId());
        }

        // 신규 콘텐츠 중 셀렉터스 콘텐츠만 반환
        return uniqueContents.stream()
                .filter(content -> !existingIds.contains(content.snsContentId()))
                .filter(classifier::isSelectorsContent)
                .toList();
    }

    private LocalDateTime since(
            SelectorsSnsAccount account, LocalDateTime generationStart) {
        LocalDateTime lastCollectedAt = account.getLastCollectedAt();
        return lastCollectedAt == null || lastCollectedAt.isBefore(generationStart)
                ? generationStart
                : lastCollectedAt;
    }

    record CollectionTarget(SelectorsSnsAccount account, LocalDateTime since) {
    }
}
