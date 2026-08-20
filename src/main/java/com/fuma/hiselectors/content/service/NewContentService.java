package com.fuma.hiselectors.content.service;

import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentFetcher;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.content.repository.ContentBatchAccountRepository;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.generation.model.Generation;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
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
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** 신규 셀렉터스 콘텐츠와 최초 버전을 저장합니다. */
    public int collect() {
        LocalDateTime collectedAt = LocalDateTime.now(clock).withNano(0);
        int savedCount = 0;

        for (CollectionTarget target : collectionTargets()) {
            try {
                List<RawContent> candidates = newCandidates(target);
                Integer saved = transactionTemplate.execute(status ->
                        save(target.account(), candidates, collectedAt));
                savedCount += saved == null ? 0 : saved;
            } catch (RuntimeException exception) {
                log.error("신규 콘텐츠 수집에 실패했습니다. accountId={}",
                        target.account().getAccountId(), exception);
            }
        }
        return savedCount;
    }

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

    private int save(
            SelectorsSnsAccount account,
            List<RawContent> rawContents,
            LocalDateTime collectedAt) {
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
                versions.add(ContentVersion.builder()
                        .contentId(contents.get(index).getId())
                        .versionNo(1L)
                        .contentHash(contentHash(rawContents.get(index)))
                        .createdAt(collectedAt)
                        .build());
            }
            versions = versionRepository.saveAll(versions);

            List<ContentMedia> media = new ArrayList<>();
            for (int index = 0; index < versions.size(); index++) {
                addMedia(media, versions.get(index).getId(), rawContents.get(index));
            }
            mediaRepository.saveAll(media);
        }

        account.completeCollection(collectedAt);
        accountRepository.save(account);
        return rawContents.size();
    }

    private void addMedia(
            List<ContentMedia> target, Long versionId, RawContent rawContent) {
        int sequenceNo = 0;
        for (String text : rawContent.texts()) {
            target.add(ContentMedia.create(
                    versionId, MediaType.TEXT, null, null, sequenceNo++,
                    Map.of("text", text)));
        }
        for (RawContentMedia rawMedia : rawContent.media()) {
            if (rawMedia.mediaType() == RawContentMedia.MediaType.TEXT) {
                throw new IllegalStateException(
                        "RawContentMedia.TEXT는 본문을 제공하지 않아 저장할 수 없습니다.");
            }
            target.add(ContentMedia.create(
                    versionId,
                    MediaType.valueOf(rawMedia.mediaType().name()),
                    rawMedia.mediaUrl(),
                    rawMedia.snsMediaId(),
                    sequenceNo++,
                    Map.of()));
        }
    }

    private String contentHash(RawContent rawContent) {
        StringBuilder canonical = new StringBuilder();
        appendHashValue(canonical, "content-hash-v2");
        appendHashValue(canonical, "texts");
        appendHashValue(canonical, String.valueOf(rawContent.texts().size()));
        for (int sequenceNo = 0; sequenceNo < rawContent.texts().size(); sequenceNo++) {
            appendHashValue(canonical, String.valueOf(sequenceNo));
            appendHashValue(canonical, rawContent.texts().get(sequenceNo));
        }

        appendHashValue(canonical, "assets");
        appendHashValue(canonical, String.valueOf(rawContent.media().size()));
        int sequenceNo = rawContent.texts().size();
        for (RawContentMedia media : rawContent.media()) {
            if (media.snsMediaId() == null) {
                throw new IllegalStateException("미디어 SNS ID는 비어 있을 수 없습니다.");
            }
            appendHashValue(canonical, String.valueOf(sequenceNo++));
            appendHashValue(canonical, media.mediaType().name());
            appendHashValue(canonical, media.snsMediaId());
        }

        try {
            byte[] value = canonical.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void appendHashValue(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    record CollectionTarget(SelectorsSnsAccount account, LocalDateTime since) {
    }
}
