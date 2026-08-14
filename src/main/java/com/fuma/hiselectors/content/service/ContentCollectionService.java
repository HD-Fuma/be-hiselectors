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
import com.fuma.hiselectors.selectors.model.Selectors;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsRepository;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
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
    // TODO: 현재 기수 시작일 조회 값으로 교체
    private static final LocalDateTime CONTENT_COLLECTION_START_AT =
            LocalDateTime.of(2026, 5, 1, 0, 0);

    private final List<ContentPlatformClient> contentClients;
    private final SelectorsContentClassifier classifier;
    private final SelectorsSnsAccountRepository accountRepository;
    private final SelectorsRepository selectorsRepository;
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

        // 외부 API 호출
        CollectionResult collectionResult = Objects.requireNonNull(
                client.collect(account.getAccountId(), CONTENT_COLLECTION_START_AT),
                "콘텐츠 수집 결과는 null일 수 없습니다.");
        List<RawContent> collectedContents = collectionResult.contents();

        // DB 저장과 마지막 수집 시각 갱신의 단일 트랜잭션 처리
        int savedCount = Objects.requireNonNull(transactionTemplate.execute(status ->
                persist(selectorsSnsAccountId, collectedContents, collectionStartedAt)));

        log.info(
                "콘텐츠 수집 결과 | 플랫폼={} | 계정={} | API 조회={}건 | 현재 기수={}건 "
                        + "| 셀렉터스 게시글={}건",
                account.getSnsCode(), account.getAccountId(), collectionResult.fetchedCount(),
                collectedContents.size(), savedCount);
        return savedCount;
    }

    /** RawContent에서 신규 셀렉터스 콘텐츠만 DB에 저장 */
    private int persist(
            Long selectorsSnsAccountId,
            List<RawContent> collectedContents,
            LocalDateTime collectionStartedAt) {
        // 트랜잭션 내부에서 SNS 계정 재조회: 마지막 수집 시각 갱신
        SelectorsSnsAccount account = findAccount(selectorsSnsAccountId);

        // 셀렉터스 코드 조회 (셀렉터스 게시글 여부 판별 목적)
        Selectors selectors = selectorsRepository.findById(account.getSelectorsId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SELECTOR_NOT_FOUND));

        // 마지막 수집 시각 이후 콘텐츠 선별
        List<RawContent> candidates = new ArrayList<>();
        LocalDateTime collectedAfter = account.getLastCollectedAt().withNano(0);
        for (RawContent content : collectedContents) {
            validatePlatform(content, account.getSnsCode());
            if (!content.createdAt().isBefore(collectedAfter)) {
                candidates.add(content);
            }
        }

        // 신규 콘텐츠만 셀렉터스 콘텐츠 판별
        List<RawContent> selectorsContents = candidates.stream()
                .filter(content -> classifier.isSelectorsContent(
                        content, selectors.getSelectorsCode()))
                .toList();

        saveAll(account.getSelectorsId(), selectorsContents, collectionStartedAt);

        // 모든 저장 성공 이후에만 마지막 수집 시각 갱신
        if (collectionStartedAt.isAfter(account.getLastCollectedAt())) {
            account.completeCollection(collectionStartedAt);
        }
        return selectorsContents.size();
    }

    private void saveAll(
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

        // 신규 콘텐츠의 텍스트 해시 일괄 저장
        List<ContentVersion> versions = new ArrayList<>(contents.size());
        for (int index = 0; index < contents.size(); index++) {
            versions.add(ContentVersion.builder()
                    .contentId(contents.get(index).getId())
                    .versionNo(1L)
                    .contentHash(contentHash(rawContents.get(index).texts()))
                    .createdAt(collectionStartedAt)
                    .build());
        }
        versionRepository.saveAll(versions);

        // TEXT 본문, 이미지·영상 미디어 CDN URL 일괄 저장
        List<ContentMedia> media = new ArrayList<>();
        for (int index = 0; index < versions.size(); index++) {
            Long versionId = versions.get(index).getId();
            RawContent rawContent = rawContents.get(index);
            addTexts(media, versionId, rawContent.texts());
            addMedia(media, versionId, rawContent);
        }
        mediaRepository.saveAll(media);
    }

    private void addTexts(
            List<ContentMedia> target, Long versionId, List<String> texts) {
        for (String text : texts) {
            target.add(ContentMedia.builder()
                    .contentVersionId(versionId)
                    .mediaType(ContentMedia.MediaType.TEXT)
                    .body(text)
                    .build());
        }
    }

    private void addMedia(
            List<ContentMedia> target, Long versionId, RawContent rawContent) {
        for (RawContentMedia rawMedia : rawContent.media()) {
            if (rawMedia.mediaType() == RawContentMedia.MediaType.TEXT) {
                throw new IllegalStateException(
                        "RawContentMedia.TEXT는 본문을 제공하지 않아 저장할 수 없습니다.");
            }
            target.add(ContentMedia.builder()
                    .contentVersionId(versionId)
                    .mediaType(ContentMedia.MediaType.valueOf(rawMedia.mediaType().name()))
                    .mediaUrl(rawMedia.mediaUrl())
                    .build());
        }
    }

    private String contentHash(List<String> texts) {
        try {
            // 현재 버전의 모든 TEXT를 순서대로 결합한 SHA-256 해시
            byte[] text = String.join("\n", texts).getBytes(UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
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
