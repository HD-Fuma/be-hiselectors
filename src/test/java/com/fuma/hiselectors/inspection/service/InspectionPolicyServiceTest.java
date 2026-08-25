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
                        "key", null, null, "gemini-test", "youtube-test", "report-test",
                        MediaResolution.LOW, 8192),
                new InspectionPromptProvider(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), ZoneOffset.UTC));

        service.syncActivePolicies();

        assertThat(saved).hasSize(2);
        InspectionPolicy youtube = saved.stream()
                .filter(policy -> policy.getPlatform() == SnsPlatform.YOUTUBE)
                .findFirst().orElseThrow();
        InspectionPolicy instagram = saved.stream()
                .filter(policy -> policy.getPlatform() == SnsPlatform.INSTAGRAM)
                .findFirst().orElseThrow();
        assertThat(youtube.getAiPrompt()).contains("검수 대상");
        assertThat(youtube.getExtractionPrompt()).contains("유튜브 영상");
        assertThat(instagram.getSttModelName()).isEqualTo("whisper-test");
        assertThat(instagram.getOcrModelName()).isEqualTo("ocr-test");
        assertThat(youtube.getConfigHash()).isNotEqualTo(instagram.getConfigHash());
        assertThat(youtube.isActive()).isTrue();
        assertThat(instagram.isActive()).isTrue();
    }
}
