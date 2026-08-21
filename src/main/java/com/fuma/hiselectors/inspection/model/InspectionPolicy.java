package com.fuma.hiselectors.inspection.model;

import com.fuma.hiselectors.application.model.SnsPlatform;
import com.fuma.hiselectors.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 플랫폼별 콘텐츠 검수 정책 스냅샷. 생성된 정책 행은 수정하지 않고 활성 여부만 변경한다. */
@Entity
@Table(name = "inspection_policy", uniqueConstraints = {
        @UniqueConstraint(
                name = "uq_inspection_policy_platform_version",
                columnNames = {"platform", "version"}),
        @UniqueConstraint(
                name = "uq_inspection_policy_config_hash",
                columnNames = {"config_hash"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionPolicy extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_policy_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private SnsPlatform platform;

    @Column(name = "version", nullable = false, length = 40)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_config", nullable = false, columnDefinition = "json")
    private InspectionRuleConfig ruleConfig;

    @Column(name = "rule_config_hash", nullable = false, length = 64)
    private String ruleConfigHash;

    @Column(name = "ai_model_name", nullable = false, length = 100)
    private String aiModelName;

    @Column(name = "ai_prompt_version", nullable = false, length = 40)
    private String aiPromptVersion;

    @Column(name = "ai_prompt", nullable = false, columnDefinition = "text")
    private String aiPrompt;

    @Column(name = "ai_config_hash", nullable = false, length = 64)
    private String aiConfigHash;

    @Column(name = "stt_model_name", length = 100)
    private String sttModelName;

    @Column(name = "ocr_model_name", length = 100)
    private String ocrModelName;

    @Column(name = "extraction_prompt_version", length = 40)
    private String extractionPromptVersion;

    @Column(name = "extraction_prompt", columnDefinition = "text")
    private String extractionPrompt;

    @Column(name = "extraction_config_hash", nullable = false, length = 64)
    private String extractionConfigHash;

    @Column(name = "config_hash", nullable = false, length = 64)
    private String configHash;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    public static InspectionPolicy create(
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
        InspectionPolicy policy = new InspectionPolicy();
        policy.platform = platform;
        policy.version = version;
        policy.ruleConfig = ruleConfig;
        policy.ruleConfigHash = ruleConfigHash;
        policy.aiModelName = aiModelName;
        policy.aiPromptVersion = aiPromptVersion;
        policy.aiPrompt = aiPrompt;
        policy.aiConfigHash = aiConfigHash;
        policy.sttModelName = sttModelName;
        policy.ocrModelName = ocrModelName;
        policy.extractionPromptVersion = extractionPromptVersion;
        policy.extractionPrompt = extractionPrompt;
        policy.extractionConfigHash = extractionConfigHash;
        policy.configHash = configHash;
        return policy;
    }

    public void activate(LocalDateTime activatedAt) {
        this.active = true;
        this.activatedAt = activatedAt;
    }

    public void deactivate() {
        this.active = false;
    }
}
