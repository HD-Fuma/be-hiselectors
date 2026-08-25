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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "selectors_sns_account", uniqueConstraints = @UniqueConstraint(
        name = "uq_selectors_sns_account_selectors_id", columnNames = "selectors_id"))
@Getter
@DynamicUpdate
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

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

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
                                String profileUrl, Long followerCount, boolean deleted,
                                LocalDateTime lastCollectedAt, String profileImageUrl) {
        this.selectorsId = selectorsId;
        this.snsCode = snsCode;
        this.accountId = accountId;
        this.profileUrl = profileUrl;
        this.followerCount = followerCount;
        this.deleted = deleted;
        this.lastCollectedAt = lastCollectedAt;
        this.profileImageUrl = profileImageUrl;
    }

    public void completeCollection(LocalDateTime collectedAt) {
        this.lastCollectedAt = collectedAt;
    }

    public void synchronize(SnsPlatform snsCode, String accountId, Long followerCount,
                            String profileUrl, String profileImageUrl) {
        boolean accountChanged = this.snsCode != snsCode
                || !Objects.equals(this.accountId, accountId);
        if (accountChanged) {
            this.lastCollectedAt = null;
            this.profileUrl = null;
            this.profileImageUrl = null;
        }
        this.snsCode = snsCode;
        this.accountId = accountId;
        this.followerCount = followerCount;
        if (profileUrl != null && !profileUrl.isBlank()) {
            this.profileUrl = profileUrl;
        }
        if (!hasText(this.profileImageUrl) && validProfileImageUrl(profileImageUrl)) {
            this.profileImageUrl = profileImageUrl;
        }
        this.deleted = false;
    }

    public void synchronizeProfileImageUrl(
            SnsPlatform snsCode, String accountId, String profileImageUrl) {
        if (this.snsCode == snsCode
                && Objects.equals(this.accountId, accountId)
                && !hasText(this.profileImageUrl)
                && validProfileImageUrl(profileImageUrl)) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    private boolean validProfileImageUrl(String value) {
        return hasText(value) && value.length() <= 500;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
