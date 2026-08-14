package com.fuma.hiselectors.kakao.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KakaoOAuthStateProviderTest {

    private final KakaoOAuthStateProvider provider = new KakaoOAuthStateProvider(
            "12345678901234567890123456789012", 300);

    @Test
    @DisplayName("state에 로그인 ID와 연결 유형을 서명한다")
    void createAndResolve() {
        KakaoOAuthState state = provider.resolve(
                provider.create("admin", KakaoConnectionType.SENDER));
        assertThat(state.loginId()).isEqualTo("admin");
        assertThat(state.connectionType()).isEqualTo(KakaoConnectionType.SENDER);
    }

    @Test
    @DisplayName("변조된 state는 거부한다")
    void rejectTamperedState() {
        String state = provider.create("admin", KakaoConnectionType.SENDER);
        assertThatThrownBy(() -> provider.resolve(state + "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
