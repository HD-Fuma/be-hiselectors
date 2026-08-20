package com.fuma.hiselectors.selectors.model;

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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "selectors_sns_account", uniqueConstraints = @UniqueConstraint(
        name = "uq_selectors_sns_account_selectors_id", columnNames = "selectors_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SelectorsSnsAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "selectors_sns_account_id")
    private Long id;

    @Column(name = "selectors_id", nullable = false)
    private Long selectorsId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sns_code", length = 20)
    private SnsPlatform snsCode;

    @Column(name = "account_id", length = 100)
    private String accountId;

    @Column(name = "follower_count")
    private Long followerCount;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "last_collected_at")
    private LocalDateTime lastCollectedAt;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Builder
    private SelectorsSnsAccount(Long selectorsId, SnsPlatform snsCode, String accountId,
                                Long followerCount, boolean deleted,
                                LocalDateTime lastCollectedAt, String profileImageUrl) {
        this.selectorsId = selectorsId;
        this.snsCode = snsCode;
        this.accountId = accountId;
        this.followerCount = followerCount;
        this.deleted = deleted;
        this.lastCollectedAt = lastCollectedAt;
        this.profileImageUrl = profileImageUrl;
    }

    public void completeCollection(LocalDateTime collectedAt) {
        this.lastCollectedAt = collectedAt;
    }
}
