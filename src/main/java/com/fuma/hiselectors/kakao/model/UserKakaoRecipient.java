package com.fuma.hiselectors.kakao.model;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(
        name = "user_kakao_recipient",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_kakao_recipient_user", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_user_kakao_recipient_kakao_user", columnNames = "kakao_user_id"),
                @UniqueConstraint(name = "uk_user_kakao_recipient_uuid", columnNames = "kakao_message_uuid")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserKakaoRecipient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_kakao_recipient_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "kakao_user_id", nullable = false)
    private Long kakaoUserId;

    @Column(name = "kakao_message_uuid", nullable = false, length = 255)
    private String kakaoMessageUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_status", nullable = false, length = 20)
    private KakaoRecipientStatus recipientStatus;

    @Builder
    private UserKakaoRecipient(Long userId, Long kakaoUserId, String kakaoMessageUuid) {
        this.userId = Objects.requireNonNull(userId);
        updateConnection(kakaoUserId, kakaoMessageUuid);
    }

    public void updateConnection(Long kakaoUserId, String kakaoMessageUuid) {
        this.kakaoUserId = Objects.requireNonNull(kakaoUserId);
        this.kakaoMessageUuid = Objects.requireNonNull(kakaoMessageUuid);
        this.recipientStatus = KakaoRecipientStatus.READY;
    }

    public void markReauthRequired() {
        this.recipientStatus = KakaoRecipientStatus.REAUTH_REQUIRED;
    }

    public void deactivate() {
        this.recipientStatus = KakaoRecipientStatus.INACTIVE;
    }
}
