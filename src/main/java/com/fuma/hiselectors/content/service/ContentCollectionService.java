package com.fuma.hiselectors.content.service;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.classifier.SelectorsContentClassifier;
import com.fuma.hiselectors.content.client.ContentPlatformClient;
import com.fuma.hiselectors.content.client.ContentPlatformClient.CollectionResult;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.ContentVersion;
import com.fuma.hiselectors.content.repository.ContentMediaRepository;
import com.fuma.hiselectors.content.repository.ContentRepository;
import com.fuma.hiselectors.content.repository.ContentVersionRepository;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.generation.service.GenerationService;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
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

/** 콘텐츠 수집 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCollectionService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final List<ContentPlatformClient> contentClients;
    private final SelectorsContentClassifier classifier;
    private final GenerationService generationService;
    private final SelectorsSnsAccountRepository accountRepository;
    private final ContentRepository contentRepository;
    private final ContentVersionRepository versionRepository;
    private final ContentMediaRepository mediaRepository;
    private final TransactionTemplate transactionTemplate;

    /** 지정한 SNS 계정의 콘텐츠 수집 */
    public int collectForAccount(Long selectorsSnsAccountId) {
        // SNS ID 검증
        if (selectorsSnsAccountId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 실행 기준 시각
        LocalDateTime collectionStartedAt = LocalDateTime.now(KOREA_ZONE).withNano(0);

        // SNS 플랫폼별 API 클라이언트 선택
        SelectorsSnsAccount account = findAccount(selectorsSnsAccountId);
        ContentPlatformClient client = findClient(account.getSnsCode());
        LocalDateTime generationStartedAt = generationService.getActive().getStartDate();

        // 외부 API 호출
        CollectionResult collectionResult = Objects.requireNonNull(
                client.collect(account.getAccountId(), generationStartedAt),
                "콘텐츠 수집 결과는 null일 수 없습니다.");
        List<RawContent> collectedContents = collectionResult.contents();

        // DB 저장과 마지막 수집 시각 갱신의 단일 트랜잭션 처리
        int savedCount = Objects.requireNonNull(transactionTemplate.execute(status ->
                persist(selectorsSnsAccountId, collectedContents,
                        generationStartedAt, collectionStartedAt)));

        log.info(
                "콘텐츠 수집 결과 | 플랫폼={} | 계정={} | API 조회={}건 | 현재 기수={}건 "
                        + "| 셀렉터스 게시글={}건",
                account.getSnsCode(), account.getAccountId(), collectionResult.fetchedCount(),
                collectedContents.size(), savedCount);
        return savedCount;
    }

    /** RawContent를 신규와 기존으로 나눠 저장 및 버전 갱신 */
    private int persist(
            Long selectorsSnsAccountId,
            List<RawContent> collectedContents,
            LocalDateTime generationStartedAt,
            LocalDateTime collectionStartedAt) {
        // 트랜잭션 내부에서 SNS 계정 재조회: 마지막 수집 시각 갱신
        SelectorsSnsAccount account = findAccount(selectorsSnsAccountId);

        // 활성 기수 시작 시각 이후 콘텐츠 선별
        List<RawContent> candidates = new ArrayList<>();
        Set<String> collectedContentIds = new HashSet<>();
        LocalDateTime lastCollectedAt = account.getLastCollectedAt();
        for (RawContent content : collectedContents) {
            validatePlatform(content, account.getSnsCode());
            if (!content.createdAt().isBefore(generationStartedAt)
                    && collectedContentIds.add(content.snsContentId())) {
                candidates.add(content);
            }
        }

        // SNS 콘텐츠 ID로 신규 콘텐츠와 기존 콘텐츠 구분
        List<Content> existingContents = findExistingContents(
                candidates, account.getSnsCode());
        Set<String> existingContentIds = new HashSet<>();
        for (Content content : existingContents) {
            existingContentIds.add(content.getSnsContentId());
        }
        List<RawContent> newCandidates = candidates.stream()
                .filter(content -> !existingContentIds.contains(content.snsContentId()))
                .toList();

        // 신규 콘텐츠만 셀렉터스 콘텐츠 판별
        List<RawContent> selectorsContents = newCandidates.stream()
                .filter(classifier::isSelectorsContent)
                .toList();

        saveNewContents(account.getSelectorsId(), selectorsContents, collectionStartedAt);
        saveChangedVersions(
                candidates, existingContents, collectionStartedAt);

        // 모든 저장 성공 이후에만 마지막 수집 시각 갱신
        if (lastCollectedAt == null || collectionStartedAt.isAfter(lastCollectedAt)) {
            account.completeCollection(collectionStartedAt);
        }
        return selectorsContents.size();
    }

    private List<Content> findExistingContents(
            List<RawContent> candidates, SnsPlatform snsCode) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<String> candidateIds = candidates.stream()
                .map(RawContent::snsContentId)
                .toList();
        return contentRepository.findAllBySnsCodeAndSnsContentIdIn(
                snsCode, candidateIds);
    }

    private void saveChangedVersions(
            List<RawContent> candidates,
            List<Content> existingContents,
            LocalDateTime collectionStartedAt) {
        if (existingContents.isEmpty()) {
            return;
        }

        Map<String, RawContent> rawContentsBySnsId = new HashMap<>();
        for (RawContent candidate : candidates) {
            rawContentsBySnsId.put(candidate.snsContentId(), candidate);
        }
        List<Content> collectedExistingContents = existingContents.stream()
                .filter(content -> rawContentsBySnsId.containsKey(content.getSnsContentId()))
                .toList();
        if (collectedExistingContents.isEmpty()) {
            return;
        }

        Map<Long, ContentVersion> latestVersionsByContentId =
                findLatestVersions(collectedExistingContents);
        List<ContentVersion> changedVersions = new ArrayList<>();
        List<RawContent> changedRawContents = new ArrayList<>();
        for (Content content : collectedExistingContents) {
            ContentVersion latestVersion = latestVersionsByContentId.get(content.getId());
            RawContent rawContent = rawContentsBySnsId.get(content.getSnsContentId());
            String currentHash = contentHash(rawContent);
            if (currentHash.equals(latestVersion.getContentHash())) {
                continue;
            }

            changedVersions.add(ContentVersion.builder()
                    .contentId(content.getId())
                    .versionNo(content.advanceVersion())
                    .contentHash(currentHash)
                    .createdAt(collectionStartedAt)
                    .build());
            changedRawContents.add(rawContent);
        }

        if (changedVersions.isEmpty()) {
            return;
        }
        versionRepository.saveAll(changedVersions);
        saveMedia(changedVersions, changedRawContents);
    }

    private Map<Long, ContentVersion> findLatestVersions(List<Content> contents) {
        List<Long> contentIds = contents.stream()
                .map(Content::getId)
                .toList();
        Map<Long, ContentVersion> latestVersionsByContentId = new HashMap<>();
        for (ContentVersion version : versionRepository.findLatestByContentIdIn(contentIds)) {
            ContentVersion duplicate = latestVersionsByContentId.put(
                    version.getContentId(), version);
            if (duplicate != null) {
                throw new IllegalStateException(
                        "콘텐츠의 최신 버전이 중복되었습니다. contentId="
                                + version.getContentId());
            }
        }

        for (Content content : contents) {
            ContentVersion latestVersion = latestVersionsByContentId.get(content.getId());
            if (latestVersion == null
                    || !content.getLastVersionNo().equals(latestVersion.getVersionNo())) {
                throw new IllegalStateException(
                        "콘텐츠의 최신 버전을 찾을 수 없습니다. contentId=" + content.getId()
                                + ", versionNo=" + content.getLastVersionNo());
            }
        }

        return latestVersionsByContentId;
    }

    private void saveNewContents(
            Long selectorsId,
            List<RawContent> rawContents,
            LocalDateTime collectionStartedAt) {
        if (rawContents.isEmpty()) {
            return;
        }

        // 콘텐츠 기본 정보 일괄 저장
        List<Content> contents = rawContents.stream()
                .map(raw -> Content.builder()
                        .selectorsId(selectorsId)
                        .snsCode(raw.snsCode())
                        .snsContentId(raw.snsContentId())
                        .contentUrl(raw.contentUrl())
                        .contentType(raw.contentType())
                        .build())
                .toList();
        contentRepository.saveAll(contents);

        // 신규 콘텐츠의 TEXT와 미디어 정보를 반영한 해시 일괄 저장
        List<ContentVersion> versions = new ArrayList<>(contents.size());
        for (int index = 0; index < contents.size(); index++) {
            versions.add(ContentVersion.builder()
                    .contentId(contents.get(index).getId())
                    .versionNo(1L)
                    .contentHash(contentHash(rawContents.get(index)))
                    .createdAt(collectionStartedAt)
                    .build());
        }
        versionRepository.saveAll(versions);

        saveMedia(versions, rawContents);
    }

    /** TEXT 본문, 이미지·영상 미디어 CDN URL 일괄 저장 */
    private void saveMedia(
            List<ContentVersion> versions,
            List<RawContent> rawContents) {
        List<ContentMedia> media = new ArrayList<>();
        for (int index = 0; index < versions.size(); index++) {
            Long versionId = versions.get(index).getId();
            RawContent rawContent = rawContents.get(index);
            int nextSequenceNo = addTexts(media, versionId, rawContent.texts(), 0);
            addMedia(media, versionId, rawContent, nextSequenceNo);
        }
        mediaRepository.saveAll(media);
    }

    private int addTexts(
            List<ContentMedia> target,
            Long versionId,
            List<String> texts,
            int nextSequenceNo) {
        for (String text : texts) {
            target.add(ContentMedia.builder()
                    .contentVersionId(versionId)
                    .mediaType(ContentMedia.MediaType.TEXT)
                    .body(text)
                    .sequenceNo(nextSequenceNo++)
                    .build());
        }
        return nextSequenceNo;
    }

    private void addMedia(
            List<ContentMedia> target,
            Long versionId,
            RawContent rawContent,
            int nextSequenceNo) {
        for (RawContentMedia rawMedia : rawContent.media()) {
            if (rawMedia.mediaType() == RawContentMedia.MediaType.TEXT) {
                throw new IllegalStateException(
                        "RawContentMedia.TEXT는 본문을 제공하지 않아 저장할 수 없습니다.");
            }
            target.add(ContentMedia.builder()
                    .contentVersionId(versionId)
                    .mediaType(ContentMedia.MediaType.valueOf(rawMedia.mediaType().name()))
                    .mediaUrl(rawMedia.mediaUrl())
                    .snsMediaId(rawMedia.snsMediaId())
                    .sequenceNo(nextSequenceNo++)
                    .build());
        }
    }

    private String contentHash(RawContent rawContent) {
        try {
            StringBuilder canonicalContent = new StringBuilder();
            appendHashValue(canonicalContent, "content-hash-v2");
            appendHashValue(canonicalContent, "texts");
            appendHashValue(canonicalContent, String.valueOf(rawContent.texts().size()));
            for (int sequenceNo = 0; sequenceNo < rawContent.texts().size(); sequenceNo++) {
                appendHashValue(canonicalContent, String.valueOf(sequenceNo));
                appendHashValue(canonicalContent, rawContent.texts().get(sequenceNo));
            }

            appendHashValue(canonicalContent, "assets");
            appendHashValue(canonicalContent, String.valueOf(rawContent.media().size()));
            int sequenceNo = rawContent.texts().size();
            for (RawContentMedia rawMedia : rawContent.media()) {
                if (rawMedia.snsMediaId() == null) {
                    throw new IllegalStateException("미디어 SNS ID는 비어 있을 수 없습니다.");
                }
                appendHashValue(canonicalContent, String.valueOf(sequenceNo++));
                appendHashValue(canonicalContent, rawMedia.mediaType().name());
                appendHashValue(canonicalContent, rawMedia.snsMediaId());
            }

            byte[] text = canonicalContent.toString().getBytes(UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private void appendHashValue(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value);
    }

    private void validatePlatform(RawContent content, SnsPlatform snsCode) {
        if (content == null || content.snsCode() != snsCode) {
            throw new IllegalStateException("수집 계정과 콘텐츠 플랫폼이 일치하지 않습니다.");
        }
    }

    private SelectorsSnsAccount findAccount(Long selectorsSnsAccountId) {
        return accountRepository.findById(selectorsSnsAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ContentPlatformClient findClient(SnsPlatform snsCode) {
        return contentClients.stream()
                .filter(client -> client.supports() == snsCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "콘텐츠 수집 클라이언트가 없습니다. platform=" + snsCode));
    }

}
