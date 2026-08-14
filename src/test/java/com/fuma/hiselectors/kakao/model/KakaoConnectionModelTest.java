package com.fuma.hiselectors.kakao.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoConnectionModelTest {

    @Test
    @DisplayName("발신 계정의 토큰을 갱신하면 연결 상태로 전환한다")
    void updateSenderTokens() {
        LocalDateTime now = LocalDateTime.now();
        KakaoSenderConnection connection = senderConnection(now);
        connection.markReauthRequired();

        connection.updateTokens(
                "new-access-token",
                "new-refresh-token",
                now.plusHours(6),
                now.plusDays(30)
        );

        assertThat(connection.getAccessTokenEncrypted()).isEqualTo("new-access-token");
        assertThat(connection.getRefreshTokenEncrypted()).isEqualTo("new-refresh-token");
        assertThat(connection.getStatus()).isEqualTo(KakaoSenderConnectionStatus.CONNECTED);
        assertThat(connection.toString()).doesNotContain("new-access-token", "new-refresh-token");
    }

    @Test
    @DisplayName("수신자 연결 상태를 갱신하고 비활성화할 수 있다")
    void updateRecipientStatus() {
        UserKakaoRecipient recipient = UserKakaoRecipient.builder()
                .userId(1L)
                .kakaoUserId(10L)
                .kakaoMessageUuid("uuid-before")
                .build();

        recipient.markReauthRequired();
        assertThat(recipient.getStatus()).isEqualTo(KakaoRecipientStatus.REAUTH_REQUIRED);

        recipient.updateConnection(20L, "uuid-after");
        assertThat(recipient.getKakaoUserId()).isEqualTo(20L);
        assertThat(recipient.getKakaoMessageUuid()).isEqualTo("uuid-after");
        assertThat(recipient.getStatus()).isEqualTo(KakaoRecipientStatus.READY);

        recipient.deactivate();
        assertThat(recipient.getStatus()).isEqualTo(KakaoRecipientStatus.INACTIVE);
    }

    private KakaoSenderConnection senderConnection(LocalDateTime now) {
        return KakaoSenderConnection.builder()
                .kakaoUserId(100L)
                .senderName("공용 발신 계정")
                .accessTokenEncrypted("access-token")
                .refreshTokenEncrypted("refresh-token")
                .accessTokenExpiresAt(now.plusHours(6))
                .refreshTokenExpiresAt(now.plusDays(30))
                .build();
    }
}
