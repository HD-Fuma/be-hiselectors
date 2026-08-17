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

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "kakao_sender_connection",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_kakao_sender_connection_kakao_user",
                columnNames = "kakao_user_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KakaoSenderConnection extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "kakao_sender_connection_id")
    private Long id;

    @Column(name = "kakao_user_id", nullable = false)
    private Long kakaoUserId;

    @Column(name = "sender_name", nullable = false, length = 50)
    private String senderName;

    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEncrypted;

    @Column(name = "access_token_expires_at", nullable = false)
    private LocalDateTime accessTokenExpiresAt;

    @Column(name = "refresh_token_expires_at", nullable = false)
    private LocalDateTime refreshTokenExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KakaoSenderConnectionStatus status;

    @Builder
    private KakaoSenderConnection(Long kakaoUserId,
                                  String senderName,
                                  String accessTokenEncrypted,
                                  String refreshTokenEncrypted,
                                  LocalDateTime accessTokenExpiresAt,
                                  LocalDateTime refreshTokenExpiresAt) {
        updateOAuthConnection(
                kakaoUserId,
                senderName,
                accessTokenEncrypted,
                refreshTokenEncrypted,
                accessTokenExpiresAt,
                refreshTokenExpiresAt
        );
    }

    public void updateOAuthConnection(Long kakaoUserId,
                                      String senderName,
                                      String accessTokenEncrypted,
                                      String refreshTokenEncrypted,
                                      LocalDateTime accessTokenExpiresAt,
                                      LocalDateTime refreshTokenExpiresAt) {
        this.kakaoUserId = Objects.requireNonNull(kakaoUserId);
        this.senderName = Objects.requireNonNull(senderName);
        updateTokens(
                accessTokenEncrypted,
                refreshTokenEncrypted,
                accessTokenExpiresAt,
                refreshTokenExpiresAt
        );
    }

    public void updateAccessToken(String accessTokenEncrypted,
                                  LocalDateTime accessTokenExpiresAt) {
        this.accessTokenEncrypted = Objects.requireNonNull(accessTokenEncrypted);
        this.accessTokenExpiresAt = Objects.requireNonNull(accessTokenExpiresAt);
        this.status = KakaoSenderConnectionStatus.CONNECTED;
    }

    public void updateTokens(String accessTokenEncrypted,
                             String refreshTokenEncrypted,
                             LocalDateTime accessTokenExpiresAt,
                             LocalDateTime refreshTokenExpiresAt) {
        this.accessTokenEncrypted = Objects.requireNonNull(accessTokenEncrypted);
        this.refreshTokenEncrypted = Objects.requireNonNull(refreshTokenEncrypted);
        this.accessTokenExpiresAt = Objects.requireNonNull(accessTokenExpiresAt);
        this.refreshTokenExpiresAt = Objects.requireNonNull(refreshTokenExpiresAt);
        this.status = KakaoSenderConnectionStatus.CONNECTED;
    }

    public void markReauthRequired() {
        this.status = KakaoSenderConnectionStatus.REAUTH_REQUIRED;
    }
}
