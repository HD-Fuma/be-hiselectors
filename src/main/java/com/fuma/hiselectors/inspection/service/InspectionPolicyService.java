package com.fuma.hiselectors.inspection.service;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.exception.BusinessException;
import com.fuma.hiselectors.exception.ErrorCode;
import com.fuma.hiselectors.inspection.config.ContentInspectionProperties;
import com.fuma.hiselectors.inspection.config.InspectionExtractionProperties;
import com.fuma.hiselectors.inspection.model.InspectionPolicy;
import com.fuma.hiselectors.inspection.model.InspectionRuleConfig;
import com.fuma.hiselectors.inspection.repository.InspectionPolicyRepository;
import com.fuma.hiselectors.stt.GeminiProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class InspectionPolicyService {

    @Value("${inspection.policy-sync.enabled:true}")
    private boolean policySyncEnabled = true;

    private final InspectionPolicyRepository policyRepository;
    private final ContentInspectionProperties inspectionProperties;
    private final InspectionExtractionProperties extractionProperties;
    private final GeminiProperties geminiProperties;
    private final InspectionPromptProvider promptProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncActivePolicies() {
        if (!policySyncEnabled) {
            return;
        }
        sync(SnsPlatform.YOUTUBE);
        sync(SnsPlatform.INSTAGRAM);
    }

    @Transactional(readOnly = true)
    public InspectionPolicy requireActive(SnsPlatform platform) {
        return policyRepository.findByPlatformAndActiveTrue(platform)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSPECTION_ENGINE_NOT_READY));
    }

    @Transactional(readOnly = true)
    public List<InspectionPolicy> requireAllActive() {
        List<InspectionPolicy> policies = policyRepository.findAllByActiveTrue();
        if (policies.size() != SnsPlatform.values().length) {
            throw new BusinessException(ErrorCode.INSPECTION_ENGINE_NOT_READY);
        }
        return policies;
    }

    private void sync(SnsPlatform platform) {
        PolicyDefinition definition = definition(platform);
        InspectionPolicy matching = policyRepository
                .findByConfigHash(definition.configHash()).orElse(null);
        if (matching != null && matching.isActive()) {
            return;
        }

        policyRepository.findAllByPlatformAndActiveTrue(platform)
                .forEach(InspectionPolicy::deactivate);
        policyRepository.flush();

        if (matching == null) {
            matching = policyRepository.save(definition.toEntity());
        }
        matching.activate(LocalDateTime.now(clock));
    }

    private PolicyDefinition definition(SnsPlatform platform) {
        InspectionRuleConfig ruleConfig = InspectionRuleConfig.from(inspectionProperties);
        String ruleHash = sha256(writeJson(ruleConfig));

        String aiPrompt = promptProvider.aiPrompt();
        String aiModel = geminiProperties.modelOrDefault();
        String aiConfigHash = sha256(String.join("\n",
                aiModel,
                InspectionPromptProvider.AI_PROMPT_VERSION,
                aiPrompt,
                String.valueOf(geminiProperties.maxOutputTokensOrDefault())));

        String sttModel;
        String ocrModel;
        String extractionPromptVersion;
        String extractionPrompt;
        if (platform == SnsPlatform.YOUTUBE) {
            sttModel = aiModel;
            ocrModel = aiModel;
            extractionPromptVersion =
                    InspectionPromptProvider.YOUTUBE_EXTRACTION_PROMPT_VERSION;
            extractionPrompt = promptProvider.youtubeExtractionPrompt();
        } else {
            sttModel = extractionProperties.instagramSttModelOrDefault();
            ocrModel = extractionProperties.instagramOcrModelOrDefault();
            extractionPromptVersion = null;
            extractionPrompt = null;
        }

        String extractionConfigHash = sha256(String.join("\n",
                platform.name(), sttModel, ocrModel,
                extractionPromptVersion == null ? "" : extractionPromptVersion,
                extractionPrompt == null ? "" : extractionPrompt,
                platform == SnsPlatform.YOUTUBE
                        ? geminiProperties.mediaResolutionApiValue() : "",
                platform == SnsPlatform.YOUTUBE
                        ? String.valueOf(geminiProperties.maxOutputTokensOrDefault()) : ""));
        String configHash = sha256(String.join("\n",
                platform.name(), ruleHash, aiConfigHash, extractionConfigHash));
        String version = platform.name().toLowerCase()
                + "-policy-" + configHash.substring(0, 12);
        return new PolicyDefinition(
                platform, version, ruleConfig, ruleHash,
                aiModel,
                InspectionPromptProvider.AI_PROMPT_VERSION, aiPrompt, aiConfigHash,
                sttModel, ocrModel, extractionPromptVersion, extractionPrompt,
                extractionConfigHash, configHash);
    }

    private String writeJson(InspectionRuleConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JacksonException e) {
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "검수 Rule 설정을 직렬화할 수 없습니다.");
        }
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

    private record PolicyDefinition(
            SnsPlatform platform,
            String version,
            InspectionRuleConfig ruleConfig,
            String ruleConfigHash,
            String aiModelName,
            String aiPromptVersion,
            String aiPrompt,
            String aiConfigHash,
            String sttModelName,
            String ocrModelName,
            String extractionPromptVersion,
            String extractionPrompt,
            String extractionConfigHash,
            String configHash) {

        private InspectionPolicy toEntity() {
            return InspectionPolicy.create(
                    platform, version, ruleConfig, ruleConfigHash,
                    aiModelName, aiPromptVersion, aiPrompt, aiConfigHash,
                    sttModelName, ocrModelName, extractionPromptVersion,
                    extractionPrompt, extractionConfigHash, configHash);
        }
    }
}
