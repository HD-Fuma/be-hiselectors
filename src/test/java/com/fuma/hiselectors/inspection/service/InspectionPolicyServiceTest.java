package com.fuma.hiselectors.inspection.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class InspectionPolicyServiceTest {

    @Test
    void skipsStartupSyncWhenDisabled() {
        InspectionPolicyRepository repository = mock(InspectionPolicyRepository.class);
        InspectionPolicyService service = service(repository, "gemini-test");
        ReflectionTestUtils.setField(service, "policySyncEnabled", false);

        service.syncActivePolicies();

        verifyNoInteractions(repository);
    }

    @Test
    void createsIndependentPlatformPoliciesWithPromptSnapshots() {
        List<InspectionPolicy> saved = createPolicies("gemini-test");

        assertThat(saved).hasSize(2);
        InspectionPolicy youtube = findPolicy(saved, SnsPlatform.YOUTUBE);
        InspectionPolicy instagram = findPolicy(saved, SnsPlatform.INSTAGRAM);
        assertThat(youtube.getAiModelName()).isEqualTo("gemini-test");
        assertThat(youtube.getSttModelName()).isEqualTo("gemini-test");
        assertThat(youtube.getOcrModelName()).isEqualTo("gemini-test");
        assertThat(instagram.getAiModelName()).isEqualTo("gemini-test");
        assertThat(youtube.getAiPrompt()).contains("검수 대상");
        assertThat(youtube.getExtractionPrompt()).contains("유튜브 Shorts 영상");
        assertThat(instagram.getSttModelName()).isEqualTo("whisper-test");
        assertThat(instagram.getOcrModelName()).isEqualTo("ocr-test");
        assertThat(youtube.getConfigHash()).isNotEqualTo(instagram.getConfigHash());
        assertThat(youtube.isActive()).isTrue();
        assertThat(instagram.isActive()).isTrue();
    }

    @Test
    void sharedModelChangeUpdatesBothPlatformHashes() {
        List<InspectionPolicy> baseline = createPolicies("gemini-v1");
        List<InspectionPolicy> changed = createPolicies("gemini-v2");

        InspectionPolicy baselineYoutube = findPolicy(baseline, SnsPlatform.YOUTUBE);
        InspectionPolicy baselineInstagram = findPolicy(baseline, SnsPlatform.INSTAGRAM);
        InspectionPolicy changedYoutube = findPolicy(changed, SnsPlatform.YOUTUBE);
        InspectionPolicy changedInstagram = findPolicy(changed, SnsPlatform.INSTAGRAM);

        assertThat(changedYoutube.getAiConfigHash())
                .isNotEqualTo(baselineYoutube.getAiConfigHash());
        assertThat(changedYoutube.getConfigHash())
                .isNotEqualTo(baselineYoutube.getConfigHash());
        assertThat(changedInstagram.getAiConfigHash())
                .isNotEqualTo(baselineInstagram.getAiConfigHash());
        assertThat(changedInstagram.getConfigHash())
                .isNotEqualTo(baselineInstagram.getConfigHash());
    }

    private List<InspectionPolicy> createPolicies(String model) {
        InspectionPolicyRepository repository = mock(InspectionPolicyRepository.class);
        List<InspectionPolicy> saved = new ArrayList<>();
        when(repository.findByConfigHash(any())).thenReturn(Optional.empty());
        when(repository.findAllByPlatformAndActiveTrue(any())).thenReturn(List.of());
        when(repository.save(any())).thenAnswer(invocation -> {
            InspectionPolicy policy = invocation.getArgument(0);
            saved.add(policy);
            return policy;
        });
        InspectionPolicyService service = service(repository, model);

        service.syncActivePolicies();
        return saved;
    }

    private InspectionPolicyService service(
            InspectionPolicyRepository repository, String model) {
        return new InspectionPolicyService(
                repository,
                new ContentInspectionProperties(
                        List.of("광고"), List.of("example.com"), "ptrsRefCd"),
                new InspectionExtractionProperties(
                        new InspectionExtractionProperties.Instagram(
                                "whisper-test", "ocr-test")),
                new GeminiProperties(
                        "key", null, null, model, MediaResolution.LOW, 8192),
                new InspectionPromptProvider(),
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-21T03:00:00Z"), ZoneOffset.UTC));
    }

    private InspectionPolicy findPolicy(
            List<InspectionPolicy> policies, SnsPlatform platform) {
        return policies.stream()
                .filter(policy -> policy.getPlatform() == platform)
                .findFirst().orElseThrow();
    }
}
