package com.fuma.hiselectors.content.client;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.dto.InstagramContentResponse;
import com.fuma.hiselectors.content.client.dto.InstagramContentResponse.Media;
import com.fuma.hiselectors.content.client.dto.InstagramContentResponse.MediaPage;
import com.fuma.hiselectors.content.client.dto.RawContent;
import com.fuma.hiselectors.content.client.dto.RawContentMedia;
import com.fuma.hiselectors.content.client.dto.RawContentMedia.MediaType;
import com.fuma.hiselectors.content.config.InstagramCollectionProperties;
import com.fuma.hiselectors.content.model.ContentType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Instagram Graph API 게시물 조회 및 {@link RawContent} 변환 클라이언트
 */
@Slf4j
@Component
public class InstagramContentClient implements ContentPlatformClient {

    private static final String GRAPH_API_HOST = "https://graph.facebook.com";

    // Meta API에서 받을 게시물 정보
    // children: 캐러셀 내부 이미지와 영상 (이미지, 영상 여러 개)
    private static final String MEDIA_FIELDS =
            "id,caption,media_type,media_product_type,permalink,timestamp,media_url,"
                    + "children{id,media_type,media_url}";

    // 비정상적인 무한 호출 방지
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;

    // Meta 게시물 작성 시각을 변환할 서비스 기준 시간대
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter META_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx");
    private static final Pattern INSTAGRAM_USERNAME =
            Pattern.compile("^[A-Za-z0-9._]{1,30}$");

    private final InstagramCollectionProperties properties;
    private final RestClient restClient;

    public InstagramContentClient(
            InstagramCollectionProperties properties,
            @Qualifier("contentRestClient") RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public SnsPlatform supports() {
        return SnsPlatform.INSTAGRAM;
    }

    /**
     * username의 Instagram 게시물 중 collectedAfter 이후 게시물 조회
     *
     * accountId: Instagram username (숫자 ID가 아님)
     * 반환값: 셀렉터스 콘텐츠 판별용 임시 데이터
     */
    @Override
    public List<RawContent> collect(String accountId, LocalDateTime collectedAfter) {
        validateRequest(accountId, collectedAfter);

        long startedAtNanos = System.nanoTime();
        int fetchedCount = 0;
        List<RawContent> contents = new ArrayList<>();

        // Business Discovery로 username의 첫 게시글 페이지 요청
        MediaPage page = requestFirstPage(accountId);

        for (int pageCount = 0; pageCount < MAX_PAGES; pageCount++) {
            // API가 반환한 게시글 수 합산 (로깅)
            fetchedCount += page.data() == null ? 0 : page.data().size();

            // 마지막 수집 시각 이후 게시글만 RawContent로 변환
            boolean reachedCollectedAt = addNewContents(
                    page.data(), collectedAfter, contents);
            if (reachedCollectedAt) {
                break;
            }

            // 페이지별 조회
            String nextUrl = page.paging() == null ? null : page.paging().next();
            if (nextUrl == null) {
                break;
            }
            if (pageCount == MAX_PAGES - 1) {
                log.warn("Instagram 콘텐츠 수집 페이지 상한 도달. maxPages={}", MAX_PAGES);
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
            page = requestNextPage(nextUrl);
        }

        // 플랫폼, 계정, 신규 기준 시각, 조회 건수, 신규 건수, 소요 시간 기록
        log.info(
                "콘텐츠 수집 완료. platform={} accountId={} collectedAfter={} "
                        + "fetchedCount={} newCount={} durationMs={}",
                supports(), accountId, collectedAfter, fetchedCount, contents.size(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos));
        return List.copyOf(contents);
    }

    private void validateRequest(String username, LocalDateTime collectedAfter) {
        // application-local.yaml 값 정상인지 확인
        if (!properties.isConfigured()) {
            throw new BusinessException(ErrorCode.INSTAGRAM_COLLECTION_CONFIG_MISSING);
        }

        // username, collectedAfter가 정상인지 확인
        if (username == null || !INSTAGRAM_USERNAME.matcher(username).matches()
                || collectedAfter == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private MediaPage requestFirstPage(String username) {
        // username 계정 게시글 조회
        String fields = "business_discovery.username(%s){media.limit(%d){%s}}"
                .formatted(username, PAGE_SIZE, MEDIA_FIELDS);
        URI uri = UriComponentsBuilder.fromUriString(GRAPH_API_HOST)
                .pathSegment(properties.apiVersion(), properties.businessAccountId())
                .queryParam("fields", fields)
                .build()
                .encode()
                .toUri();

        // 첫 응답의 JSON 구조를 InstagramContentResponse로 변환
        InstagramContentResponse response = request(
                uri, InstagramContentResponse.class);
        if (response.businessDiscovery() == null
                || response.businessDiscovery().media() == null) {
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
        return response.businessDiscovery().media();
    }

    private MediaPage requestNextPage(String nextUrl) {
        URI uri;
        try {
            uri = URI.create(nextUrl);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
        // Meta가 제공한 다음 페이지 URL의 HTTPS 스킴과 허용 호스트 검증
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"graph.facebook.com".equalsIgnoreCase(uri.getHost())) {
            log.warn("Instagram 페이지네이션 URL이 허용된 호스트가 아닙니다. host={}", uri.getHost());
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
        return request(uri, MediaPage.class);
    }

    private <T> T request(URI uri, Class<T> responseType) {
        try {
            T response = restClient.get()
                    .uri(uri)
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .retrieve()
                    .body(responseType);
            if (response == null) {
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
            return response;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Instagram Graph API 호출 실패. cause={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
    }

    private boolean addNewContents(List<Media> media, LocalDateTime collectedAfter,
                                   List<RawContent> contents) {
        if (media == null || media.isEmpty()) {
            return true;
        }

        boolean hasNewContent = false;
        for (Media item : media) {
            if (item == null || item.timestamp() == null) {
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
            // createdAt은 DB 저장 시각이 아닌 Instagram 게시글 작성 시각
            LocalDateTime createdAt = parseTimestamp(item.timestamp());
            if (!createdAt.isAfter(collectedAfter)) {
                continue;
            }
            contents.add(toRawContent(item, createdAt));
            hasNewContent = true;
        }
        // 최신순 페이지에서 신규 게시글이 없으면 이후 페이지 조회 종료
        return !hasNewContent;
    }

    private RawContent toRawContent(Media media, LocalDateTime createdAt) {
        if (media.id() == null || media.permalink() == null) {
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }

        // Meta 응답을 RawContent로 변환
        return new RawContent(
                SnsPlatform.INSTAGRAM,
                media.id(),
                media.permalink(),
                contentType(media),
                media.caption() == null ? "" : media.caption(),
                createdAt,
                rawMedia(media));
    }

    private ContentType contentType(Media media) {
        return "REELS".equalsIgnoreCase(media.mediaProductType())
                ? ContentType.SHORT_FORM
                : ContentType.FEED;
    }

    private List<RawContentMedia> rawMedia(Media media) {
        // 일반 게시글과 릴스는 게시글 자체의 단일 이미지 또는 영상 사용
        if (!"CAROUSEL_ALBUM".equalsIgnoreCase(media.mediaType())) {
            return List.of(toRawContentMedia(media));
        }

        // 캐러셀은 여러 children을 각각 RawContentMedia로 변환
        if (media.children() == null || media.children().data() == null
                || media.children().data().isEmpty()) {
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
        return media.children().data().stream()
                .map(this::toRawContentMedia)
                .toList();
    }

    private RawContentMedia toRawContentMedia(Media media) {
        if (media == null || media.id() == null || media.mediaType() == null) {
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
        MediaType mediaType = switch (media.mediaType()) {
            case "IMAGE" -> MediaType.IMAGE;
            case "VIDEO" -> MediaType.VIDEO;
            default -> throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        };
        return new RawContentMedia(media.id(), mediaType, media.mediaUrl());
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        try {
            // ISO 표준 형식의 작성 시각을 한국 시간으로 변환
            return OffsetDateTime.parse(timestamp)
                    .atZoneSameInstant(KOREA_ZONE)
                    .toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                // Meta의 콜론 없는 offset 형식인 +0000 처리
                return OffsetDateTime.parse(timestamp, META_TIMESTAMP_FORMATTER)
                        .atZoneSameInstant(KOREA_ZONE)
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
        }
    }
}
