package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.ai.YoutubeIntegratedInspectionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.IntegratedInspectionResult;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.stt.SttResult;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaPreprocessingService {

    private final YoutubeIntegratedInspectionClient youtubeClient;
    private final InspectionPolicyRepository policyRepository;
    private final Clock clock;

    /**
     * YouTube는 추출이 필요할 때 영상 통합 검수 결과까지 반환한다.
     * 이미 추출된 텍스트를 재사용하거나 Instagram인 경우 AI 결과는 비어 있다.
     */
    public PreprocessingResult preprocess(
            Content content, List<ContentMedia> media, InspectionPolicy activePolicy) {
        if (content.getSnsCode() != SnsPlatform.YOUTUBE) {
            return PreprocessingResult.reused();
        }
        Optional<ContentMedia> video = media.stream()
                .filter(item -> item.getMediaType() == MediaType.VIDEO)
                .filter(item -> resolveYoutubeVideoId(content, item) != null)
                .findFirst();
        if (video.isEmpty()) {
            return PreprocessingResult.reused();
        }

        ContentMedia target = video.get();
        String videoId = resolveYoutubeVideoId(content, target);
        String inputHash = sha256(videoId);
        if (canReuse(target, inputHash, activePolicy)) {
            return PreprocessingResult.reused();
        }

        IntegratedInspectionResult result = youtubeClient.inspect(
                videoId, content, target, media, activePolicy);
        MediaExtractionUpdate extractionUpdate = applyExtraction(
                target, result.extraction(), activePolicy.getId(), inputHash);
        return new PreprocessingResult(
                Optional.of(result.inspection()), Optional.of(extractionUpdate));
    }

    /** 기존 추출 결과를 덮어쓰지 않고 새 버전에서 다시 추출해야 하는지 판별한다. */
    public boolean requiresNewVersion(
            Content content, List<ContentMedia> media, InspectionPolicy activePolicy) {
        if (content.getSnsCode() != SnsPlatform.YOUTUBE) {
            return false;
        }
        return media.stream()
                .filter(item -> item.getMediaType() == MediaType.VIDEO)
                .filter(item -> resolveYoutubeVideoId(content, item) != null)
                .anyMatch(item -> requiresNewVersion(content, item, activePolicy));
    }

    private boolean requiresNewVersion(
            Content content, ContentMedia media, InspectionPolicy activePolicy) {
        Map<String, Object> body = media.bodyOrEmpty();
        if (body.containsKey("stt") || body.containsKey("ocr")) {
            return true;
        }
        if (media.getExtractedWithPolicyId() == null) {
            return !body.isEmpty();
        }
        String videoId = resolveYoutubeVideoId(content, media);
        return videoId == null || !canReuse(media, sha256(videoId), activePolicy);
    }

    private boolean canReuse(ContentMedia media, String inputHash, InspectionPolicy activePolicy) {
        if (!media.bodyOrEmpty().containsKey("text")
                || media.getExtractedWithPolicyId() == null
                || !inputHash.equals(media.getExtractionInputHash())) {
            return false;
        }
        return policyRepository.findById(media.getExtractedWithPolicyId())
                .map(previous -> previous.getExtractionConfigHash()
                        .equals(activePolicy.getExtractionConfigHash()))
                .orElse(false);
    }

    private MediaExtractionUpdate applyExtraction(ContentMedia media, SttResult result,
                                                  Long policyId, String inputHash) {
        if (!media.bodyOrEmpty().isEmpty() || media.getExtractedWithPolicyId() != null) {
            throw new IllegalStateException("추출이 완료된 ContentMedia.body는 덮어쓸 수 없습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", combineText(result.stt(), result.ocr()));
        LocalDateTime extractedAt = LocalDateTime.now(clock);
        media.replaceBody(body);
        media.markExtracted(policyId, inputHash, extractedAt);
        return new MediaExtractionUpdate(
                media.getId(), Map.copyOf(body), policyId, inputHash, extractedAt);
    }

    static String combineText(String stt, String ocr) {
        return java.util.stream.Stream.of(stt, ocr)
                .filter(java.util.Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
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
        } catch (IllegalArgumentException e) {
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
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public record PreprocessingResult(
            Optional<AiInspectionResponse> integratedAiResult,
            Optional<MediaExtractionUpdate> extractionUpdate) {

        public PreprocessingResult {
            integratedAiResult = integratedAiResult == null
                    ? Optional.empty() : integratedAiResult;
            extractionUpdate = extractionUpdate == null
                    ? Optional.empty() : extractionUpdate;
        }

        public static PreprocessingResult reused() {
            return new PreprocessingResult(Optional.empty(), Optional.empty());
        }
    }

    public record MediaExtractionUpdate(
            Long contentMediaId,
            Map<String, Object> body,
            Long inspectionPolicyId,
            String inputHash,
            LocalDateTime extractedAt) {
    }
}
