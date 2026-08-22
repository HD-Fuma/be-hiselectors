package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.content.model.Content;
import com.fuma.hiselectors.content.model.ContentMedia;
import com.fuma.hiselectors.content.model.MediaType;
import com.fuma.hiselectors.inspection.ai.YoutubeIntegratedInspectionClient;
import com.fuma.hiselectors.inspection.model.AiInspectionResponse;
import com.fuma.hiselectors.content.model.ContentReportData;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.IntegratedInspectionResult;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.stt.SttResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MediaPreprocessingServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-21T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void reusesYoutubeExtractionWhenInputAndExtractionPolicyAreSame() {
        YoutubeIntegratedInspectionClient youtube = mock(YoutubeIntegratedInspectionClient.class);
        InspectionPolicyRepository policies = mock(InspectionPolicyRepository.class);
        InspectionPolicy previous = policy(1L, "same-extraction");
        InspectionPolicy active = policy(2L, "same-extraction");
        ContentMedia video = video(Map.of("stt", List.of(), "ocr", List.of()));
        video.markExtracted(1L, sha256("abc123"), CLOCK.instant().atOffset(ZoneOffset.UTC)
                .toLocalDateTime());
        when(policies.findById(1L)).thenReturn(Optional.of(previous));
        MediaPreprocessingService service = new MediaPreprocessingService(
                youtube, policies, CLOCK);

        MediaPreprocessingService.PreprocessingResult result = service.preprocess(
                content(), List.of(video), active);

        assertThat(result.integratedAiResult()).isEmpty();
        verify(youtube, never()).inspect(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any());
        assertThat(video.getExtractedWithPolicyId()).isEqualTo(1L);
    }

    @Test
    void runsOneIntegratedCallWhenYoutubeExtractionIsStale() {
        YoutubeIntegratedInspectionClient youtube = mock(YoutubeIntegratedInspectionClient.class);
        InspectionPolicyRepository policies = mock(InspectionPolicyRepository.class);
        InspectionPolicy active = policy(2L, "new-extraction");
        Content content = content();
        ContentMedia video = video(Map.of());
        AiInspectionResponse aiResult = new AiInspectionResponse(
                ContentReportData.empty(), List.of());
        when(youtube.inspect("abc123", content, video, List.of(video), active))
                .thenReturn(new IntegratedInspectionResult(
                        new SttResult("요약", "음성", "화면", null), aiResult));
        MediaPreprocessingService service = new MediaPreprocessingService(
                youtube, policies, CLOCK);

        MediaPreprocessingService.PreprocessingResult result = service.preprocess(
                content, List.of(video), active);

        assertThat(result.integratedAiResult()).contains(aiResult);
        assertThat(video.getBody()).containsKeys("summary", "stt", "ocr");
        assertThat(video.getExtractedWithPolicyId()).isEqualTo(2L);
        verify(youtube).inspect("abc123", content, video, List.of(video), active);
    }

    private Content content() {
        Content content = Content.create(
                1L, SnsPlatform.YOUTUBE, "https://youtu.be/abc123", "LONG_FORM");
        ReflectionTestUtils.setField(content, "id", 10L);
        return content;
    }

    private ContentMedia video(Map<String, Object> body) {
        ContentMedia media = ContentMedia.create(
                20L, "https://youtu.be/abc123", MediaType.VIDEO, body);
        ReflectionTestUtils.setField(media, "id", 30L);
        return media;
    }

    private InspectionPolicy policy(Long id, String extractionHash) {
        InspectionPolicy policy = mock(InspectionPolicy.class);
        when(policy.getId()).thenReturn(id);
        when(policy.getExtractionConfigHash()).thenReturn(extractionHash);
        return policy;
    }

    private String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
