package com.fuma.hiselectors.application.model;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "application", uniqueConstraints = @UniqueConstraint(
        name = "uq_application_user_generation", columnNames = {"user_id", "generation_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "generation_id", nullable = false)
    private Long generationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sns_code", nullable = false, length = 20)
    private SnsPlatform snsCode;

    @Column(name = "sns_account_id", nullable = false, length = 200)
    private String snsAccountId;

    @Column(name = "follower_count")
    private Long followerCount;

    /** 최근 활동일(프론트 전달). */
    @Column(name = "last_content_at")
    private LocalDateTime lastContentAt;

    @Column(name = "engagement_rate", precision = 5, scale = 2)
    private BigDecimal engagementRate;

    @Column(name = "alarm_yn", nullable = false)
    private boolean alarmYn;

    @Column(name = "policy_agreed_at", nullable = false)
    private LocalDateTime policyAgreedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status;

    @Builder
    private Application(Long userId, Long generationId, SnsPlatform snsCode, String snsAccountId,
                        Long followerCount, LocalDateTime lastContentAt, BigDecimal engagementRate,
                        boolean alarmYn, LocalDateTime policyAgreedAt, ApplicationStatus status) {
        this.userId = userId;
        this.generationId = generationId;
        this.snsCode = snsCode;
        this.snsAccountId = snsAccountId;
        this.followerCount = followerCount;
        this.lastContentAt = lastContentAt;
        this.engagementRate = engagementRate;
        this.alarmYn = alarmYn;
        this.policyAgreedAt = policyAgreedAt;
        this.status = status;
    }
}

