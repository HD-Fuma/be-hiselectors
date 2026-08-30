package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.client.InstagramContentFetcher;
import com.fuma.hiselectors.content.client.YoutubeContentFetcher;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.extraction.ContentExtractionExecutionResult;
import com.fuma.hiselectors.inspection.extraction.InstagramContentExtractionClient;
import com.fuma.hiselectors.inspection.extraction.YoutubeContentExtractionClient;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.selectors.model.SelectorsSnsAccount;
import com.fuma.hiselectors.selectors.repository.SelectorsSnsAccountRepository;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaPreprocessingService {

    private final YoutubeContentExtractionClient youtubeClient;
    private final InstagramContentExtractionClient instagramClient;
    private final YoutubeContentFetcher youtubeContentFetcher;
    private final InstagramContentFetcher instagramContentFetcher;
    private final SelectorsSnsAccountRepository snsAccountRepository;
    private final ContentMediaExtractionBodyMapper bodyMapper;
    private final InspectionPolicyRepository policyRepository;
    private final Clock clock;

    /** 모든 원본 IMAGE/VIDEO를 순서대로 추출한다. 하나라도 실패하면 전체 검수를 실패시킨다. */
    public PreprocessingResult preprocess(
            Content content, List<ContentMedia> media, InspectionPolicy activePolicy) {
        List<PendingExtraction> pendingExtractions = media.stream()
                .filter(item -> item.getMediaType() != MediaType.TEXT)
                .sorted(Comparator.comparing(ContentMedia::getSequenceNo))
                .filter(item -> !canReuse(
                        item, extractionInputHash(content, item), activePolicy))
                .map(item -> prepareExtraction(content, item, activePolicy))
                .toList();

        List<MediaExtractionUpdate> updates = new ArrayList<>(pendingExtractions.size());
        pendingExtractions.forEach(pending -> updates.add(applyExtraction(pending)));
        return new PreprocessingResult(updates);
    }

    /** 추출 결과를 덮어써야 한다면 원본 스냅샷을 복제한 새 콘텐츠 버전을 요구한다. */
    public boolean requiresNewVersion(
            Content content, List<ContentMedia> media, InspectionPolicy activePolicy) {
        return media.stream()
                .filter(item -> item.getMediaType() != MediaType.TEXT)
                .anyMatch(item -> requiresNewVersion(content, item, activePolicy));
    }

    private boolean requiresNewVersion(
            Content content, ContentMedia media, InspectionPolicy activePolicy) {
        boolean untouched = media.bodyOrEmpty().isEmpty()
                && media.getExtractedWithPolicyId() == null;
        if (untouched) {
            return false;
        }
        return !canReuse(media, extractionInputHash(content, media), activePolicy);
    }

    private boolean canReuse(
            ContentMedia media, String inputHash, InspectionPolicy activePolicy) {
        if (!bodyMapper.isCurrentExtraction(media.bodyOrEmpty())
                || media.getExtractedWithPolicyId() == null
                || inputHash == null
                || !inputHash.equals(media.getExtractionInputHash())) {
            return false;
        }
        return policyRepository.findById(media.getExtractedWithPolicyId())
                .map(previous -> previous.getExtractionConfigHash()
                        .equals(activePolicy.getExtractionConfigHash()))
                .orElse(false);
    }

    private PendingExtraction prepareExtraction(
            Content content, ContentMedia media, InspectionPolicy activePolicy) {
        if (!media.bodyOrEmpty().isEmpty() || media.getExtractedWithPolicyId() != null) {
            throw new IllegalStateException(
                    "추출이 완료된 ContentMedia.body는 덮어쓸 수 없습니다.");
        }
        String inputHash = extractionInputHash(content, media);
        if (inputHash == null) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }

        ContentExtractionExecutionResult execution = switch (content.getSnsCode()) {
            case YOUTUBE -> extractYoutube(content, media);
            case INSTAGRAM -> extractInstagram(content, media);
        };
        Map<String, Object> body = bodyMapper.toBody(execution.extraction());
        return new PendingExtraction(
                media, body, activePolicy.getId(), inputHash, LocalDateTime.now(clock));
    }

    private MediaExtractionUpdate applyExtraction(PendingExtraction pending) {
        ContentMedia media = pending.media();
        media.replaceBody(pending.body());
        media.markExtracted(
                pending.inspectionPolicyId(), pending.inputHash(), pending.extractedAt());
        return new MediaExtractionUpdate(
                media.getId(), Map.copyOf(pending.body()), pending.inspectionPolicyId(),
                pending.inputHash(), pending.extractedAt(),
                media.getMediaUrl(), media.getThumbnailUrl());
    }

    private ContentExtractionExecutionResult extractYoutube(
            Content content, ContentMedia media) {
        if (media.getMediaType() != MediaType.VIDEO) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
        String videoId = resolveYoutubeVideoId(content, media);
        if (videoId == null) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
        return youtubeClient.extract(videoId, youtubeContentFetcher.durationMs(videoId));
    }

    private ContentExtractionExecutionResult extractInstagram(
            Content content, ContentMedia media) {
        if (!hasUsableInstagramSource(media)) {
            if (media.getSnsMediaId() == null || media.getSnsMediaId().isBlank()) {
                throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
            }
            refreshInstagramUrls(content, media);
        }
        try {
            return instagramClient.extract(media.getMediaUrl(), media.getThumbnailUrl());
        } catch (BusinessException exception) {
            if (exception.getErrorCode() != ErrorCode.MEDIA_URL_EXPIRED) {
                throw exception;
            }
            refreshInstagramUrls(content, media);
            return instagramClient.extract(media.getMediaUrl(), media.getThumbnailUrl());
        }
    }

    private boolean hasUsableInstagramSource(ContentMedia media) {
        if (media.getMediaType() == MediaType.VIDEO
                && (media.getMediaUrl() == null || media.getMediaUrl().isBlank())) {
            return false;
        }
        return (media.getMediaUrl() != null && !media.getMediaUrl().isBlank())
                || (media.getThumbnailUrl() != null && !media.getThumbnailUrl().isBlank());
    }

    private void refreshInstagramUrls(Content content, ContentMedia media) {
        if (media.getSnsMediaId() == null || media.getSnsMediaId().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_URL_EXPIRED);
        }
        String username = instagramUsername(content);
        if (username == null) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
        InstagramContentFetcher.MediaUrls fresh = instagramContentFetcher.fetchMediaUrls(
                username, content.getSnsContentId(), media.getSnsMediaId().strip());
        media.replaceUrls(fresh.mediaUrl(), fresh.thumbnailUrl());
        log.info("만료된 Instagram CDN URL을 갱신했습니다. contentMediaId={}, snsMediaId={}",
                media.getId(), media.getSnsMediaId());
        if (!hasUsableInstagramSource(media)) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
    }

    private String instagramUsername(Content content) {
        return snsAccountRepository.findBySelectorsIdAndDeletedFalse(content.getSelectorsId())
                .filter(account -> account.getSnsCode() == SnsPlatform.INSTAGRAM)
                .map(SelectorsSnsAccount::getAccountId)
                .filter(accountId -> accountId != null && !accountId.isBlank())
                .map(String::strip)
                .orElse(null);
    }

    private String extractionInputHash(Content content, ContentMedia media) {
        String sourceId;
        if (content.getSnsCode() == SnsPlatform.YOUTUBE) {
            sourceId = resolveYoutubeVideoId(content, media);
        } else if (media.getSnsMediaId() != null && !media.getSnsMediaId().isBlank()) {
            sourceId = media.getSnsMediaId().strip();
        } else if (media.getMediaUrl() != null && !media.getMediaUrl().isBlank()) {
            sourceId = media.getMediaUrl().strip();
        } else {
            sourceId = media.getThumbnailUrl() == null
                    ? null : media.getThumbnailUrl().strip();
        }
        return sourceId == null || sourceId.isBlank() ? null : sha256(String.join("\n",
                content.getSnsCode().name(), media.getMediaType().name(), sourceId));
    }

    private String resolveYoutubeVideoId(Content content, ContentMedia media) {
        if (media.getSnsMediaId() != null && !media.getSnsMediaId().isBlank()) {
            return media.getSnsMediaId().strip();
        }
        return youtubeVideoId(content.getContentUrl());
    }

    private String youtubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            if ("youtu.be".equalsIgnoreCase(uri.getHost())) {
                return uri.getPath() == null ? null : uri.getPath().replaceFirst("^/", "");
            }
            if (uri.getHost() != null && uri.getHost().toLowerCase().contains("youtube.com")) {
                return queryValue(uri, "v");
            }
            return null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String queryValue(URI uri, String name) {
        if (uri.getRawQuery() == null) {
            return null;
        }
        return Arrays.stream(uri.getRawQuery().split("&"))
                .map(parameter -> parameter.split("=", 2))
                .filter(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name))
                .map(pair -> pair.length == 2
                        ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "")
                .findFirst().orElse(null);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public record PreprocessingResult(List<MediaExtractionUpdate> extractionUpdates) {

        public PreprocessingResult {
            extractionUpdates = extractionUpdates == null
                    ? List.of() : List.copyOf(extractionUpdates);
        }
    }

    public record MediaExtractionUpdate(
            Long contentMediaId,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt,
            String mediaUrl,
            String thumbnailUrl) {
    }

    private record PendingExtraction(
            ContentMedia media,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt) {
    }
}
