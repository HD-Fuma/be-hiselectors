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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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

    private static final int PAGE_SIZE = 25;
    private static final int OUT_OF_PERIOD_STOP_THRESHOLD = 4;

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
     * username의 Instagram 게시물 중 수집 기준 시각 이후 게시물 조회
     *
     * accountId: Instagram username (숫자 ID가 아님)
     * 반환값: 셀렉터스 콘텐츠 판별용 임시 데이터
     */
    @Override
    public CollectionResult collect(String accountId, LocalDateTime collectedAfter) {
        validateRequest(accountId, collectedAfter);

        int fetchedCount = 0;
        int consecutiveOutOfPeriodCount = 0;
        List<RawContent> contents = new ArrayList<>();
        Set<String> requestedNextUrls = new HashSet<>();

        // Business Discovery로 username의 첫 게시글 페이지 요청
        MediaPage page = requestFirstPage(accountId);

        while (true) {
            // API가 반환한 게시글 수 합산 (로깅)
            List<Media> media = page.data();
            fetchedCount += media == null ? 0 : media.size();
            if (media == null || media.isEmpty()) {
                break;
            }

            // 이미 받은 페이지는 끝까지 확인하고, 페이지 끝의 연속 횟수로 다음 요청 결정
            consecutiveOutOfPeriodCount = addCollectedContents(
                    media, collectedAfter, contents, consecutiveOutOfPeriodCount);
            if (consecutiveOutOfPeriodCount >= OUT_OF_PERIOD_STOP_THRESHOLD) {
                break;
            }

            // 페이지별 조회
            String nextUrl = page.paging() == null ? null : page.paging().next();
            if (nextUrl == null) {
                break;
            }
            if (!requestedNextUrls.add(nextUrl)) {
                log.warn("Instagram 페이지 URL 반복 감지");
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
            page = requestNextPage(nextUrl);
        }

        return new CollectionResult(fetchedCount, contents);
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
            log.warn("Instagram 페이지네이션 URL이 허용된 호스트가 아닙니다. 호스트={}", uri.getHost());
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
        } catch (RestClientResponseException e) {
            log.warn("Instagram Graph API 호출 실패. HTTP상태={} 응답={}",
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString().replaceAll("[\\r\\n]+", " "));
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Instagram Graph API 호출 실패. 원인={}",
                    e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
        }
    }

    private int addCollectedContents(
            List<Media> media,
            LocalDateTime collectedAfter,
            List<RawContent> contents,
            int consecutiveOutOfPeriodCount) {
        for (Media item : media) {
            if (item == null || item.timestamp() == null) {
                throw new BusinessException(ErrorCode.INSTAGRAM_API_CALL_FAILED);
            }
            // createdAt은 DB 저장 시각이 아닌 Instagram 게시글 작성 시각
            LocalDateTime createdAt = parseTimestamp(item.timestamp());
            if (createdAt.isBefore(collectedAfter)) {
                consecutiveOutOfPeriodCount++;
                continue;
            }
            contents.add(toRawContent(item, createdAt));
            consecutiveOutOfPeriodCount = 0;
        }
        return consecutiveOutOfPeriodCount;
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
