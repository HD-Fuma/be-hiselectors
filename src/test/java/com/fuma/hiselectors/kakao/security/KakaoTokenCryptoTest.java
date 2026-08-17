package com.fuma.hiselectors.kakao.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fuma.hiselectors.exception.BusinessException;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoTokenCryptoTest {

    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("AES-GCM으로 암호화한 토큰을 복호화한다")
    void encryptAndDecrypt() {
        KakaoTokenCrypto crypto = new KakaoTokenCrypto(key);

        String encrypted = crypto.encrypt("secret-token");

        assertThat(encrypted).doesNotContain("secret-token");
        assertThat(crypto.decrypt(encrypted)).isEqualTo("secret-token");
    }

    @Test
    @DisplayName("같은 토큰도 임의 IV로 서로 다른 암호문을 만든다")
    void randomIv() {
        KakaoTokenCrypto crypto = new KakaoTokenCrypto(key);
        assertThat(crypto.encrypt("same-token")).isNotEqualTo(crypto.encrypt("same-token"));
    }

    @Test
    @DisplayName("32바이트가 아닌 암호화 키는 거부한다")
    void invalidKey() {
        KakaoTokenCrypto crypto = new KakaoTokenCrypto(
                Base64.getEncoder().encodeToString(new byte[16]));
        assertThatThrownBy(() -> crypto.encrypt("token"))
                .isInstanceOf(BusinessException.class);
    }
}
