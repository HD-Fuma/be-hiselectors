package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.stt.GeminiProperties;
import com.fuma.hiselectors.stt.MediaResolution;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InspectionPolicyServiceTest {

    @Test
    void createsIndependentPlatformPoliciesWithPromptSnapshots() {
        List<InspectionPolicy> saved = createPolicies("youtube-test", "report-test");

        assertThat(saved).hasSize(2);
        InspectionPolicy youtube = findPolicy(saved, SnsPlatform.YOUTUBE);
        InspectionPolicy instagram = findPolicy(saved, SnsPlatform.INSTAGRAM);
        assertThat(youtube.getAiModelName()).isEqualTo("youtube-test");
        assertThat(youtube.getSttModelName()).isEqualTo("youtube-test");
        assertThat(youtube.getOcrModelName()).isEqualTo("youtube-test");
        assertThat(instagram.getAiModelName()).isEqualTo("report-test");
        assertThat(youtube.getAiPrompt()).contains("검수 대상");
        assertThat(youtube.getExtractionPrompt()).contains("유튜브 Shorts 영상");
        assertThat(instagram.getSttModelName()).isEqualTo("whisper-test");
        assertThat(instagram.getOcrModelName()).isEqualTo("ocr-test");
        assertThat(youtube.getConfigHash()).isNotEqualTo(instagram.getConfigHash());
        assertThat(youtube.isActive()).isTrue();
        assertThat(instagram.isActive()).isTrue();
    }

    @Test
    void dedicatedModelChangeUpdatesOnlyMatchingPlatformHashes() {
        List<InspectionPolicy> baseline = createPolicies("youtube-v1", "report-v1");
        List<InspectionPolicy> youtubeChanged = createPolicies("youtube-v2", "report-v1");
        List<InspectionPolicy> reportChanged = createPolicies("youtube-v1", "report-v2");

        InspectionPolicy baselineYoutube = findPolicy(baseline, SnsPlatform.YOUTUBE);
        InspectionPolicy baselineInstagram = findPolicy(baseline, SnsPlatform.INSTAGRAM);
        InspectionPolicy changedYoutube = findPolicy(youtubeChanged, SnsPlatform.YOUTUBE);
        InspectionPolicy youtubeStableInstagram =
                findPolicy(youtubeChanged, SnsPlatform.INSTAGRAM);
        InspectionPolicy reportStableYoutube = findPolicy(reportChanged, SnsPlatform.YOUTUBE);
        InspectionPolicy changedInstagram = findPolicy(reportChanged, SnsPlatform.INSTAGRAM);

        assertThat(changedYoutube.getAiConfigHash())
                .isNotEqualTo(baselineYoutube.getAiConfigHash());
        assertThat(changedYoutube.getConfigHash())
                .isNotEqualTo(baselineYoutube.getConfigHash());
        assertThat(youtubeStableInstagram.getAiConfigHash())
                .isEqualTo(baselineInstagram.getAiConfigHash());
        assertThat(youtubeStableInstagram.getConfigHash())
                .isEqualTo(baselineInstagram.getConfigHash());

        assertThat(changedInstagram.getAiConfigHash())
                .isNotEqualTo(baselineInstagram.getAiConfigHash());
        assertThat(changedInstagram.getConfigHash())
                .isNotEqualTo(baselineInstagram.getConfigHash());
        assertThat(reportStableYoutube.getAiConfigHash())
                .isEqualTo(baselineYoutube.getAiConfigHash());
        assertThat(reportStableYoutube.getConfigHash())
                .isEqualTo(baselineYoutube.getConfigHash());
    }

    private List<InspectionPolicy> createPolicies(String youtubeModel, String reportModel) {
        InspectionPolicyRepository repository = mock(InspectionPolicyRepository.class);
        List<InspectionPolicy> saved = new ArrayList<>();
        when(repository.findByConfigHash(any())).thenReturn(Optional.empty());
        when(repository.findAllByPlatformAndActiveTrue(any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            InspectionPolicy policy = invocation.getArgument(0);
            saved.add(policy);
            return policy;
        });
        InspectionPolicyService service = new InspectionPolicyService(
                repository,
                new ContentInspectionProperties(
                        List.of("광고"), List.of("example.com"), "ptrsRefCd"),
                new InspectionExtractionProperties(
                        new InspectionExtractionProperties.Instagram(
                                "whisper-test", "ocr-test")),
                new GeminiProperties(
                        "key", null, null, "gemini-test", youtubeModel, reportModel,
                        MediaResolution.LOW, 8192),
                new InspectionPromptProvider(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), ZoneOffset.UTC));

        service.syncActivePolicies();
        return saved;
    }

    private InspectionPolicy findPolicy(
            List<InspectionPolicy> policies, SnsPlatform platform) {
        return policies.stream()
                .filter(policy -> policy.getPlatform() == platform)
                .findFirst().orElseThrow();
    }
}
