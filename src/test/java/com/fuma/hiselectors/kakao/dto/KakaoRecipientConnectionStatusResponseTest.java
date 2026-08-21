package com.fuma.hiselectors.kakao.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fuma.hiselectors.kakao.model.KakaoRecipientStatus;
import com.fuma.hiselectors.kakao.model.UserKakaoRecipient;
import org.junit.jupiter.api.Test;

class KakaoRecipientConnectionStatusResponseTest {

    @Test
    void exposesRecipientEnumWithoutIdentifiers() {
        UserKakaoRecipient ready = UserKakaoRecipient.builder()
                .userId(1L)
                .kakaoUserId(99L)
                .kakaoMessageUuid("secret-uuid")
                .build();

        KakaoRecipientConnectionStatusResponse readyStatus =
                KakaoRecipientConnectionStatusResponse.from(ready);
        assertThat(readyStatus.status()).isEqualTo(KakaoRecipientStatus.READY);
        assertThat(readyStatus.toString()).doesNotContain("99", "secret-uuid");

        ready.markReauthRequired();
        assertThat(KakaoRecipientConnectionStatusResponse.from(ready).status())
                .isEqualTo(KakaoRecipientStatus.REAUTH_REQUIRED);

        ready.deactivate();
        assertThat(KakaoRecipientConnectionStatusResponse.from(ready).status())
                .isEqualTo(KakaoRecipientStatus.INACTIVE);
    }
}
