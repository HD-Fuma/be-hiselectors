package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.extraction.ContentExtractionExecutionResult;
import com.fuma.hiselectors.inspection.extraction.InstagramContentExtractionClient;
import com.fuma.hiselectors.inspection.extraction.YoutubeContentExtractionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaPreprocessingService {

    private final YoutubeContentExtractionClient youtubeClient;
    private final InstagramContentExtractionClient instagramClient;
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
        return new PreprocessingResult(Optional.empty(), updates);
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
            case INSTAGRAM -> extractInstagram(media);
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
                pending.inputHash(), pending.extractedAt());
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
        return youtubeClient.extract(videoId);
    }

    private ContentExtractionExecutionResult extractInstagram(ContentMedia media) {
        if (media.getMediaType() == MediaType.VIDEO
                && (media.getMediaUrl() == null || media.getMediaUrl().isBlank())) {
            // 썸네일 OCR만으로 영상을 검수하면 음성·영상 근거가 빠지므로 실패시킨다.
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
        if ((media.getMediaUrl() == null || media.getMediaUrl().isBlank())
                && (media.getThumbnailUrl() == null || media.getThumbnailUrl().isBlank())) {
            throw new BusinessException(ErrorCode.CONTENT_MEDIA_SOURCE_UNAVAILABLE);
        }
        return instagramClient.extract(media.getMediaUrl(), media.getThumbnailUrl());
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

    public record PreprocessingResult(
            Optional<AiInspectionResponse> integratedAiResult,
            List<MediaExtractionUpdate> extractionUpdates) {

        public PreprocessingResult {
            integratedAiResult = integratedAiResult == null
                    ? Optional.empty() : integratedAiResult;
            extractionUpdates = extractionUpdates == null
                    ? List.of() : List.copyOf(extractionUpdates);
        }

        public PreprocessingResult(
                Optional<AiInspectionResponse> integratedAiResult,
                Optional<MediaExtractionUpdate> extractionUpdate) {
            this(integratedAiResult,
                    extractionUpdate == null ? List.of() : extractionUpdate.stream().toList());
        }

        public static PreprocessingResult reused() {
            return new PreprocessingResult(Optional.empty(), List.of());
        }
    }

    public record MediaExtractionUpdate(
            Long contentMediaId,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt) {
    }

    private record PendingExtraction(
            ContentMedia media,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt) {
    }
}
