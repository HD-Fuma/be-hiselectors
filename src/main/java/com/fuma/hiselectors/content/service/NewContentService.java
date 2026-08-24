package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.ContentVersionCreationReason;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewContentService {

    private final GenerationService generationService;
    private final ContentBatchAccountRepository accountRepository;
    private final List<ContentFetcher> fetchers;
    private final SelectorsContentClassifier classifier;
    private final ContentRepository contentRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentMediaRepository mediaRepository;
    private final ContentSnapshotFactory snapshotFactory;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 신규 셀렉터스 콘텐츠와 최초 버전을 저장합니다. */
    public NewContentResult collect() {
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);
        int savedCount = 0;
        int failedAccountCount = 0;
        Map<SnsPlatform, PlatformCollectionStats> platformStats =
                new EnumMap<>(SnsPlatform.class);

        for (CollectionTarget target : collectionTargets()) {
            NewContentSelection selection = null;
            try {
                NewContentSelection selected = newCandidates(target);
                selection = selected;
                Integer saved = transactionTemplate.execute(status ->
                        save(target.account(), selected.selectorsContents(), collectedAt));
                int savedVersions = saved == null ? 0 : saved;
                savedCount += savedVersions;
                mergeStats(platformStats, target.account().getSnsCode(),
                        selection, savedVersions, 0);
            } catch (RuntimeException exception) {
                failedAccountCount++;
                mergeStats(platformStats, target.account().getSnsCode(), selection, 0, 1);
                log.error("신규 콘텐츠 수집에 실패했습니다. accountId={}",
                        target.account().getAccountId(), exception);
            }
        }
        return new NewContentResult(savedCount, failedAccountCount, Map.copyOf(platformStats));
    }

    /** 현재 기수의 계정별 수집 시작 시각 결정 */
    List<CollectionTarget> collectionTargets() {
        Generation generation = generationService.getCurrentActivity();
        return accountRepository.findAllByGenerationId(generation.getId()).stream()
                .map(account -> new CollectionTarget(
                        account, since(account, generation.getActivityStartDate())))
                .toList();
    }

    NewContentSelection newCandidates(CollectionTarget target) {
        SelectorsSnsAccount account = target.account();

        // 계정 플랫폼에 맞는 Fetcher로 신규 후보 조회
        ContentFetcher fetcher = fetchers.stream()
                .filter(candidate -> candidate.supports() == account.getSnsCode())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "콘텐츠 Fetcher가 없습니다. platform=" + account.getSnsCode()));
        List<RawContent> contents = Objects.requireNonNull(
                fetcher.fetchByAccount(account.getAccountId(), target.since()));

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
            return new NewContentSelection(0, List.of());
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

        List<RawContent> newContents = uniqueContents.stream()
                .filter(content -> !existingIds.contains(content.snsContentId()))
                .toList();

        // 신규 콘텐츠 중 셀렉터스 콘텐츠만 반환
        List<RawContent> selectorsContents = newContents.stream()
                .filter(classifier::isSelectorsContent)
                .toList();
        return new NewContentSelection(newContents.size(), selectorsContents);
    }

    private void mergeStats(
            Map<SnsPlatform, PlatformCollectionStats> stats,
            SnsPlatform platform,
            NewContentSelection selection,
            int savedVersionCount,
            int failedAccountCount) {
        PlatformCollectionStats update = new PlatformCollectionStats(
                selection == null ? 0 : selection.candidateCount(),
                selection == null ? 0 : selection.selectorsContents().size(),
                savedVersionCount,
                failedAccountCount);
        stats.merge(platform, update, PlatformCollectionStats::plus);
    }

    private LocalDateTime since(
            SelectorsSnsAccount account, LocalDateTime generationStart) {
        LocalDateTime lastCollectedAt = account.getLastCollectedAt();
        return lastCollectedAt == null || lastCollectedAt.isBefore(generationStart)
                ? generationStart
                : lastCollectedAt;
    }

    private int save(
            SelectorsSnsAccount account,
            List<RawContent> rawContents,
            LocalDateTime collectedAt) {
        if (accountRepository.advanceCollectionCursorIfAccountUnchanged(
                account.getId(), account.getSnsCode(), account.getAccountId(), collectedAt) != 1) {
            throw new IllegalStateException("SNS 계정이 수집 중 변경됐습니다.");
        }
        if (!rawContents.isEmpty()) {
            List<Content> contents = contentRepository.saveAll(rawContents.stream()
                    .map(raw -> Content.builder()
                            .selectorsId(account.getSelectorsId())
                            .snsCode(raw.snsCode())
                            .snsContentId(raw.snsContentId())
                            .contentUrl(raw.contentUrl())
                            .contentType(raw.contentType())
                            .lastVersionNo(1L)
                            .build())
                    .toList());

            List<ContentVersion> versions = new ArrayList<>();
            for (int index = 0; index < contents.size(); index++) {
                versions.add(snapshotFactory.createVersion(
                        contents.get(index).getId(),
                        1L,
                        rawContents.get(index),
                        collectedAt,
                        ContentVersionCreationReason.INITIAL));
            }
            versions = versionRepository.saveAll(versions);

            List<ContentMedia> media = new ArrayList<>();
            for (int index = 0; index < versions.size(); index++) {
                media.addAll(snapshotFactory.createMedia(
                        versions.get(index).getId(), rawContents.get(index)));
            }
            mediaRepository.saveAll(media);
        }

        return rawContents.size();
    }

    record CollectionTarget(SelectorsSnsAccount account, LocalDateTime since) {
    }

    record NewContentSelection(int candidateCount, List<RawContent> selectorsContents) {
    }

    public record PlatformCollectionStats(
            int candidateCount,
            int selectorsContentCount,
            int savedVersionCount,
            int failedAccountCount) {

        private PlatformCollectionStats plus(PlatformCollectionStats other) {
            return new PlatformCollectionStats(
                    candidateCount + other.candidateCount,
                    selectorsContentCount + other.selectorsContentCount,
                    savedVersionCount + other.savedVersionCount,
                    failedAccountCount + other.failedAccountCount);
        }
    }

    public record NewContentResult(
            int savedContentCount,
            int failedAccountCount,
            Map<SnsPlatform, PlatformCollectionStats> platformStats) {

        public NewContentResult(int savedContentCount, int failedAccountCount) {
            this(savedContentCount, failedAccountCount, Map.of());
        }
    }
}
